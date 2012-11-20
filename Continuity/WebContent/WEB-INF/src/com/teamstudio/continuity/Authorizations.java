package com.teamstudio.continuity;

import java.util.Vector;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.Document;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.View;

public abstract class Authorizations {
	
	public static String DIR_GROUP_PREFIX = "#";
	
	public static String ROLE_EDITOR = "[bcEditor]";
	public static String ROLE_USER = "[bcUser]";
	
	private static String DIR_GROUP_EDITOR_SUFFIX = "-editors";
	private static String DIR_GROUP_USER_SUFFIX = "-users";
	
	public static String USER_TYPE_EDITOR = "editor";
	public static String USER_TYPE_USER = "user";
	
	//add a member to a specific organisation group
	public static void addMemberToOrgGroup( String userName, String orgId, String type, String orgName) {

		addMemberToGroup(
				userName,
				getGroupName(orgId, type),
				orgName
		);
		
	}
	
	public static void createOrgGroup( String orgId, String type, String groupDescription, Vector<String> members) {
		
		try {
			
			//create (or get) group document
			Document docGroup = Authorizations.createGroupDocument(getGroupName(orgId, type), groupDescription);
			docGroup.replaceItemValue("Members", members);
			docGroup.save();
			docGroup.recycle();
							
		} catch (Exception e) {
			
		}
		
	}
	
	//add a user to the specified group (and set the description of the group)
	public static void addMemberToGroup( String userName, String groupName, String description) {
		
		try {
			
			//create (or get) group document
			Document docGroup = Authorizations.createGroupDocument(groupName, description);
		
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
			
			docGroup.recycle();
			
		} catch (Exception e) {
			Logger.error(e);
		}
		
	}
	
	//remove a specific user from a group
	@SuppressWarnings("unchecked")
	public static void removeMemberFromGroup( String userName, String groupName ){
		
		View viewGroups = null;
		
		try {
			
			//check group name for default prefix
			if (groupName.indexOf(Authorizations.DIR_GROUP_PREFIX) != 0) {
				groupName = Authorizations.DIR_GROUP_PREFIX + groupName;
			}
			
			//get groups view
			viewGroups = Utils.getDirectory().getView("($Groups)");
			if ( viewGroups == null ) {
				throw ( new Exception("groups view could not be found") );
			}
			
			Document docGroup = viewGroups.getDocumentByKey(groupName, true);
		
			if (docGroup != null) {
				Item itemMembers = docGroup.getFirstItem("Members");
					
				if (itemMembers != null) {	
					
					
					if ( itemMembers.containsValue(userName) ) {
						
						Vector<String> members = itemMembers.getValues();
						members.remove(userName);

						if ( members.size() == 0) {
							docGroup.remove(true);
							Logger.info("user "  + userName + " removed from " + groupName + " (was last member: group document removed)");
						} else {
							docGroup.replaceItemValue("Members", members);
							docGroup.save();
							Logger.info("user "  + userName + " removed from " + groupName);
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
	
	public static void deleteOrgGroup( String orgId, String type) {
	
		try {

			String groupName = getGroupName(orgId, type);
			
			Document docGroup = getGroupDocument(groupName);
			if (docGroup != null) {
				Logger.debug("removing organisation group: " + groupName);
				docGroup.remove(true);
			}
			
		} catch (NotesException e) {
			Logger.error(e);
		}
	
	}
	
	//retrieve a user's groupName, based on an organisation id and group type 
	public static String getGroupName( String orgId, String type) {
	
		String strGroup;
		
		if (type.equals(Authorizations.USER_TYPE_USER )) {
			
			strGroup = Authorizations.DIR_GROUP_PREFIX + orgId + Authorizations.DIR_GROUP_USER_SUFFIX;
			
		} else if (type.equals( Authorizations.USER_TYPE_EDITOR)) {
			
			strGroup = Authorizations.DIR_GROUP_PREFIX + orgId + Authorizations.DIR_GROUP_EDITOR_SUFFIX;
			
		} else {		//general organisation group
			
			strGroup = Authorizations.DIR_GROUP_PREFIX + orgId;
			
		}
		return strGroup;
		
	}
	
	/*
	 * create a group  in the specified directory (if it doesn't exist yet)
	 * returns the group  document created/ retrieved
	 */
	private static Document createGroupDocument(String groupName, String groupDescription) {
		
		try {
			
			if (groupName == null || groupName.length()==0) {
				throw ( new Exception("no group name specified" ) );
			}
			
			//get groups view
			View viewGroups = Utils.getDirectory().getView("($Groups)");
			if ( viewGroups == null ) {
				throw ( new Exception("groups view could not be found") );
			}
			
			if (groupName.indexOf(Authorizations.DIR_GROUP_PREFIX) != 0) {
				groupName = Authorizations.DIR_GROUP_PREFIX + groupName;
			}
			
			Document docGroup = viewGroups.getDocumentByKey(groupName, true);
			
			if (docGroup == null) {		//group doesn't exist (yet): create it
				
				Logger.info("create group for " + groupName + " (" + groupDescription + ")");
			
				docGroup = viewGroups.getParent().createDocument();
				docGroup.replaceItemValue("Form", "Group");
				docGroup.replaceItemValue("Type", "Group");	
				docGroup.replaceItemValue("ListName", groupName);
				docGroup.replaceItemValue("GroupType", "2");		//acl group only
				docGroup.replaceItemValue("ListDescription", groupDescription) ;
				docGroup.computeWithForm(false, false);
			
			} else if ( !docGroup.getItemValueString("ListDescription").equals(groupDescription) ) {
				
				//update description
				docGroup.replaceItemValue("ListDescription", groupDescription) ;
				
			}
			
			return docGroup;
		} catch (Exception e) {
			Logger.error(e);
			return null;
		}
		
	}
	
	//retrieve a (NAB) group document
	private static Document getGroupDocument( String groupName) throws NotesException {
		
		//get groups view in directory
		View viewGroups = Utils.getDirectory().getView("($Groups)");
		if ( viewGroups != null ) {
			
			return viewGroups.getDocumentByKey(groupName, true);
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

