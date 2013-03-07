package com.teamstudio.continuity.utils;

import java.util.Vector;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.Configuration;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

/*
 * functions to manage groups in a Domino directory
 * 
 * NOTE: the groups in the $Groups view in the NAB are sorted by abbreviated group names (not canonical) 
 */

public abstract class Authorizations {
	
	public static String DIR_GROUP_PREFIX = "group-";
	public static String GROUP_EDITORS = "editors";
	public static String GROUP_USERS = "users";
	
	static final String USERS_VIEW_NAB = "($Users)";
	
	public static String ROLE_EDITOR = "[bcEditor]";
	public static String ROLE_USER = "[bcUser]";
	
	public static String USER_TYPE_CONTACT = "contact";
	public static String USER_TYPE_USER = "user";
	public static String USER_TYPE_EDITOR = "editor";
	
	//add a user to the specified group (and set the description of the group)
	public static void addMemberToGroup( String userName, String userType) {
		
		Document docGroup = null;
		
		try {
			
			Name groupName = getGroupName(userType); 
			
			//create (or get) group document
			docGroup = Authorizations.createGroupDocument( groupName );
		
			//add current user to authorization Group	
			Item itemMembers = docGroup.getFirstItem("Members");
				
			if (itemMembers == null) {
				
				itemMembers = docGroup.replaceItemValue("Members", userName);
				docGroup.save();
				
				Logger.info("user "  + userName + " added to " + groupName);
				
			} else if ( !itemMembers.containsValue(userName) ) {
				
				itemMembers.appendToTextList(userName);
				docGroup.save();
				
				Logger.info("user "  + userName + " added to " + groupName);
			}
			
			
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docGroup);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static void removeMemberFromAllGroups( String userName) {
		
		DocumentCollection dc = null;
		Document docGroup = null;
		Database dbDirectory = null;
		Name nmGroup = null;
		
		try {
			
			if (StringUtil.isEmpty(userName)) {
				throw(new Exception("missing username"));
			}
			
			Configuration config = Configuration.get();
			
			Logger.info("removing " + userName + " from all groups for organisation " + config.getOrganisationId());
			
			dbDirectory = Utils.getDirectory();
			dc = dbDirectory.search("Type=\"Group\" & @IsMember(\"" + userName + "\"; Members)");
			
			Session session = dbDirectory.getParent();
			
			Document docTemp = null;
			
			docGroup = dc.getFirstDocument();
			while (null != docGroup) {
				docTemp = dc.getNextDocument(docGroup);
				
				String groupName = docGroup.getItemValueString("ListName");
				 nmGroup = session.createName(groupName);
				String groupOrg = nmGroup.getOrganization();
				nmGroup.recycle();
				
				//remove users only from groups related to the current instance
				if ( groupOrg.equals( config.getOrganisationId() )) {
					
					Item itemMembers = docGroup.getFirstItem("Members");
					Vector<String> members = itemMembers.getValues();
					members.remove(userName);
	
					if ( members.size() == 0) {
						docGroup.remove(true);
						Logger.info("last user "  + userName + " removed from " + groupName + ": group document removed");
					} else {
						docGroup.replaceItemValue("Members", members);
						docGroup.save();
						Logger.info("user "  + userName + " removed from " + groupName);
					}
					
					itemMembers.recycle();
				}
					
				docGroup = docTemp;
			}
			
		} catch (Exception e) {
			Logger.error(e, "removeMemberFromAllGroups");
			
		} finally {
			
			Utils.recycle(docGroup, dc, dbDirectory);
		}
		
		
	}
	
	//remove a specific user from a group
	@SuppressWarnings("unchecked")
	public static void removeMemberFromGroup( String userName, String userType ){
		
		View viewGroups = null;
		
		try {
			
			Name groupName = getGroupName(userType); 
			
			Logger.info("removing " + userName + " from group " + groupName.getCanonical());
			
			//get groups view
			viewGroups = Utils.getDirectory().getView("($Groups)");
			if ( viewGroups == null ) {
				throw ( new Exception("groups view could not be found") );
			}
			
			Document docGroup = viewGroups.getDocumentByKey(groupName.getAbbreviated() , true);
		
			if (docGroup != null) {
				Item itemMembers = docGroup.getFirstItem("Members");
					
				if (itemMembers != null) {	
					
					
					if ( itemMembers.containsValue(userName) ) {
						
						Vector<String> members = itemMembers.getValues();
						members.remove(userName);

						if ( members.size() == 0) {
							docGroup.remove(true);
							Logger.info("user "  + userName + " removed from " + groupName.getCanonical() + " (was last member: group document removed)");
						} else {
							docGroup.replaceItemValue("Members", members);
							docGroup.save();
							Logger.info("user "  + userName + " removed from " + groupName.getCanonical());
						}
						
					}
				}
				
			}
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {

			try {
				if (viewGroups != null) { viewGroups.recycle(); }
			} catch (Exception e) { }
			
		}
		
	}
	
	public static void deleteGroup(String type) {
	
		Document docGroup = null;
		try {

			Name groupName = getGroupName(type);
			
			//get groups view in directory
			View viewGroups = Utils.getDirectory().getView("($Groups)");
			if ( viewGroups == null ) {
				throw(new Exception("groups view not found in directory"));
			}
			
			docGroup = viewGroups.getDocumentByKey( groupName.getAbbreviated() );
			
			if (docGroup != null) {
				Logger.debug("removing organisation group: " + groupName);
				docGroup.remove(true);
			}
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docGroup);
		}
	
	}
	
	//retrieve a groupName, based on the group type 
	private static Name getGroupName( String type) {
	
		Session session = null;
		Name nmGroup = null;
		
		try {
			session = ExtLibUtil.getCurrentSession();
			
			Configuration config = Configuration.get();
			
			if (type.equals(Authorizations.USER_TYPE_USER )) {
				
				nmGroup = session.createName( Authorizations.DIR_GROUP_PREFIX + Authorizations.GROUP_USERS + "/" + config.getOrganisationId() );
				
			} else if (type.equals( Authorizations.USER_TYPE_EDITOR)) {
				
				nmGroup = session.createName( Authorizations.DIR_GROUP_PREFIX + Authorizations.GROUP_EDITORS + "/" + config.getOrganisationId() );
				
			}
			
		} catch (Exception e) {
			Logger.error(e);
		}

		return nmGroup;
		
	}
	
	/*
	 * create a group  in the specified directory (if it doesn't exist yet)
	 * returns the group  document created/ retrieved
	 */
	private static Document createGroupDocument(Name groupName) {
		
		try {
			
			//get groups view
			View viewGroups = Utils.getDirectory().getView("($Groups)");
			if ( viewGroups == null ) {
				throw ( new Exception("groups view could not be found") );
			}
			
			Document docGroup = viewGroups.getDocumentByKey(groupName.getAbbreviated() , true);
			
			if (docGroup == null) {		//group doesn't exist (yet): create it
				
				Logger.info("create group \"" + groupName + "\"");
			
				docGroup = viewGroups.getParent().createDocument();
				docGroup.replaceItemValue("Form", "Group");
				docGroup.replaceItemValue("Type", "Group");	
				docGroup.replaceItemValue("ListName", groupName.getCanonical());
				docGroup.replaceItemValue("GroupType", "2");		//acl group only
				docGroup.computeWithForm(false, false);
				
			}
			
			return docGroup;
		} catch (Exception e) {
			Logger.error(e);
			return null;
		}
		
	}
	
	//create an account for a user in the (Domino) Directory
	//returns the generated userName, no password is set
	public static String createAccount( String userName, String firstName, String lastName, String email ) {
		
		Document docUser = null;
		Session session = null;
		Database dbDirectory = null;
		
		Name nmUser = null;
		
		try {
			
			//open (secondary) directory db
			session = Utils.getCurrentSessionAsSigner();
			dbDirectory = Utils.getDirectory();
			
			//check if this account already exists
			if ( StringUtil.isNotEmpty(userName) ) {	
				
				View vwUsers = dbDirectory.getView("($Users)");
				docUser = vwUsers.getDocumentByKey(userName, true);
				
				if (docUser == null) {
					
					//not found in secondary address book, try primary
					vwUsers.recycle();
					dbDirectory.recycle();
					
					dbDirectory = session.getDatabase("", "names.nsf");
					
					vwUsers = dbDirectory.getView("($Users)");
					docUser = vwUsers.getDocumentByKey(userName, true);
					
				}
				
				if (docUser == null) {
					throw(new Exception( "Error: could not find person document for " + userName +  " in directory") );
				}
				
			} else {		//create new user
				
				Logger.debug("create username");
				
				Configuration config = Configuration.get();
				
				//generate username
				nmUser = createUserName(session, dbDirectory, firstName, lastName, email, config.getOrganisationId() );
				userName = nmUser.getCanonical();
				
				Logger.debug("username=" + userName );
				
				docUser = dbDirectory.createDocument();
				
				docUser.replaceItemValue("Form", "Person");
				docUser.replaceItemValue("Type", "Person");
				docUser.replaceItemValue("FullName", nmUser.getCanonical() );
				docUser.replaceItemValue("MailSystem", "5");			//Other Internet Mail
				docUser.replaceItemValue("$SecurePassword", "1");		//set "more secure" passwords
				//note: password is not set, so this account can't be used (yet) from a browser/ Unplugged
				
				docUser.replaceItemValue("cAccountActivated", "no");		//by default new accounts are marked as 'not activated' (yet)
				
			}
				
			docUser.replaceItemValue("FirstName", firstName);
			docUser.replaceItemValue("LastName", lastName);
			docUser.replaceItemValue("MailAddress", email);
			docUser.replaceItemValue("InternetAddress", email);

			docUser.computeWithForm(false, false);
				
			if ( docUser.save() ) {
				Logger.info("account in directory for " + userName + " created/ updated");
			} else {
				Logger.info("could not save account document");
			}
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docUser, dbDirectory, nmUser, session);
		}
		
		return userName;
		
	}
	
