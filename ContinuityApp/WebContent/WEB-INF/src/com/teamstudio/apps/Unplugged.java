package com.teamstudio.apps;

import lotus.domino.*;

import java.util.Vector;

import com.teamstudio.continuity.Configuration;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class Unplugged {
	
	private static final String USERS_VIEW = "UsersAll";

	public Unplugged() { }
	
	/*
	 * Create an Unplugged application definition in the Unplugged database
	 * and add the specified user to it. The user is created if Unplugged
	 * if he doesn't exist yet.
	 */
	@SuppressWarnings("unchecked")
	public static boolean createApplication( String userName, String appPath, boolean isActive) {
		
		Session sessionAsSigner = null;
		Database dbUnplugged = null;
		Document docApp = null;
		Document docUser = null;
		View vwUsers = null;
		Name nmUser = null;
		
		try {
			
			String correctedPath = appPath.replace("\\", "/");
			
			Logger.debug("create unplugged application " + correctedPath + " for " + userName );
			
			Configuration config = Configuration.get();
			
			//open unplugged db
			sessionAsSigner = Utils.getCurrentSessionAsSigner();
			dbUnplugged = sessionAsSigner.getDatabase( config.getServerName(), config.getUnpluggedDbPath());

			//create notes name object for user
			nmUser = sessionAsSigner.createName(userName);
			
			//check if user already exists in Unplugged
			vwUsers = dbUnplugged.getView(USERS_VIEW);
			docUser = vwUsers.getDocumentByKey( nmUser.getAbbreviated(), true);
			
			if (docUser == null) {
				
				//user doesn't exist yet: create
				Unplugged.createUser( dbUnplugged, nmUser.getCanonical(), isActive );
				
			} else 	if (docUser.getItemValueString("Active").equals("1") && !isActive) {
					
				//mark user as inactive
				docUser.replaceItemValue("Active", "0");
				docUser.save();
					
			} else if (!docUser.getItemValueString("Active").equals("1") && isActive ) {
				
				//mark user as active
				docUser.replaceItemValue("Active", "1");
				docUser.save();
				
			}	
							
			//check if an app document for this app already exists and create it if not
			DocumentCollection dcApp = dbUnplugged.search("Form=\"UserDatabase\" & Path=\"" + correctedPath + "\"" );
			
			if (dcApp.getCount()==0) {
				
				//create new app document
				Logger.debug("application not found: create new");
				
				docApp = dbUnplugged.createDocument();
				docApp.replaceItemValue("form", "UserDatabase");
				docApp.replaceItemValue("Path", correctedPath );
				
			} else {
				
				//update existing app document
				docApp = dcApp.getFirstDocument();	

			}
			
			Vector<String> appUsers = docApp.getItemValue("UserName");
			
			if (!appUsers.contains(nmUser.getCanonical())) {

				Logger.debug(nmUser.getCanonical() + " not in list of application users: adding");
				
				appUsers.add(nmUser.getCanonical());
				docApp.replaceItemValue("UserName", appUsers);
				docApp.replaceItemValue("Active", "1");
				docApp.computeWithForm(true, true);
				docApp.save();
			}
			
			Logger.debug("done");
			
		} catch (NotesException e) {
			
			Logger.error(e);
		} finally{
			
			Utils.recycle(docUser, docApp, nmUser, dbUnplugged);
			
		}
		
		return true;
	}
	
	//create an Unplugged user
	private static void createUser( Database dbUnplugged, String userName, boolean isActive) {
		
		Document docUser = null;
		
		try {
			
			Logger.info("create user document for " + userName);
				
			docUser = dbUnplugged.createDocument();
			docUser.replaceItemValue("Form", "User");
			docUser.replaceItemValue("UserName", userName);
			docUser.replaceItemValue("Active", (isActive ? "1": "0"));
			docUser.computeWithForm(true, true);
			docUser.save();
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docUser);
			
		}
		
	}
	
	//removes the specified applications for the user from Unplugged
	@SuppressWarnings("unchecked")
	public static void deleteApplication( String userName, Vector<String> appPaths) {
		
		Session sessionAsSigner = null;
		Database dbUnplugged = null;
		Document docUser = null;
		View vwUsers = null;
		Name nmUser = null;
		Document docApp = null;
		
		try {
			
			Configuration config = Configuration.get();
			
			//open unplugged db
			sessionAsSigner = Utils.getCurrentSessionAsSigner();
			dbUnplugged = sessionAsSigner.getDatabase( config.getServerName(), config.getUnpluggedDbPath());
			
			nmUser = sessionAsSigner.createName(userName);
			
			//get all application documents for this user
			DocumentCollection dcApp = dbUnplugged.search("Form=\"UserDatabase\" & @IsMember(\"" + userName + "\"; UserName)" );
			
			Document docTemp = null;
			
			int numRemoved = 0;
			
			//update app documents
			docApp = dcApp.getFirstDocument();
			while (null != docApp) {
				
				String path = docApp.getItemValueString("Path");
				
				if (appPaths.contains(path)) {
					//remove application 
					Vector<String> appUsers = docApp.getItemValue("UserName");
					
					Logger.debug(nmUser.getCanonical() + " is a user for " + path + " - removing");
					
					appUsers.remove( nmUser.getCanonical() );
					docApp.replaceItemValue("UserName", appUsers);
					docApp.computeWithForm(true, true);
					docApp.save();
					
					numRemoved++;
					
				}
				
				docTemp = dcApp.getNextDocument(docApp);
				docApp.recycle();
				docApp = docTemp;
			}
			
			if (numRemoved == dcApp.getCount()) {		//user removed from all apps - remove user config
				
				Logger.info("Unplugged user " + nmUser.getCanonical() + " removed from all applications - remove user config" );
				
				//check for user account
				vwUsers = dbUnplugged.getView(USERS_VIEW);
				docUser = vwUsers.getDocumentByKey( nmUser.getAbbreviated(), true);
				
				if (docUser != null) {
					docUser.remove(true);
					Logger.info("removed Unplugged user " + nmUser.getCanonical() );
				}
				
			}
										
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docUser, nmUser, dbUnplugged, sessionAsSigner);
		}
		
		
	}
	
}
