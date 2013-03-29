package com.teamstudio.continuity;

import java.io.Serializable;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.Document;

public class Configuration implements Serializable {

	private static final long serialVersionUID = -7047733321266533775L;
	
	private static final String BEAN_NAME = "configBean";
	
	private String settingsUnid;
	private String organisationName;
	private String organisationId;
	private String directoryDbPath;
	private String unpluggedDbPath;
	private String continuityDbPath;
	
	private String coreDbPath;
	private String coreDbUrl;
	
	private String serverName;

	private static String VERSION = "v1.1";		//used to determine data version too

	private String appVersion;
	
	public Configuration() {
		reload();
		
	}
	
	public static Configuration get() {
		return (Configuration) Utils.resolveVariable(BEAN_NAME);
	}
	
	/*
	 * Reload the application configuration by reading the settings document in the current
	 * and core database.
	 */
	public void reload() {
	
		Database dbCurrent = null;
		View vwSettings = null;
		Document docSettings = null;
		Database dbCore = null;

		try {
			
			//get path to core database
			
			dbCurrent = ExtLibUtil.getCurrentSession().getCurrentDatabase();
			vwSettings = dbCurrent.getView("vwSettings");

			docSettings = vwSettings.getDocumentByKey("fSettings", true);
			
			serverName = dbCurrent.getServer();
			coreDbPath = docSettings.getItemValueString("coreDbPath");
			coreDbUrl = "/" + coreDbPath.replace("\\", "/");
			continuityDbPath = dbCurrent.getFilePath();
			
			appVersion = docSettings.getItemValueString("appVersion");
			
			Utils.recycle(docSettings, vwSettings, dbCurrent);
			
			//get settings from core db
			Session sessionAsSigner = Utils.getCurrentSessionAsSigner();
			dbCore = sessionAsSigner.getDatabase(serverName, coreDbPath);
			vwSettings = dbCore.getView("vwSettings");
			
			if (StringUtil.isEmpty(coreDbPath)) {
				
				System.out.println("error while reading configuration: core database path not set");
				
			} else {
				
				docSettings = vwSettings.getDocumentByKey("fSettings", true);

				if (docSettings == null) {
					
					System.out.println("could not read settings from core database");
					
				} else {
				
					settingsUnid = docSettings.getUniversalID();
					
					organisationName = docSettings.getItemValueString("organisationName");
					organisationId = docSettings.getItemValueString("organisationId");
					directoryDbPath = docSettings.getItemValueString("directoryDbPath");
					unpluggedDbPath = docSettings.getItemValueString("unpluggedDbPath");
					
					docSettings.recycle();
					
				}
				
				vwSettings.recycle();
				
				if ( !appVersion.equals(VERSION) ) {
					
					dbCurrent = sessionAsSigner.getCurrentDatabase();
					Conversion.startConversion(dbCurrent, coreDbPath);
					
					//update version in settings document (re-retrieve using signer access)
					vwSettings = dbCurrent.getView("vwSettings");
					docSettings = vwSettings.getDocumentByKey("fSettings", true);
					docSettings.replaceItemValue("appVersion", VERSION);
					docSettings.save();
					
				}
				
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.recycle(docSettings, vwSettings, dbCurrent, dbCore);
			
		}
	
	}
	
	public String getSettingsUnid() {
		return settingsUnid;
	}
	
	public String getOrganisationName() {
		return organisationName;
	}

	public String getOrganisationId() {
		return organisationId;
	}

	public String getContinuityDbPath() {
		return continuityDbPath;
	}
	
	public String getDirectoryDbPath() {
		return directoryDbPath;
	}
	
	public String getUnpluggedDbPath() {
		return unpluggedDbPath;
	}

	public String getServerName() {
		return serverName;
	}
		
	public String getCoreDbPath() {
		return coreDbPath;
	}
	public String getCoreDbUrl() {
		return coreDbUrl;
	}
	public String getAppVersion() {
		return appVersion;
	}
	
}
