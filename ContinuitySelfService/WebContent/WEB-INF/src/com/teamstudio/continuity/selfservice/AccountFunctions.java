package com.teamstudio.continuity.selfservice;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

import javax.faces.context.FacesContext;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.designer.context.XSPContext;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Mail;
import com.teamstudio.continuity.utils.Utils;

public class AccountFunctions implements Serializable {

	private static final long serialVersionUID = -5214138093643006877L;
	private String emailAddress;
	private String passCode;
	
	private String accountUnid;
	private boolean isAccountActive;
	
	private String userName;
	private String displayName;
	private String orgAlias;		//organisation alias from the user's username
	
	private boolean existingUser = false;
	private boolean isValidateKey = false;
	
	private String newPassword;
	private String newPasswordRepeat;
	
	private String redirectTo;
	private long passCodeRequestedAt;
	
	private String action;
	private static String TYPE_LOST_PASSWORD = "lost";
	private static String TYPE_ACTIVATE = "activate";
	
	private boolean isPWChanged;
	
	private String lostPasswordLink;
	
	private static final String txtLimitedUse = "<b>Note:</b> this link is only valid for a limited amount of time and can only be <b>used once</b> to change/ set a password. If you did not make this request yourself you can safely ignore this email: the link expires automatically.</p>";
	
	private com.teamstudio.continuity.selfservice.Configuration config;

	public AccountFunctions() {
		
		config = com.teamstudio.continuity.selfservice.Configuration.get();
		
		//if an e-mail address is in the URL, use this to send the password
		XSPContext context = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
		
		emailAddress = context.getUrlParameter("for");
		redirectTo = context.getUrlParameter("to");
		
		action = context.getUrlParameter("type");
		
		isPWChanged = false;
		
		lostPasswordLink = "account.xsp?type=" + TYPE_LOST_PASSWORD;
	}
	
	/*
	 * Called when the user hits 'send' on the activation/ lost password page.
	 * 
	 * Sends a link to the user that he can use to change his password or activate his account (based on the current action type)
	 */
	public void sendLink() {
		
		Session session = null;
		Database dbDirectory = null;
		DocumentCollection dc = null;
		Document docAccount = null;
		View vwUsers = null;
		
		try {
			
			if ( StringUtil.isEmpty(emailAddress) ) {
				return;
			}
			
    		//check for a valid e-mail adres
    		if ( emailAddress.indexOf("@")>-1 && !Utils.rfc2822.matcher(emailAddress).matches()) {
    			Utils.addWarningMessage("Enter a valid email address.");
    			return;
			}
    		
    		//check if the user exists in the directory
    		session = ExtLibUtil.getCurrentSessionAsSigner();
			dbDirectory = session.getDatabase( config.getServerName(), config.getDirectoryDbPath());
			vwUsers = dbDirectory.getView("($Users)");
			
			dc = vwUsers.getAllDocumentsByKey(emailAddress, true);
			
			if (dc.getCount()==1) {
			
				docAccount = dc.getFirstDocument();
				existingUser = true;
				
				isAccountActive = docAccount.getItemValueString("cAccountActivated").equals("yes");
				
				if (isActivation() && isAccountActive) {
	    			Utils.addWarningMessage("This account has already been activated. If you have lost your password, click <a href=\"" + lostPasswordLink + "&for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "\">here</a>.");
	    			return;
				}
				
				if (isLostPassword() && !isAccountActive) {
					Utils.addWarningMessage("This account hasn't been activated yet. Click <a href=\"account.xsp?type=" + TYPE_ACTIVATE + "&for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "\">here</a> to start the activation process.");
	    			return;
				}
				
				//create a random passcode and store a hash of it in the user's account document
				//the passcode is used in the link send to the user and validated when the user tries to set his password
				session = ExtLibUtil.getCurrentSession();
				
				String passCode = com.teamstudio.continuity.utils.Utils.getRandomString(16, true);
				
				docAccount.replaceItemValue("cPasscodeHash", session.hashPassword(passCode));
				docAccount.replaceItemValue("cPasscodeRequestAt", (new Date()).getTime());		//requested time as a number
				docAccount.save();
				
				Logger.debug("passcode save in user account document for user " + docAccount.getItemValueString("FullName") );
				
				//send a notification to the user
				Mail mail = new Mail();
				mail.setAsSigner(true);
				mail.setTo(emailAddress);
				
				if (isActivation()) {
					
					mail.setSubject("Activate your account");
					
					String linkActive = config.getDbUrl() + "/account.xsp?type=" + TYPE_ACTIVATE + "&for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "&key=" + java.net.URLEncoder.encode(passCode, "UTF-8");
					
					mail.addHTML("<p>To activate you Continuity account you need to open <a href=\"" + linkActive + "\">this</a> link and set a password.</p>" +
							"<p>After doing so you can use this email address (" + emailAddress + ") and the new password to configure Unplugged.</p>" +
							AccountFunctions.txtLimitedUse);
					
					mail.send();
					
					Utils.addInfoMessage("An email has been send to you containing instructions on how to complete the activation.");
					
				} else {

					mail.setSubject("Reset your password");
					
					String linkLost = config.getDbUrl() + "/account.xsp?type=" + TYPE_LOST_PASSWORD + "&for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "&key=" + java.net.URLEncoder.encode(passCode, "UTF-8");
					
					mail.addHTML("<p>You have just made a request to reset your password for Continuity.</p>" +
							"<p>Click <a href=\"" + linkLost + "\">here</a> to set a new password.</p>" +
							txtLimitedUse);
					
					mail.send();
					
					Utils.addInfoMessage("An email has been send to you containing instructions on how to set a new password.");
					
				}
				
			} else if (dc.getCount()>1){
				
				Utils.addErrorMessage("Multiple users found with this email address.");
				return;
				
			} else {

    			//invalid user/ e-mail address
				Utils.addErrorMessage("You have entered an unknown email address.");
				return;
			}
			
						
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.incinerate(docAccount, vwUsers, dbDirectory, session);
		}
		
	}
	
