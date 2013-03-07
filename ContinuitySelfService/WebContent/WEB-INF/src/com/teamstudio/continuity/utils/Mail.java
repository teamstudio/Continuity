package com.teamstudio.continuity.utils;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Vector;
import javax.faces.context.FacesContext;

import com.teamstudio.continuity.utils.Logger;

import lotus.domino.*;

/*
 * Class to generate HTML e-mail messages
 * 
 * Author: Mark Leusink (m.leusink@gmail.com)
 * 
 * Version: 2012-03-06
 * 
 * Usage example (simple):
 * 
 * var mail = new Mail();
 * mail.setTo( "m.leusink@gmail.com")
 * mail.setSubject("Your notification");
 * mail.addHTML("<b>Hello world</b>");
 * mail.send();
 *  
 * Usage example (extended):
 * 
 * var mail = new Mail();
 * mail.setTo( "m.leusink@gmail.com")
 * Vector<String> cc = new Vector<String>();
 * cc.add("user@domain.com");
 * cc.add("anotheruser@domain.com");
 * mail.setCC( cc );
 * mail.setBB( "user3@domaino.com");
 * mail.setSubject("A HTML notification from Domino");
 * mail.addHTML("<h1>Hi!</h1>");
 * mail.addHTML("<table><tbody><tr><td>contents in a table here</td></tr></tbody></table>");
 * mail.addDocAttachment( "DC9126E84C59093FC1257953003C13E6", "jellyfish.jpg", true);
 * mail.addFileAttachment( "c:/temp/", "report.pdf", true);
 * mail.setSender("m.leusink@gmail.com", "Mark Leusink");
 * mail.send();
 */

public class Mail {
	
	private static String subjectPrefix = "[continuity] ";
	
	private Vector<String> to;
	private Vector<String> cc;
	private Vector<String> bcc;
	
	private boolean asSigner = false;
	
	public void setAsSigner(boolean signer) {
		asSigner = signer;
	}
	
	private StringBuffer contentsHTML;
	private StringBuffer contentsPlain;
	
	private String subject = "";
	
	private String fromName;
	private String fromEmail;
	
	private Session session;
	private Database dbCurrent;
	
	private Vector<Attachment> attachments;

	public Mail() {
		
		to = new Vector<String>();
		cc = new Vector<String>();
		bcc = new Vector<String>();
		
		contentsHTML = new StringBuffer();
		contentsPlain = new StringBuffer();
		
		attachments = new Vector<Attachment>();
		
		this.setSender("no-reply@continuity.com", "Continuity");
	}

	//send the mail
	public void send() {
		
		try {
			
			if (this.to.size()==0 && this.cc.size()==0 && this.bcc.size()==0) {
				Logger.debug("cannot send e-mail: no recipients set");
				return;
			}
			
			//log.debug("sending e-mail");
			
			//get session
			FacesContext facesContext = FacesContext.getCurrentInstance();
			if (asSigner) {
				session = (Session) facesContext.getApplication().getVariableResolver().resolveVariable(facesContext, "sessionAsSigner");
			} else {
				
				session = (Session) facesContext.getApplication().getVariableResolver().resolveVariable(facesContext, "session");
			}
			dbCurrent = session.getCurrentDatabase();

			session.setConvertMime(false);
			
			Document doc = dbCurrent.createDocument();
			
			doc.replaceItemValue("RecNoOutOfOffice", "1");	//no replies from out of office agents
			
			MIMEEntity mimeRoot = doc.createMIMEEntity();
			
			//set to
			if (this.to.size() > 0) {
				
				//log.debug("Mail to: " + vectorJoin(to));
				
				MIMEHeader mimeHeadTo = mimeRoot.createHeader("To");
				mimeHeadTo.setHeaderVal( vectorJoin(to) );
			}
			
			//set to
			if (this.cc.size() > 0) {
				
				//log.debug("Mail cc: " + vectorJoin(cc));
				
				MIMEHeader mimeHeadCC = mimeRoot.createHeader("CC");
				mimeHeadCC.setHeaderVal( vectorJoin(to) );
			}
			//set to
			if (this.bcc.size() > 0) {
				
				//log.debug("Mail bcc: " + vectorJoin(bcc));
				
				MIMEHeader mimeHeadBCC = mimeRoot.createHeader("BCC");
				mimeHeadBCC.setHeaderVal( vectorJoin(to) );
			}
			
			//set subject
			MIMEHeader headSubject = mimeRoot.createHeader("Subject");
			headSubject.setHeaderVal(subjectPrefix + this.subject);

			String boundary = doc.getUniversalID().toLowerCase();
			
			//create text/alternative directive: text/plain and text/html part will be childs of this entity
			MIMEEntity mimeRootChild = mimeRoot.createChildEntity();
			
			MIMEHeader headContentType = mimeRootChild.createHeader("Content-Type");
			headContentType.setHeaderVal( "multipart/alternative; boundary=\"" + boundary + "\"" );
			
			//create plain text part
			MIMEEntity mimePlainText = mimeRootChild.createChildEntity();
			Stream streamPlain = session.createStream();
			streamPlain.writeText( contentsPlain.toString() );
			mimePlainText.setContentFromText(streamPlain, "text/plain; charset=\"UTF-8\"", MIMEEntity.ENC_NONE);
			streamPlain.close();
			
			//create HTML part
			MIMEEntity mimeHTML = mimeRootChild.createChildEntity();
			Stream streamHTML = session.createStream();
			
			//add default header/footer
			com.teamstudio.continuity.selfservice.Configuration config = com.teamstudio.continuity.selfservice.Configuration.get();
			
			streamHTML.writeText( config.getMailHeader() );
			streamHTML.writeText( contentsHTML.toString());
			streamHTML.writeText( config.getMailFooter()  );
			
			mimeHTML.setContentFromText(streamHTML, "text/html; charset=\"UTF-8\"", MIMEEntity.ENC_NONE);
			streamHTML.close();
			
			//set the sender
			setMailSender(mimeRoot);
			
			//add attachments
			addAttachments(mimeRoot);
			
			//send the e-mail
			doc.send();
			
			//log.debug("mail sent");
			
			session.setConvertMime(true);
			
			doc.recycle();
			
		} catch (NotesException e) {
			
			e.printStackTrace();
		} finally {
			
			try {
				dbCurrent.recycle();
			} catch (Exception e) { }
			
		}

	}
	
