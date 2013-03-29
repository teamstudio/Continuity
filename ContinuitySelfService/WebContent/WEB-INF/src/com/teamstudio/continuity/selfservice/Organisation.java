package com.teamstudio.continuity.selfservice;

import java.io.File;
import java.io.Serializable;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.apps.Unplugged;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.Document;

public class Organisation implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	String folder;
	String name;
	String alias;
	
	String adminFirstName;
	String adminLastName;
	String adminEmail;
	
	String coreDbPath;
	String continuityDbPath;
	
	Configuration config;
	
	private static final String CORE_DB_FILENAME = "core.nsf";
	private static final String CONTINUITY_DB_FILENAME = "continuity.nsf";
	
	private static final String ADMINS_GROUP = "LocalDomainAdmins";
	
	
	public Organisation() {	}

	  /*
	 * process new organisation:
	 * - create core db
	 * - create continuity db
	 * - create default admin user
	 * - setup acl
	 */
	public boolean save( com.ibm.xsp.model.domino.wrapped.DominoDocument docOrg ) {
		
		boolean result = false;
		
		try {
			
			config = Configuration.get();
			
			if (docOrg.getItemValueString("setupPerformed").equals("") ) {
			
				adminFirstName = docOrg.getItemValueString("adminFirstName");
				adminLastName = docOrg.getItemValueString("adminLastName");
				adminEmail = docOrg.getItemValueString("adminEmail");
				
				folder = docOrg.getItemValueString("folder");
				name = docOrg.getItemValueString("name");
				alias = docOrg.getItemValueString("alias");
				
				coreDbPath = config.getInstallBasePath() + File.separator + folder + File.separator + CORE_DB_FILENAME;
				continuityDbPath = config.getInstallBasePath() + File.separator + folder + File.separator + CONTINUITY_DB_FILENAME;
				
				Logger.info("setup new Continuity instance for " + name + ", " + alias + ", folder: " + folder);
				Logger.info("admin: " + adminFirstName + " " + adminLastName + " (email: " + adminEmail + ")");
				
				setupCoreDb();
				
				setupContinuityDb();
				
				result = true;
				
				docOrg.replaceItemValue("coreDbPath", coreDbPath);
				docOrg.replaceItemValue("continuityDbPath", continuityDbPath);
				
				docOrg.replaceItemValue("setupPerformed", "true");
				
			}
			
			docOrg.save();
			

			//create Unplugged configuration for this user
			Unplugged.createAppDefinition( continuityDbPath, false, true);
			Unplugged.createAppDefinition( coreDbPath, true, false);
			
		} catch (NotesException e) {
			Logger.error(e);
		}
		
		return result;
			
	}
	
	private void setupCoreDb() {
		
		Database dbTemplate = null;
		Database dbCore = null;
		
		try {
			
			//get core template
			Session session = ExtLibUtil.getCurrentSession();
			dbTemplate = session.getDatabase(config.getServerName(), config.getCoreDbTemplatePath());
			
			if (!dbTemplate.isOpen()) {
				throw(new Exception("could not open core database template at " + config.getCoreDbTemplatePath()) );
			}
			
			Logger.info("create database at " + coreDbPath + " from template " + config.getCoreDbTemplatePath());
			
			//create new database
			dbCore = dbTemplate.createFromTemplate(null, coreDbPath, true);
			
			//cleanup target database
			dbCore.getAllDocuments().removeAll(true);
			
			dbCore.setTitle( "Core (" + name + ")");
			dbCore.setDesignLockingEnabled(false);		//disable design locking
			
			initACL(session, dbCore, "core");
			
			//create settings document (authors = [bvEditor] role)
			Document docSettings = dbCore.createDocument();
			docSettings.replaceItemValue("form", "fSettings");
			docSettings.replaceItemValue("organisationId", alias);
			docSettings.replaceItemValue("organisationName", name );
			docSettings.replaceItemValue("unpluggedDbPath", config.getUnpluggedDbPath());
			docSettings.replaceItemValue("directoryDbPath", config.getDirectoryDbPath());
			docSettings.replaceItemValue("docAuthors", Configuration.ROLE_EDITOR).setAuthors(true);
			
			docSettings.save();
			docSettings.recycle();
			
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.incinerate(dbCore, dbTemplate);
			
		}
		
	}
	
	private void initACL(Session session, Database dbTarget, String type) {
		
		ACL acl = null;
		ACLEntry aclEntry = null;
		
		try {
			Logger.info("setup ACL");
			
			acl = dbTarget.getACL();
					
			//default/ anonymous: no access
			aclEntry = Authorizations.createACLEntry(acl, "-Default-", ACLEntry.TYPE_UNSPECIFIED , ACL.LEVEL_NOACCESS );
			aclEntry = Authorizations.createACLEntry(acl, "Anonymous", ACLEntry.TYPE_UNSPECIFIED , ACL.LEVEL_NOACCESS );
			
			//add default admin groups
			Logger.info("add adminstrator group: " + ADMINS_GROUP);
					
			aclEntry = Authorizations.createACLEntry(acl, ADMINS_GROUP, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_MANAGER );
			aclEntry.enableRole( Configuration.ROLE_EDITOR );
			
			if (type.equals("continuity")) {
				aclEntry.enableRole( Configuration.ROLE_DEBUG );
			}

			//add LocalDomainServers
			Logger.info("- add LocalDomainServers");
			aclEntry = Authorizations.createACLEntry(acl, "LocalDomainServers", ACLEntry.TYPE_SERVER_GROUP, ACL.LEVEL_MANAGER );
			aclEntry.enableRole( Configuration.ROLE_EDITOR );
			
			//activate all roles for current server
			String server = config.getServerName();
			Logger.info("- add " + server );
			aclEntry = acl.getEntry( server );
			if (aclEntry != null) {
				aclEntry.enableRole( Configuration.ROLE_EDITOR );
			}
				
			//remove signer
			String signerName = session.getEffectiveUserName();
			Logger.info("- remove signer: " + signerName);
			aclEntry = acl.getEntry( signerName );
			if (aclEntry != null) {
				aclEntry.remove();
			}
				
			//generic organisation group
			String group = "*/" + alias;
			Logger.info("add group (" + group + ")");
			aclEntry = Authorizations.createACLEntry(acl, group, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_AUTHOR );
			aclEntry.setCanCreateDocuments(true);
			
			//editors
			group = "group-editors/" + alias;
			Logger.info("add group (" + group + ")");
			aclEntry = Authorizations.createACLEntry(acl, group, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_AUTHOR );
			aclEntry.setCanCreateDocuments(true);
			aclEntry.setCanDeleteDocuments(true);
			aclEntry.enableRole( Configuration.ROLE_EDITOR );
			
			//users
			if (type.equals("continuity")) {
				group = "group-users/" + alias;
				Logger.info("add group (" + group + ")");
				aclEntry = Authorizations.createACLEntry(acl, group, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_AUTHOR );
				aclEntry.setCanCreateDocuments(true);
				aclEntry.enableRole( Configuration.ROLE_USER );
			}
				
			acl.save();
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.incinerate(aclEntry, acl);
		}
	}
	
	private void setupContinuityDb() {
		
		Database dbTemplate = null;
		Database dbContinuity = null;
		
		try {
			
			//get core template
			Session session = ExtLibUtil.getCurrentSession();
			dbTemplate = session.getDatabase(config.getServerName(), config.getContinuityDbTemplatePath());
			
			if (!dbTemplate.isOpen()) {
				throw(new Exception("could not open continuity database template at " + config.getContinuityDbTemplatePath()) );
			}
			
			Logger.info("create database at " + continuityDbPath + " from template at " + config.getContinuityDbTemplatePath() );
			
			//create new database
			dbContinuity = dbTemplate.createFromTemplate(null, continuityDbPath, true);

			//cleanup target database
			dbContinuity.getAllDocuments().removeAll(true);
			
			dbContinuity.setTitle( "Continuity (" + name + ")");
			dbContinuity.setDesignLockingEnabled(false);		//disable design locking
			
			initACL(session, dbContinuity, "continuity");
			
			//create settings document (no authors needed)
			Document docSettings = dbContinuity.createDocument();
			docSettings.replaceItemValue("form", "fSettings");
			docSettings.replaceItemValue("coreDbPath", coreDbPath);			
			docSettings.save();
			docSettings.recycle();
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.incinerate(dbContinuity, dbTemplate);
			
		}
		
	}

	
}