	/*
	 * This function is called when the lost password/ activate page is opened. If the url contains email and passcode parameters,
	 * it validates if the values are correct and tries to retrieve the user's account document
	 * 
	 * It will send an error message to the user if the link is not valid
	 */
	public void validateInputs() {
		
		Session session = null;
		Database dbDirectory = null;
		Document docAccount = null;
		DocumentCollection dc = null;
		View vwUsers = null;
		
		try {
			
			XSPContext context = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
			
			this.emailAddress = context.getUrlParameter("for");
			this.passCode = context.getUrlParameter("key");
			
			if (StringUtil.isNotEmpty(emailAddress) && StringUtil.isNotEmpty(passCode) ) {
				
				this.isValidateKey = true;
				
				session = ExtLibUtil.getCurrentSessionAsSigner();
				dbDirectory = session.getDatabase( config.getServerName(), config.getDirectoryDbPath());
				vwUsers = dbDirectory.getView("($Users)");
				
				dc = vwUsers.getAllDocumentsByKey(emailAddress, true);
				
				if (dc.getCount()==1) {
					docAccount = dc.getFirstDocument();
				}
				
				if (null == docAccount) {
	
					isPWChanged = true;
					Utils.addErrorMessage("Invalid request");
					
				} else {
					
					accountUnid = docAccount.getUniversalID();
					
					userName = docAccount.getItemValueString("FullName");
					displayName = docAccount.getItemValueString("FirstName") + " " + docAccount.getItemValueString("LastName");
					existingUser = true;
					
					Name nmUser = session.createName(userName);
					orgAlias = nmUser.getOrganization();
					nmUser.recycle();
					
					String passCodeHash = docAccount.getItemValueString( "cPasscodeHash");
					passCodeRequestedAt = (long) docAccount.getItemValueDouble("cPasscodeRequestAt");		//stored as long)
					
					isAccountActive = docAccount.getItemValueString("cAccountActivated").equals("yes");
					
					if (isActivation() && isAccountActive) {
						Utils.addErrorMessage("This account has already been activated. If you have lost your password, click <a href=\"" + lostPasswordLink + "&for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "\">here</a>.");
						isPWChanged = true;
						return;
					}
									
					//check if the request is still valid (valid for 30 minutes)
					long now = (new Date().getTime());
					
					if ( now > (passCodeRequestedAt + ( 30 * 60 * 1000 )) ) {
						Utils.addErrorMessage("This request has expired.");
						isPWChanged = true;
						return;
					}
					
					//verify the passcode
					if ( !session.verifyPassword(passCode, passCodeHash) ) {
						isPWChanged = true;
						Utils.addErrorMessage("Invalid request");
						return;
					}
					
				}
			}
		} catch (Exception e) {

			e.printStackTrace();
		} finally {
			
			Utils.incinerate(docAccount, vwUsers, dc, dbDirectory, session);
		}
		
	}
	
	public boolean isValidateKey() {
		return isValidateKey;
	}
	