	public void setTo(Vector<String> sendTo) {
		this.to = sendTo;
	}
	public void setTo(String sendTo) {
		this.to.add(sendTo);
	}

	public void setCC(Vector<String> sendCC) {
		this.cc = sendCC;
	}
	public void setCC(String sendTo) {
		this.cc.add(sendTo);
	}

	public void setBCC(Vector<String> sendBCC) {
		this.bcc = sendBCC;
	}
	public void setBCC(String sendTo) {
		this.bcc.add(sendTo);
	}
	
	public void setSubject( String subject ) {
		this.subject = subject;
	}
	
	//add html to the mail
	public void addHTML( String content ) {
		
		this.contentsHTML.append(content);

		//create a plain text representation of the HTML contents:
		//remove all HTML tags and add a line break
		String plainText = content.replaceAll("\\<.*?>","");
		this.contentsPlain.append(plainText);	
	}
	
	//add text to the mail
	public void addText( String content ) {
		
		contentsPlain.append( content);
		
		contentsHTML.append( content.replace( "\n", "<br />") );
	}
	
	//set the sender of the mail
	//fromEmail is required, fromName is optional: might be null
	public void setSender( String email, String name) {
		this.fromEmail = email;
		this.fromName = name;
	}
	
	//joins the contents of a vector (of string) and returns a comma separated string 
	private String vectorJoin( Vector<String> v) {
		
		String delimiter = ",";
		
		StringBuilder builder = new StringBuilder();
	    Iterator<String> iter = v.iterator();
	    while (iter.hasNext()) {
	        builder.append(iter.next());
	        if (!iter.hasNext()) {
	          break;                  
	        }
	        builder.append(delimiter);
	    }
	    return builder.toString();
	}
	
	//change the sender of an e-mail
	private void setMailSender( MIMEEntity mimeRoot ) {
		
		try {
			if (fromEmail == null) {
				return;
			}
			
			MIMEHeader replyTo = mimeRoot.createHeader("Reply-To");
			replyTo.setHeaderVal(fromEmail);
			
			MIMEHeader returnPath = mimeRoot.createHeader("Return-Path");
			returnPath.setHeaderVal(fromEmail);
			
			String name = fromEmail;
			if (fromName != null) {
				name = "\"" + fromName + "\" <" + fromEmail + ">";
			}
			
			MIMEHeader from = mimeRoot.createHeader("From");
			from.setHeaderVal(name);
			MIMEHeader sender = mimeRoot.createHeader("Sender");
			sender.setHeaderVal(name);
		} catch (NotesException e) {

			e.printStackTrace();
		}
		
	};
	 
