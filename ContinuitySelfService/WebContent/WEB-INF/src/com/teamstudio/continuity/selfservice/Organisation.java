package com.teamstudio.continuity.selfservice;

import java.io.File;
import java.io.Serializable;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.apps.Unplugged;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.DocumentCollection;
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

	boolean isNew;

	String continuityDbPath;

	boolean copyStarterLists;

	private String noteId;

	Configuration config;

	private static final String CONTINUITY_DB_FILENAME = "continuity.nsf";

	private static final String ADMINS_GROUP = "LocalDomainAdmins";

	public Organisation() {
	}

	public void setDocument(DominoDocument docOrg) throws NotesException {

		isNew = docOrg.isNewNote();
		noteId = docOrg.getNoteID();

	}
	
	/*
	 * completely wipes a Continuity instance from this server:
	 * - database
	 * - users and groups in the
	 */

	public boolean remove() {

		boolean success = false;

		config = Configuration.get();

		try { 

			if (isNew) {
				throw (new Exception("cannot remove a new instance"));

			}

			//get the doc
			Session s = ExtLibUtil.getCurrentSession();
			Database dbCurrent = s.getCurrentDatabase();
			Document docOrg = dbCurrent.getDocumentByID(noteId);

			if (null != docOrg) {

			
				String dbPath = docOrg.getItemValueString("continuityDbPath");
				String alias = docOrg.getItemValueString("alias");
				
				if (StringUtil.isEmpty(dbPath) || StringUtil.isEmpty(alias) ) {
					throw( new Exception("missing Continuity db path or alias"));
				}
				
				Logger.debug("Removing Continuity instance for " + alias + " at " + dbPath);

				//remove Continuity application configuration
				Logger.info(" ");
				Unplugged.removeApplication(dbPath);

				//remove Unplugged users from this instance (and their devices) from Unplugged database
				Logger.info(" ");
				Unplugged.removeUsersAndDevices(alias);

				//remove Unplugged users/ groups for this instance from the (secondary) NAB
				Logger.info(" ");
				removeUsersAndGroups(s, alias);
				
				//remove the continuity database
				Logger.info(" ");
				Database dbContinuity = s.getDatabase(dbCurrent.getServer(), dbPath);
				if (dbContinuity.isOpen()) {
					
					Logger.info("Continuity datatabase at  " + dbPath + " opened");
					
					dbContinuity.remove();
					Logger.info("- Continuity database removed" );
				}
				
				//remove the organisation document
				docOrg.remove(true);
				
				Logger.info(" ");
				Logger.info("Instance for " + alias + " removed succesfully");

				success = true;

			}

		} catch (Exception e) {

			Logger.error(e);
		}

		return success;

	}

	private void removeUsersAndGroups( Session s, String alias) throws NotesException {

		Database dbCurrent = ExtLibUtil.getCurrentDatabase();
		Database dbDirectory = s.getDatabase(dbCurrent.getServer(), config.getDirectoryDbPath());

		if (dbDirectory.isOpen()) {
 
			Logger.debug("Directory database opened at " + config.getDirectoryDbPath());

			String alias1 = "/o=" + alias.toLowerCase();

			//remove users
			DocumentCollection dc = dbDirectory.search("Type=\"Person\" & @Contains(@LowerCase(FullName); \"" + alias1 + "\")");

			Logger.debug("- Found " + dc.getCount() + " users in directory for instance \"" + alias + "\"");
 
			dc.removeAll(true);
			Logger.info("- users removed");

			//remove groups
			dc = dbDirectory.search("Type=\"Group\" & @Contains(@LowerCase(ListName); \"" + alias1 + "\")");

			Logger.debug("- found " + dc.getCount() + " groups in directory for instance \"" + alias + "\"");

			dc.removeAll(true);
			Logger.info("- groups removed");

		}

	}

	/*
	 * process new organisation: - create continuity db - setup acl
	 */
	public boolean save(com.ibm.xsp.model.domino.wrapped.DominoDocument docOrg) {

		boolean result = false;

		try {

			config = Configuration.get();

			if (docOrg.getItemValueString("setupPerformed").equals("")) {

				adminFirstName = docOrg.getItemValueString("adminFirstName");
				adminLastName = docOrg.getItemValueString("adminLastName");
				adminEmail = docOrg.getItemValueString("adminEmail");

				folder = docOrg.getItemValueString("folder");
				name = docOrg.getItemValueString("name");
				alias = docOrg.getItemValueString("alias");

				copyStarterLists = docOrg.getItemValueString("copyStarterLists").equals("true");

				continuityDbPath = config.getInstallBasePath() + File.separator + folder + File.separator + CONTINUITY_DB_FILENAME;

				Logger.info("setup new Continuity instance for " + name + ", " + alias + ", folder: " + folder);
				Logger.info("admin: " + adminFirstName + " " + adminLastName + " (email: " + adminEmail + ")");

				setupContinuityDb();

				result = true;

				docOrg.replaceItemValue("continuityDbPath", continuityDbPath);
				docOrg.replaceItemValue("setupPerformed", "true");

			}

			docOrg.save();

			//create Unplugged configuration for this user
			Unplugged.createAppDefinition(continuityDbPath, false, true);

		} catch (NotesException e) {
			Logger.error(e);
		}

		return result;

	}

	private void initACL(Session session, Database dbTarget) {

		ACL acl = null;
		ACLEntry aclEntry = null;

		try {
			Logger.info("setup ACL");

			acl = dbTarget.getACL();

			//default/ anonymous: no access
			aclEntry = Authorizations.createACLEntry(acl, "-Default-", ACLEntry.TYPE_UNSPECIFIED, ACL.LEVEL_NOACCESS);
			aclEntry = Authorizations.createACLEntry(acl, "Anonymous", ACLEntry.TYPE_UNSPECIFIED, ACL.LEVEL_NOACCESS);

			//add default admin group with editor access and editor role
			if (StringUtil.isNotEmpty(config.getAdminGroup())) {
				aclEntry = Authorizations.createACLEntry(acl, config.getAdminGroup(), ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_EDITOR);
				aclEntry.setCanDeleteDocuments(true);
				aclEntry.enableRole(Configuration.ROLE_EDITOR);
			}

			//add LocalDomainAdmins
			Logger.info("add adminstrator group: " + ADMINS_GROUP);

			aclEntry = Authorizations.createACLEntry(acl, ADMINS_GROUP, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_MANAGER);
			aclEntry.enableRole(Configuration.ROLE_EDITOR);

			aclEntry.enableRole(Configuration.ROLE_DEBUG);

			//add LocalDomainServers
			Logger.info("- add LocalDomainServers");
			aclEntry = Authorizations.createACLEntry(acl, "LocalDomainServers", ACLEntry.TYPE_SERVER_GROUP, ACL.LEVEL_MANAGER);
			aclEntry.enableRole(Configuration.ROLE_EDITOR);

			//activate all roles for current server
			String server = config.getServerName();
			Logger.info("- add " + server);
			aclEntry = acl.getEntry(server);
			if (aclEntry != null) {
				aclEntry.enableRole(Configuration.ROLE_EDITOR);
			}

			//remove signer
			String signerName = session.getEffectiveUserName();
			Logger.info("- remove signer: " + signerName);
			aclEntry = acl.getEntry(signerName);
			if (aclEntry != null) {
				aclEntry.remove();
			}

			//generic organisation group
			String group = "*/" + alias;
			Logger.info("add group (" + group + ")");
			aclEntry = Authorizations.createACLEntry(acl, group, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_AUTHOR);
			aclEntry.setCanCreateDocuments(true);
			aclEntry.setCanDeleteDocuments(false);

			//editors
			group = "group-editors/" + alias;
			Logger.info("add group (" + group + ")");
			aclEntry = Authorizations.createACLEntry(acl, group, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_AUTHOR);
			aclEntry.setCanCreateDocuments(true);
			aclEntry.setCanDeleteDocuments(true);
			aclEntry.enableRole(Configuration.ROLE_EDITOR);

			//users
			group = "group-users/" + alias;
			Logger.info("add group (" + group + ")");
			aclEntry = Authorizations.createACLEntry(acl, group, ACLEntry.TYPE_PERSON_GROUP, ACL.LEVEL_AUTHOR);
			aclEntry.setCanCreateDocuments(true);
			aclEntry.setCanDeleteDocuments(false);
			aclEntry.enableRole(Configuration.ROLE_USER);

			acl.save();

		} catch (Exception e) {
			Logger.error(e);
		} finally {

			Utils.incinerate(aclEntry, acl);
		}
	}

	private void setupContinuityDb() {

		Database dbTemplate = null;
		Database dbNewInstance = null;

		try {

			//get Continuity template
			Session session = ExtLibUtil.getCurrentSession();
			dbTemplate = session.getDatabase(config.getServerName(), config.getContinuityDbTemplatePath());

			if (!dbTemplate.isOpen()) {
				throw (new Exception("could not open continuity database template at " + config.getContinuityDbTemplatePath()));
			}

			Logger.info("create database at " + continuityDbPath + " from template at " + config.getContinuityDbTemplatePath());

			//create new database
			dbNewInstance = dbTemplate.createFromTemplate(null, continuityDbPath, true);

			//remove all documents
			DocumentCollection dcAll = dbNewInstance.getAllDocuments();
			dcAll.removeAll(true);
			Utils.incinerate(dcAll);

			dbNewInstance.setTitle("Continuity (" + name + ")");
			dbNewInstance.setDesignLockingEnabled(false); //disable design locking

			initACL(session, dbNewInstance);

			//create settings document (no authors needed)
			Document docSettings = dbNewInstance.createDocument();
			docSettings.replaceItemValue("form", "fSettings");
			docSettings.replaceItemValue("organisationId", alias);
			docSettings.replaceItemValue("organisationName", name);
			docSettings.replaceItemValue("unpluggedDbPath", config.getUnpluggedDbPath());
			docSettings.replaceItemValue("directoryDbPath", config.getDirectoryDbPath());
			docSettings.replaceItemValue("senderEmail", config.getSenderEmail());
			docSettings.replaceItemValue("senderName", config.getSenderName());
			docSettings.replaceItemValue("docAuthors", Configuration.ROLE_EDITOR).setAuthors(true);

			docSettings.save();
			docSettings.recycle();

			copyStarterLists(dbNewInstance, dbTemplate);

		} catch (Exception e) {
			Logger.error(e);
		} finally {

			Utils.incinerate(dbNewInstance, dbTemplate);

		}

	}

	private void copyStarterLists(Database dbNewInstance, Database dbTemplate) throws NotesException {

		Logger.info("Copy starter lists? " + copyStarterLists);

		if (StringUtil.isEmpty(config.getStarterListsDbTemplatePath())) {

			Logger.info("cannot copy starter lists: database path not filled in");

		} else {

			//copy starter lists
			if (!config.getStarterListsDbTemplatePath().equals(config.getContinuityDbTemplatePath())) {

				Logger.info("start copy starter lists from database " + config.getStarterListsDbTemplatePath());

				//use a different db
				Session session = dbTemplate.getParent();
				Utils.incinerate(dbTemplate);
				dbTemplate = session.getDatabase(config.getServerName(), config.getStarterListsDbTemplatePath());

			} else {

				Logger.info("start copy starter lists from Continuity template database");
			}

			if (dbTemplate.isOpen()) {

				Logger.info("- starter list database succesfully opened");

				DocumentCollection dcForStarters = null;

				if (copyStarterLists) {

					//copy from starter lists db
					dcForStarters = dbTemplate.search("Form = \"fHazard\":\"fHelp\":\"fScenario\":\"fPlan\":\"fRole\":\"fTaskCategory\"");

				} else {

					//copy help only
					dcForStarters = dbTemplate.search("Form = \"fHelp\"");

				}

				Logger.info("found " + dcForStarters.getCount() + " starter documents");

				Document docStarter = dcForStarters.getFirstDocument();
				while (null != docStarter) {

					Document docCopied = docStarter.copyToDatabase(dbNewInstance);

					//clear quick guide link from copied document 
					if (docCopied.hasItem("quickGuideIds")) {
						docCopied.replaceItemValue("quickGuideIds", "");
						docCopied.save();
						Utils.incinerate(docCopied);
					}

					Document docTemp = dcForStarters.getNextDocument(docStarter);
					docStarter.recycle();
					docStarter = docTemp;
				}

				Logger.info("copied all documents");

			} else {

				Logger.error("could not open starter lists database at " + config.getStarterListsDbTemplatePath());

			}

		}

	}

}