	//change the password for a user in the user's account document
	//this function is used for the password recovery function as well as the user activation function
	public void changePassword() {
		
		Document docAccount = null;
		Session sessionAsSigner = null;
		Database dbDirectory = null;
		
		try {

			if (StringUtil.isEmpty(newPassword) || StringUtil.isEmpty(newPasswordRepeat)) {
				Utils.addWarningMessage("You have to enter the new password twice");
				return;
			}
			
			if (!newPassword.equals(newPasswordRepeat)) {
				Utils.addWarningMessage("The passwords do not match");
				return;
			}
			
			//store new password in Person document
			sessionAsSigner = ExtLibUtil.getCurrentSessionAsSigner();
			dbDirectory = sessionAsSigner.getDatabase( config.getServerName(), config.getDirectoryDbPath());

			docAccount = dbDirectory.getDocumentByUNID(accountUnid);
					
			if (null != docAccount) {
				
				Logger.debug("updating password in account document");
				
				docAccount.replaceItemValue("HTTPPassword", newPassword);
				docAccount.removeItem("cPasscodeHash");
				docAccount.removeItem("cPasscodeRequestAt");
				docAccount.replaceItemValue("cAccountActivated", "yes");
				docAccount.computeWithForm(true, true);
				docAccount.save();
				
				if (!isAccountActive) {
					
					//account isn't active yet: mark as active in the user's profile 
					Document docUserProfile = getUserProfile(userName );
					
					if (null != docUserProfile) {
						
						docUserProfile.replaceItemValue("status", "active");
						docUserProfile.save();
						docUserProfile.recycle();
						
						Logger.debug("user marked active in contact document");
				
					
					}
					
					//mark user active in Unplugged configuration
					activateUnpluggedUser( userName);
					
				}
				
				isPWChanged = true;
				
				//refresh $Users/ $Groups view
				View vwTemp = dbDirectory.getView("($Users)");
				vwTemp.refresh();
				vwTemp.recycle();
				vwTemp = dbDirectory.getView("($Groups)");
				vwTemp.refresh();
				vwTemp.recycle();
				
				if (isActivation() ) {
					Utils.addInfoMessage("Your password has been set and your account activated. Click <a href=\"" + config.getHostUrl() + "/?for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "\">here</a> to sign in.");
				} else {
					Utils.addInfoMessage("Your password has been changed. Click <a href=\"" + config.getHostUrl() + "/?for=" + java.net.URLEncoder.encode(emailAddress, "UTF-8") + "\">here</a> to sign in.");
				}
				
				
			} else {
				
				Utils.addErrorMessage("An error occurred: your password has not been changed.");
			
			}

			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.incinerate(docAccount, dbDirectory, sessionAsSigner);
		}
		
		
	}
	
	private Document getUserProfile(String userName) {
		
		Document docUserProfile = null;
		Database dbCore = null;
		View vwUserProfiles = null;
		Session sessionAsSigner = null;
		
		try {
			
			String coreDbPath = config.getCoreDbPath(orgAlias);
			
			if ( StringUtil.isNotEmpty(coreDbPath) ) {
		
				//open core database and mark user's account as active
				sessionAsSigner = ExtLibUtil.getCurrentSessionAsSigner();
				dbCore = sessionAsSigner.getDatabase( config.getServerName(), coreDbPath);
				vwUserProfiles = dbCore.getView("vwContactsByUsername");
				docUserProfile = vwUserProfiles.getDocumentByKey(userName );
				
				if (docUserProfile==null) {
					Logger.warn("user document for " + userName + " could not be found in " + coreDbPath);
				}

			} else {
				
				Logger.warn("core database path not specified for user " + userName + "' organisation");
				
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return docUserProfile;
	}
	
	//activate a 'user' in Unplugged (if the user exists)
	private boolean activateUnpluggedUser(String userName) {
		
		Session sessionAsSigner = null;
		Database dbUnplugged = null;
		Document docUser = null;
		View vwUsers = null;
		Name nmUser = null;
		
		String USERS_VIEW = "UsersAll";
		
		try {
			
			Logger.debug("activating Unplugged user " + userName);
			
			Configuration config = Configuration.get();
			
			//open unplugged db
			sessionAsSigner = Utils.getCurrentSessionAsSigner();
			dbUnplugged = sessionAsSigner.getDatabase( config.getServerName(), config.getUnpluggedDbPath());

			//create notes name object for user
			nmUser = sessionAsSigner.createName(userName);
			
			//check if user already exists in Unplugged
			vwUsers = dbUnplugged.getView(USERS_VIEW);
			docUser = vwUsers.getDocumentByKey( nmUser.getAbbreviated(), true);
			
			if (docUser != null && !docUser.getItemValueString("Active").equals("1") ) {
				
				//mark user as active
				docUser.replaceItemValue("Active", "1");
				docUser.save();
				docUser.recycle();
				
			}	
			
		} catch (NotesException e) {
			
			Logger.error(e);
		} finally{
			
			
			Utils.recycle(docUser, nmUser, dbUnplugged);
			
		}
		
		return true;
		
	}
	
	public boolean isPWChanged() {
		return isPWChanged;
	}
	
	public boolean isActivation() {
		return action.equals(TYPE_ACTIVATE);
	}

	public boolean isLostPassword() {
		return action.equals(TYPE_LOST_PASSWORD);
	}
    
	public String getEmailAddress() {
		return this.emailAddress;
	}
	public void setEmailAddress(String email) {
		this.emailAddress = email;
	}
	public String getPassCode() {
		return passCode;
	}
	public void setPassCode(String passCode) {
		this.passCode = passCode;
	}
	public boolean isExistingUser() {
		return existingUser;
	}
	public String getName() {
		return displayName;
	}
	
	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

	public String getNewPasswordRepeat() {
		return newPasswordRepeat;
	}

	public void setNewPasswordRepeat(String newPasswordRepeat) {
		this.newPasswordRepeat = newPasswordRepeat;
	}

	public void redirect() {
		//redirect to server root
		try {
			
			FacesContext.getCurrentInstance().getExternalContext().redirect( ( StringUtil.isNotEmpty(redirectTo) ? redirectTo : config.getHostUrl()) );
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}

