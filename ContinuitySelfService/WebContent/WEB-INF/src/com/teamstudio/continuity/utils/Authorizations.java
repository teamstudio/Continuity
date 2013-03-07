package com.teamstudio.continuity.utils;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.NotesException;

public class Authorizations {

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
