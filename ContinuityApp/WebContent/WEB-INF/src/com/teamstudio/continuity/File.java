package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.View;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.FileRowData;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class File implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public static final String TYPE_DOC = "document";
	public static final String TYPE_PDF = "pdf";
	public static final String TYPE_PPT = "presentation";
	public static final String TYPE_SPREADSHEET = "spreadsheet";
	public static final String TYPE_IMAGE = "image";
	public static final String TYPE_VIDEO = "video";
	public static final String TYPE_UNKNOWN = "unknown";

	public File() {
		
	}
	
	public boolean save( DominoDocument xspDocFile ) {
		
		try {
			boolean blnIsNew = xspDocFile.isNewNote();
			
			if ( blnIsNew ) {
									
				//set the owners: [bcEditor] and owner
				xspDocFile.replaceItemValue("docAuthors", Authorizations.ROLE_EDITOR );
			
				Document docFile = xspDocFile.getDocument(true);
				docFile.getFirstItem("docAuthors").setAuthors(true);
				docFile.getFirstItem("owner").setAuthors(true);

				xspDocFile.replaceItemValue("id", "q" + xspDocFile.getDocument().getUniversalID().toLowerCase() );
				
			}
			
			//get file type/ name/ size
			List<FileRowData> files = xspDocFile.getAttachmentList("files");
			
			//Logger.debug("found" + files.size() + "files");
			
			if (files.size()==0) {		//file is required
				Utils.addInfoMessage("Attachment is required");
				return false;
			}
			
			String type = "";
			long size = 0;
			String name = "";
			String ext ="";
			
			if (files.size()>0) {
				
				FileRowData file = files.get(0);

				name = file.getName();
				
				ext = name.substring( name.lastIndexOf(".") + 1).toLowerCase();
				
				ArrayList<String> doc = new ArrayList<String>(Arrays.asList( "doc,docx,odt,pages".split(",") ) );
				ArrayList<String> spreadsheet = new ArrayList<String>(Arrays.asList( "xls,xlsx,odf,numbers".split(",") ) );
				ArrayList<String> pdf = new ArrayList<String>(Arrays.asList( "pdf".split(",") ) );
				ArrayList<String> presentation = new ArrayList<String>(Arrays.asList( "ppt,odp,otp,sdd,sxi,pot,potx,pps,ppsx,pptx,key,keynote".split(",") ) );
				ArrayList<String> image = new ArrayList<String>(Arrays.asList( "jpg,jpeg,png,bmp,gif".split(",") ) );
				ArrayList<String> video = new ArrayList<String>(Arrays.asList( "mov,avi,mpeg,mp2,mp4,mkv".split(",") ) );
				
				if (doc.contains(ext) ) {
					type = TYPE_DOC;
				} else if (spreadsheet.contains(ext) ) {
					type = TYPE_SPREADSHEET;
				} else if (pdf.contains(ext) ) {
					type = TYPE_PDF;
				} else if (presentation.contains(ext) ) {
					type = TYPE_PPT;
				} else if (image.contains(ext) ) {
					type = TYPE_IMAGE;
				} else if (video.contains(ext) ) {
					type = TYPE_VIDEO;
				} else {
					type = TYPE_UNKNOWN;
				}
				
				size = file.getLength();
				
			}
			
			xspDocFile.replaceItemValue("fileName", name);
			xspDocFile.replaceItemValue("fileNameEncoded", java.net.URLEncoder.encode(name, "UTF-8") );
			xspDocFile.replaceItemValue("fileExt", ext);
			xspDocFile.replaceItemValue("fileType", type);
			
			String readableSize = "";
			
			if (size < 1024) {
				readableSize = size + " bytes";
			} else if (size < (1024*1024) ) {
				//smaller than 1 MB
				readableSize = Math.round(size/1024) + " KB";
			} else {
				readableSize = Math.round(size/1024/1024) + " MB";
			}
			
			xspDocFile.replaceItemValue("fileSizeBytes", size);
			xspDocFile.replaceItemValue("fileSize", readableSize);
			
			xspDocFile.replaceItemValue("createdDate", xspDocFile.getDocument().getCreated() );
			
			//get displayName of owner
			String owner = xspDocFile.getItemValueString("owner");
			String ownerName = xspDocFile.getItemValueString("ownerName");
			
			Map<String,Object> viewScope = ExtLibUtil.getViewScope();
			
			if ( ownerName.length()==0 || !viewScope.get("currentOwner").equals( owner) ) {
				
				Database dbCurrent = ExtLibUtil.getCurrentDatabase();
				View vwContacts = dbCurrent.getView("vwContactsByUsername");
				Document docContact = vwContacts.getDocumentByKey(owner, true);
				
				if (null != docContact) {
					ownerName = docContact.getItemValueString("name");

				}
				Utils.recycle(docContact, vwContacts);
				
				xspDocFile.replaceItemValue("ownerName", ownerName);
			}
			
			xspDocFile.save();

			if (blnIsNew) {	
				//update menu items count
				User.get().updateMenuOptionCounts();
			}
			
			Logger.debug("save");
			
			boolean result = xspDocFile.save();
			Logger.debug( result + "");
			
			return result;
			
		} catch (Exception e) {
			
			Logger.debug("error");
			Logger.error(e);
			
			return false;
		}
		
		
		
	}
	

}