	//generate a unique username
	private static Name createUserName(Session session, Database dbDirectory, String firstName, String lastName, String email, String organisation) {
		
		View viewUsers = null;
		Document docUser = null;
		
		try {
			//base username consists of firstname + dot + lastname
			//or the part left of the @ sign in the user's email address
			//(spaces converted to dots, invalid characters removed, double dots removed)
			
			String userNameBase = lastName;
			
			if ( StringUtil.isNotEmpty(firstName)) {
				userNameBase = firstName.trim() + "." + userNameBase;
			}
					
			if ( StringUtil.isEmpty(userNameBase) ) {
				//use first part of email address
				userNameBase = email.substring(0, email.indexOf("@"));
			}
					
			userNameBase = userNameBase.toLowerCase()
				.trim()
				.replace(" ", ".")
				.replace("..", ".")
				.replaceAll("[^a-zA-Z0-9.-]", "");

			if ( StringUtil.isEmpty(userNameBase) ) {		//cannot construct name
				return null;
			} else if (userNameBase.length() > 25) {
				//max base length = 25 chars
				userNameBase = userNameBase.substring(0, 24);
			}
			
			//validate uniqueness of constructed username in address book
			viewUsers = dbDirectory.getView( USERS_VIEW_NAB);
			viewUsers.refresh();
			viewUsers.setAutoUpdate(false);
			
			docUser = null;
			
			int counter = 0; 
			boolean addSuffix = false;
		
			if (userNameBase.length() < 3) { 
				userNameBase += "xx";
			}		//short names: append xx by default
			
			Name nmUser = null;
			
			//loop until a unique username was found
			do {

				counter++;
				
				String s = "00" + counter;
				s = "_" + s.substring(s.length()-3);		//suffix = _001, _002, etc.
				
				if (counter > 250 ) {		//failsafe for infinite loops
					return null;
				}
				
				nmUser = session.createName( userNameBase + (addSuffix ? s : "") + "/" + organisation);
				
				Logger.debug("check with name: " + nmUser.getCanonical()); 
				docUser = viewUsers.getDocumentByKey( nmUser.getCanonical() , true);
				
				if (docUser==null) {		//no person document found with this username: use it
					return nmUser;
				}
				
				nmUser.recycle();
				docUser.recycle();
				
				addSuffix = true;
				
			} while ( docUser != null);

		} catch (NotesException e) {

			Logger.error(e);
			
		} finally {
			
			Utils.recycle(docUser, viewUsers);
		}
		
		return null;
	}
	
	//create or update an ACL entry, returns the created/updated entry
	public static ACLEntry createACLEntry(ACL acl, String name, int type, int level) throws NotesException {
		
		//log.debug("create/ get entry: " + name);
		//log.debug("level: " + level + ", type: " + type);
		
		ACLEntry aclEntry = acl.getEntry(name);

		if (aclEntry == null) {
			aclEntry = acl.createACLEntry(name, level);	
		} else {
			
			aclEntry.setLevel(level);
		}
		
		aclEntry.setUserType(type);
		
		return aclEntry;
	}
	
}