	/*
	 * add an attachment on a document to the mail message
	 * 
	 * unid (string) = unid of the document in the current database that contains the file to be send
	 * fileName (string) = well... guess...
	 * inlineImage (boolean, defaults to false): if set to true, this image is marked as "inline" (used in the content)
	 */
	  
	public String addDocAttachment( String unid, String fileName, boolean inlineImage ) {
		
		String contentId = Long.toHexString(Double.doubleToLongBits(Math.random()));
		
		Attachment att = new Attachment( Attachment.TYPE_DOCUMENT, fileName, contentId, inlineImage);
		att.setUnid(unid);
		
		attachments.add( att );
		return "cid:" + contentId;
	}
	
	/* add an OS file to the mail message
	 * 
	 * path (string): path of the file on the server
	 * fileName (string): fileName of the file
	 * inlineImage (boolean, defaults to false): if set to true, this image is marked as "inline" (used in the content)
	 */
	  
	public String addFileAttachment( String path, String fileName, boolean inlineImage ) {
		String contentId = Long.toHexString(Double.doubleToLongBits(Math.random()));
		
		Attachment att = new Attachment( Attachment.TYPE_FILE, fileName, contentId, inlineImage);
		att.setPath(path);
		
		attachments.add( att );
		return "cid:" + contentId;
	}
	
	//add the attachments to the MIME mail
	private void addAttachments( MIMEEntity mimeRoot ) {
		
		Stream streamFile = null;
		
		//process attachments
		Iterator<Attachment> it = attachments.iterator();
		
		while(it.hasNext() ) {
			Attachment att = it.next();
			
			//get content type for file
			String contentType = att.getContentType() + "; name=\"" + att.getFileName() + "\"";
			
			EmbeddedObject eo = null;
			InputStream is = null;
			Document docFile = null;
						
			try {
			
				if ( att.isDocumentType() ) {
				
					//retrieve the document containing the attachment to send from the current database
					docFile = dbCurrent.getDocumentByUNID( att.getUnid() );
					if (null != docFile) {

						eo = docFile.getAttachment(att.fileName);
						is = eo.getInputStream();
					} 
				
				} else {
					
					is = new java.io.FileInputStream( att.getPath() + att.getFileName() );
					
				}
				
				if (is != null) {
					
					MIMEEntity mimeChild = mimeRoot.createChildEntity();
					MIMEHeader mimeHeader = mimeChild.createHeader("Content-Disposition");
					
					if ( att.isInline() ) {
						mimeHeader.setHeaderVal("inline; filename=\"" + att.fileName + "\"");
					} else {
						mimeHeader.setHeaderVal("attachment; filename=\"" + att.fileName + "\"");
					}
					
					mimeHeader = mimeChild.createHeader("Content-ID");
					mimeHeader.setHeaderVal( "<" + att.contentId + ">" );

					streamFile = session.createStream();
					streamFile.setContents(is);
					mimeChild.setContentFromBytes(streamFile, contentType, MIMEEntity.ENC_IDENTITY_BINARY);
				
				}
				
			
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					if (is != null) { is.close(); }
					if (eo != null) { eo.recycle(); }
					if (docFile != null) { docFile.recycle(); }
				} catch (Exception e) { }
			}

		}	
	
	}
	
	private class Attachment {
		
		public static final String TYPE_DOCUMENT = "document";
		public static final String TYPE_FILE = "file"; 
		
		private String type;
		private String unid;		//can be unid or OS path
		private String fileName;
		private String contentId;
		private boolean asInline;
		private String path;
		
		public Attachment( String type, String fileName, String contentId, boolean asInline) {
			this.type = type;
			
			this.fileName = fileName;
			this.contentId = contentId;
			this.asInline = asInline;
		}
		
		public void setUnid(String unid) {
			this.unid = unid;
		}
		public void setPath( String path ) {
			this.path = path;
			
		}
		public String getFileName() {
			return fileName;
		}
		public String getPath() {
			return path;
		}
		
		public String getContentType() {
			String extension = fileName.substring( fileName.lastIndexOf(".")+1).toLowerCase();
			
			String contentType = "application/octet-stream";
			
			if (extension.equals("gif" )) {
				contentType = "image/gif";
			} else if (extension.equals("jpg") || extension.equals("jpeg") ) {
				contentType = "image/jpeg";
			} else if (extension.equals("png" )) {
				contentType = "image/png";
			}
			
			return contentType;
			
		}
		
		public boolean isDocumentType() {
			return type.equals( TYPE_DOCUMENT);
		}
		
		public String getUnid() {
			return unid;
		}
		
		public boolean isInline() {
			return asInline;
		}
		
	}
	
}

