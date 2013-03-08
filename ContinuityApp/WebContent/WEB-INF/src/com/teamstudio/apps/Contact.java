package com.teamstudio.apps;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.EmbeddedObject;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

import com.teamstudio.continuity.Configuration;
import com.teamstudio.continuity.OrganisationUnit;
import com.teamstudio.continuity.Role;
import com.teamstudio.continuity.utils.*;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.component.UIFileuploadEx.UploadedFile;
import com.ibm.xsp.extlib.util.ExtLibUtil;

/*
 * Class representing a 'contact' (user) in the application
 */
public class Contact implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String userName;
	private String userType;
	private String importIdentifier;
	
	private String firstName;
	private String lastName;
	private String name;
	private String jobTitle;
	private String orgUnitName;
	private String email;
	private String phoneMobile;
	private String phoneWork;
	private String phoneHome;
	
	private String roleId;
	private String orgUnitId;
	private String status;

	private UploadedFile photo;
	
	public static final String RT_ITEM_PHOTOS = "photos";
	
	public static final String STATUS_NEW = "new";
	public static final String STATUS_ACTIVE = "active";
	public static final String STATUS_INACTIVE = "inactive";
	
	private transient View vwUsers = null;
	HashMap<String, Vector<String> > callTreeUsers = null;
	
	public Contact() { }
	
	//save the current contact document
	public boolean save( com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocContact ) {
		
		boolean success = false;

		Document docContact = null;
		
		try {
	
			boolean isNewNote = xspDocContact.isNewNote();
			
			userName = xspDocContact.getItemValueString("userName");
			
			if ( isNewNote ) {

				if (StringUtil.isNotEmpty(userName)) {
					status = Contact.STATUS_ACTIVE;		//new user in Continuity linked to existing Domino user - mark as active
				} else {
					status = Contact.STATUS_NEW;		//set default status: not activated/ new
				}
				
				xspDocContact.replaceItemValue("status", status);
				
				//set authors
				Document doc = xspDocContact.getDocument(true);
				Utils.setAuthors(doc, Authorizations.ROLE_EDITOR);

			} else {
				
				status = xspDocContact.getItemValueString("status");
				
			}

			//store name (firstname + lastname)
			setLastName( xspDocContact.getItemValueString("lastName") );
			setFirstName( xspDocContact.getItemValueString("firstName") );
			setEmail(xspDocContact.getItemValueString("email"));
			
			xspDocContact.replaceItemValue("name", name );
			
			//save the current xsp document
			xspDocContact.save();
			
			docContact = xspDocContact.getDocument(true);
			
			if (photo != null) {

				Logger.debug("new photo uploaded");
				
				if (createThumbnail(docContact, photo) ) {
					docContact.save();
				}
			}
			
			userName = docContact.getItemValueString("userName");
			
			//convert username to canonical (if needed)
			if ( isNewNote && StringUtil.isNotEmpty(userName) ) {
				Session session = ExtLibUtil.getCurrentSession();
				Name nmUser = session.createName(userName);
				
				userName = nmUser.getCanonical();
				docContact.replaceItemValue("userName", userName);

			}
				
			userType = docContact.getItemValueString("userType");
			
			//update roleId: set to none if userType=contact or none
			roleId = docContact.getItemValueString("roleId");
			if (userType.equals(Authorizations.USER_TYPE_CONTACT) || userType.equals("none") ) {
				roleId = "none";
			}
			
			//store role
			docContact.replaceItemValue("roleId", roleId);
			docContact.replaceItemValue("roleName", Role.getName(roleId) );			
			
			//store org unit name
			orgUnitId = docContact.getItemValueString("orgUnitId");
			docContact.replaceItemValue("orgUnitName", OrganisationUnit.getName(orgUnitId));
			
			docContact.save();
			
			setupAccount(docContact);
			
			//update calltree info
			updateCallTreeInfo(docContact);
			
			success = true;
			
			Map<String,Object> sessionScope = ExtLibUtil.getSessionScope();
			
			if (userName.equals( sessionScope.get("userName") ) ) {
				Utils.addInfoMessage("Your profile has been saved");
			} else {
				Utils.addInfoMessage("Contact " + name + " has been saved");
			}
			
			Logger.debug("finished");
			
		} catch (Exception e) {
			
			Utils.addErrorMessage("An error occurred while saving this contact: " + e.getMessage());
			
			Logger.error(e);
			
		} finally {
			Utils.recycle(docContact);
		}
		
		return success;
		
	}
	
	/*
	 * Update information on contact documents to be able to display the calltree.
	 * The call tree is based on the 'reports to' attribute of every contact
	 */
	@SuppressWarnings("unchecked")
	private void updateCallTreeInfo(Document docContact ) {
		
		Logger.debug("start the call tree thing");
		
		Database dbCore = null;
		View vwContactsThatLinkTo = null;
		
		ViewNavigator nav = null;
		
		try {
			
			if (StringUtil.isEmpty( docContact.getItemValueString("callTreeLinksTo")) ) {
				return;
			}
			
			dbCore = docContact.getParentDatabase();
			vwContactsThatLinkTo = dbCore.getView("vwContactsThatLinkTo");
			vwUsers = dbCore.getView("vwContactsByUsername");
			vwContactsThatLinkTo.setAutoUpdate(false);
			vwUsers.setAutoUpdate(false);
			
			ViewEntry ve = null;
			ViewEntry veTmp = null;
			
			ArrayList<String> callTreeStart = new ArrayList<String>();
			callTreeUsers = new HashMap<String, Vector<String> >();
			
			//Logger.debug("get main cats");
			
			//find all users that are supposed to call someone else
			nav = vwContactsThatLinkTo.createViewNav(0);
			ve = nav.getFirst();
			while (null != ve) {
				
				userName = (String) ve.getColumnValues().get(0);
				
				Logger.debug("process main cat: " + userName);

				callTreeUsers.put( userName, ve.getDocument().getItemValue("callTreeLinksTo") );
				
				veTmp = nav.getNext();
				ve.recycle();
				ve = veTmp;
			}
			
			//find contacts that aren't called by anyone: that determines the first level of the calltree
			for (String callTreeUser : callTreeUsers.keySet()) {
				
				boolean userIsCalled = false;
				
				Iterator<Vector<String>> callUsers = callTreeUsers.values().iterator();
				while (callUsers.hasNext() && !userIsCalled) {
					userIsCalled = callUsers.next().contains( callTreeUser);
				}
				
				if (!userIsCalled) {
					callTreeStart.add(callTreeUser);
				}

			}
			
			//Logger.debug("update call tree");
			
			for( String userName : callTreeStart) {
				updateCallTree(userName, 1);
			}
						
		
		} catch (Exception e) {
			
			Logger.error(e);
			
		} finally {
			
			Utils.recycle(nav, vwUsers, vwContactsThatLinkTo, dbCore);
		}
	
	}
	
	/*
	 * recursively called function to set the calltree level in contact documents
	 */
	private void updateCallTree(String userName, int level) throws NotesException {
		
		Logger.debug("update call tree for " + userName + ", level: " + level);
		setCallTreeLevel( userName, level);
		
		if (callTreeUsers.containsKey(userName)) {

			//Logger.debug("> should call " + callTreeUsers.get(userName).size() + " users");
			
			for(String shouldCall :  callTreeUsers.get(userName)) {
				updateCallTree( shouldCall, (level+1));
			}
		}
	}
	
	/*
	 * set the call tree level for the specified contact
	 */
	private void setCallTreeLevel( String userName, int level) throws NotesException {
		
		//Logger.debug("- try saving to doc for " + userName);
		
		ViewEntry veContact = vwUsers.getEntryByKey(userName, true);
		
		if (null != veContact) {
			
			Document doc = veContact.getDocument();
			doc.replaceItemValue("callTreeLevel", level);
			doc.save();
			doc.recycle();
			
			//Logger.debug("saved level " + level + " for " + userName);
			veContact.recycle();
		} else {
			
			Logger.warn("contact document not found for " + userName);
			
		}
	}
	
	/*
	 * Create a new contact in the application
	 * 
	 * Uses the importIdentifier attribute to check if the contact already exists
	 */
	public void create() {
		
		Database dbCore = null;
		Document docContact = null;
		View vwContactsById = null;
		Session session = null;
		
		try {
			
			//try to find contact 
			Configuration config = Configuration.get();
			
			session = ExtLibUtil.getCurrentSession();
			dbCore = session.getDatabase( config.getServerName(), config.getCoreDbPath() );
			vwContactsById = dbCore.getView("vwContactsByIdentifier");
			
			docContact = vwContactsById.getDocumentByKey(importIdentifier, true);
			
			if (docContact == null) {
			
				status = Contact.STATUS_NEW;
				
				docContact = dbCore.createDocument();
				docContact.replaceItemValue("form", "fContact");
				docContact.replaceItemValue("status", status);
				docContact.replaceItemValue("identifier", importIdentifier);
				
				//default authors
				Utils.setAuthors(docContact, Authorizations.ROLE_EDITOR);

			} else {
				
				//existing user found: overwrite existing details
				userName = docContact.getItemValueString("userName");
				status = docContact.getItemValueString("status");
				
			}
			
			docContact.replaceItemValue("firstName", firstName);
			docContact.replaceItemValue("lastName", lastName);
			docContact.replaceItemValue("name", name);
			docContact.replaceItemValue("email", email);
			
			docContact.replaceItemValue("phoneWork", phoneWork);
			docContact.replaceItemValue("phoneTypePrimary", "work");
			
			docContact.replaceItemValue("jobTitle", jobTitle);
			docContact.replaceItemValue("userType", userType);
			
			if (userType.equals(Authorizations.USER_TYPE_CONTACT) || userType.equals("none")) {
				docContact.replaceItemValue("roleId", "none");
			}
			
			docContact.save();
			
			setupAccount(docContact);
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docContact, vwContactsById, dbCore);
		}
		
	}
	
	/*
	 * Setup an account for a user:
	 *  - add/ remove to Directory groups
	 *  - create Unplugged configuration
	 */
	private void setupAccount( Document docContact) throws NotesException {
		
		Logger.info("set up user - type = " + userType + ", name = " + userName);
		
		//check if we need to create an account for this user
		if (userType.equals("none")) {
			
			userName = "";
			
		} else {		//user should have an account
			
			//create/ update account document
			userName = Authorizations.createAccount( userName, firstName, lastName, email);
				
			//set user as author in contact doc
			Utils.setAuthors(docContact, userName, "userName");
			docContact.save();
				
			if ( status.equals(STATUS_INACTIVE) ) {		//deactivated user: remove from all groups
				
				Authorizations.removeMemberFromAllGroups( userName );
				
			} else {				//active user: add user to/ remove user from relevant groups (based on user type)
				
				Logger.debug("active or new user");
				
				if (userType.equals( Authorizations.USER_TYPE_EDITOR)) {			//add to editor group
					
					Authorizations.addMemberToGroup( userName, Authorizations.USER_TYPE_EDITOR);
					
				} else if (userType.equals(Authorizations.USER_TYPE_USER)) {		//add to user group
					
					Authorizations.addMemberToGroup( userName, Authorizations.USER_TYPE_USER);
					
				} else {		//standard contact doesn't need to be in any groups: remove from all
					
					Authorizations.removeMemberFromAllGroups(userName );
					
				}
				
			}
	
			//create Unplugged configuration for this user
			Unplugged.createApplication(userName, Configuration.get().getContinuityDbPath(), status.equals( Contact.STATUS_ACTIVE ));
			Unplugged.createApplication(userName, Configuration.get().getCoreDbPath(), status.equals( Contact.STATUS_ACTIVE ));
		}
		
	}
	
	/*
	 * Remove the current contact document
	 * including related items: user account and Unplugged configuration
	 */
	public boolean remove( com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocContact ) {
		
		boolean success = false;
		Document docContact = null;
		Name nmUser = null;
		
		try {
			
			Logger.debug("removing user " + xspDocContact.getItemValueString("name") + " (userName: " + xspDocContact.getItemValueString("userName") +")");
		
			docContact = xspDocContact.getDocument(true);
			String userName = docContact.getItemValueString("userName");
			String name = docContact.getItemValueString("name");
			
			if (userName.length()>0) {		//user has an account: cleanup
				
				Session session = ExtLibUtil.getCurrentSession();
				nmUser = session.createName( userName);
				String userOrg = nmUser.getOrganization();
				nmUser.recycle();
				
				String currentOrgId = Configuration.get().getOrganisationId();
				
				if (userOrg.equals( currentOrgId )) {
					/*
					 * Cleanup this user's account only if the user was created in this instance.
					 * Normally this is always the case, unless users have been copied between instances or imported from the main address book:
					 * In that scenario: don't delete the user's account document
					 */
	
					Logger.info("user's organisation (" + userOrg + ") matches current organisation's: " + currentOrgId + ", remove account for " + userName);
					
					//remove from all groups
					Authorizations.removeMemberFromAllGroups(userName);
					
					//remove user's account from secondary user Directory
					deleteAccount( userName);
					
				} else {
					
					Logger.info("account not removed: user's organisation (" + userOrg + ") does not match the current organisation's: " + currentOrgId);
					
				}
				
				//remove this Continuity application for this user from Unplugged
				Vector<String> removeApps = new Vector<String>();
				removeApps.add( Configuration.get().getContinuityDbPath() );
				removeApps.add( Configuration.get().getCoreDbPath() );
				
				Unplugged.deleteApplication( userName, removeApps);
				
			}
				
			//remove user's contact document in current databse
			docContact.remove(true);
				
			success = true;
				
			Utils.addInfoMessage("Contact " + name + " has been removed");
			
		} catch (Exception e) {
			Logger.error(e);
			
			Utils.addErrorMessage("An error occurred while removing the contact");
			
		} finally {
			
			Utils.recycle(nmUser, docContact);
		}
		
		return success;
	}
	
	//remove a user's account from the Directory
	private void deleteAccount( String userName ) {
		
		Document docUser = null;
		Database dbDirectory = null;
		View vwUsers = null;
		
		final String VIEW_USERS = "($Users)";
		
		try {
			
			//open directory db
			dbDirectory = Utils.getDirectory();
			vwUsers = dbDirectory.getView(VIEW_USERS);
			
			docUser = vwUsers.getDocumentByKey(userName, true);
			
			if (null != docUser) {
				
				docUser.remove(true);
				Logger.info("account document for " + userName + " removed");
				
			} else {
				
				Logger.info("account document for " + userName + " not found");
			}
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docUser, dbDirectory);
		}
		
	}
	
	//change a user's password
	public static boolean changePassword(String userName, String currentPassword, String newPassword, String newPasswordRepeat ) {

		boolean result = false;
		Document docUser = null;
		
		Session session = null;
		Database dbDirectory = null;
		View vwUsers = null;
		
		try {
			
			if ( StringUtil.isEmpty(currentPassword) || StringUtil.isEmpty(newPassword) ) {
				Utils.addErrorMessage("Missing current password or new password");
				return result;
			}
			
			if ( !newPassword.equals(newPasswordRepeat)) {
				Utils.addErrorMessage("New passwords do not match");
				return result;
			}
			
			if (currentPassword.equals(newPassword)) {
				Utils.addErrorMessage("Please enter a different password from your previous one");
				return result;
			}
			
			session = Utils.getCurrentSessionAsSigner();
			dbDirectory = Utils.getDirectory();
			vwUsers = dbDirectory.getView("($Users)");
			
			docUser = vwUsers.getDocumentByKey(userName, true);
			if (docUser == null ) {
				throw( new Exception( "Account for user " + userName + " not found") );
			}
			
			//validate current pw
			if ( !session.verifyPassword(currentPassword, docUser.getItemValueString("HTTPPassword"))) {
				Utils.addErrorMessage("The current password is incorrect");
				return result;
			}
			
			Logger.info("changing password for user " + userName);
			
			docUser.replaceItemValue("HTTPPassword", newPassword);
			docUser.computeWithForm(true, true);
			
			result = docUser.save();
			
			if (result) {
				Utils.addInfoMessage("Your password has been changed");
			}
			
		} catch (Exception e) {
			
			Logger.error(e);
		} finally {
			Utils.recycle(docUser, dbDirectory, session);
			
		}
		
		return result;
	}
	
	//create a thumbnail image and store it in the "photos" rich text item
	private static boolean createThumbnail( Document doc, UploadedFile photo ) {
		
		RichTextItem rtPhotos = null;
		boolean result = false;
		
		
		try {
			
			File uploadedFile = photo.getUploadedFile().getServerFile();
			if (!uploadedFile.exists()) {
				throw( new Exception("cannot find uploaded file"));
			}
		
			//remove existing images
			if (doc.hasItem( Contact.RT_ITEM_PHOTOS)) {
				rtPhotos = (RichTextItem) doc.getFirstItem(Contact.RT_ITEM_PHOTOS);
				rtPhotos.remove();
				rtPhotos.recycle();
			}
		
			//create new rich text item
			rtPhotos = doc.createRichTextItem(Contact.RT_ITEM_PHOTOS);
	
			
			ImageResizer resizer = new ImageResizer(uploadedFile);
			
			//create a thumbnail for the uploaded file
			resizer.setTargetSize(150, 150);
			String thumbnailFileName = "cont_thumbnail.jpg";
			
			File thumbnail = resizer.resize( uploadedFile.getParentFile().getAbsolutePath() + File.separator + thumbnailFileName);
			doc.replaceItemValue("photoThumbnailName", thumbnailFileName );
			
			rtPhotos.embedObject(EmbeddedObject.EMBED_ATTACHMENT, "", thumbnail.getAbsolutePath(), null);
			
			thumbnail.delete();
		
			//create a medium sized image (250x250)
			//Logger.debug("creating medium sized photo");
			
			///resizer = new ImageResizer(uploadedFile );
			//resizer.setTargetSize(100, 100);
			//String thumbnailMedFileName = "cont_photo_med.jpg";
			//File photoMedium = resizer.resize( uploadedFile.getParentFile().getAbsolutePath() + File.separator + thumbnailMedFileName);
			//docContact.replaceItemValue("photoMed", thumbnailMedFileName);
			//rt = docContact.createRichTextItem("photoMed");
			//rt.embedObject(EmbeddedObject.EMBED_ATTACHMENT, "", photoMedium.getAbsolutePath(), null);
			
			//photoMedium.delete();
			
			result = true;
			
		} catch (Exception e) {
			Logger.error(e);
		
		} finally {
			Utils.recycle(rtPhotos);
		}
		
		return result;
	
	}

	//getter/setter for a file upload
  	public UploadedFile getPhoto() {
		return this.photo;
  	}
	public void setPhoto(UploadedFile photo) {
		this.photo = photo;
	}

	public String getName() {
		return name;
	}
	
	public String getFirstName() {
		return firstName;
	}
	public void setFirstName(String firstName) {
		this.firstName = firstName;
		this.name = (firstName + " " + lastName).trim();
	}

	public String getLastName() {
		return lastName;
	}
	public void setLastName(String lastName) {
		this.lastName = lastName;
		this.name = (firstName + " " + lastName).trim();
	}
	
	public String getUserType() {
		return userType;
	}
	public void setUserType( String to ) {
		userType = to;
	}

	public String getJobTitle() {
		return jobTitle;
	}
	public void setJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
	}

	public String getOrgUnitName() {
		return orgUnitName;
	}
	public void setOrgUnitName(String orgUnitName) {
		this.orgUnitName = orgUnitName;
	}

	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhoneMobile() {
		return phoneMobile;
	}
	public void setPhoneMobile(String phoneMobile) {
		this.phoneMobile = phoneMobile;
	}
	
	public String getPhoneWork() {
		return phoneWork;
	}
	public void setPhoneWork(String phoneWork) {
		this.phoneWork = phoneWork;
	}
	
	public String getPhoneHome() {
		return phoneHome;
	}
	public void setPhoneHome(String phoneHome) {
		this.phoneHome = phoneHome;
	}
	
	public void setImportIdentifier( String to) {
		this.importIdentifier = to;
	}
}
