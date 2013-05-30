package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.HashMap;

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
	private String senderEmail;
	private String senderName;
	
	private static String DATA_VERSION = "102";
	
	private String serverName;
	
	
	private HashMap<String,String> labels = new HashMap<String,String>();
	
	public Configuration() {
		reload();
	}
	
	public String getAppVersion() {
		return "v1.12";		//current application version
	}
	
	public static Configuration get() {
		return (Configuration) Utils.resolveVariable(BEAN_NAME);

	}
	
	/*
	 * Reload the application configuration by reading the settings document
	 */
	public void reload() {
	
		Database dbCurrent = null;
		View vwAllByType = null;
		Document docSettings = null;

		try {
			
			Session sessionAsSigner = Utils.getCurrentSessionAsSigner();
			dbCurrent = sessionAsSigner.getCurrentDatabase();
			vwAllByType = dbCurrent.getView("vwAllByType");

			docSettings = vwAllByType.getDocumentByKey("fSettings", true);
			
			if (docSettings == null) {
				
				System.out.println("could not read settings document");
					
			} else {
			
				String dataVersion = docSettings.getItemValueString("dataVersion");
				
				if ( !dataVersion.equals(DATA_VERSION) ) {
					
					Conversion.startConversion(vwAllByType);
					
					//update version in settings document
					docSettings = vwAllByType.getDocumentByKey("fSettings", true);
					docSettings.replaceItemValue("dataVersion", DATA_VERSION);
					docSettings.save();
					
				}

				serverName = dbCurrent.getServer();
				continuityDbPath = dbCurrent.getFilePath();
				
				settingsUnid = docSettings.getUniversalID();
				organisationName = docSettings.getItemValueString("organisationName");
				organisationId = docSettings.getItemValueString("organisationId");
				directoryDbPath = docSettings.getItemValueString("directoryDbPath");
				unpluggedDbPath = docSettings.getItemValueString("unpluggedDbPath");
				
				senderEmail = docSettings.getItemValueString("senderEmail");
				senderName = docSettings.getItemValueString("senderName");
				
				//defaults
				if (senderEmail.length()==0) {
					senderEmail = "no-reply@continuity.com";
				}
				if (senderName.length()==0) {
					senderName = "Continuity";
				}
				
				//labels
				if (docSettings.getItemValueString("riskNaming").equals("activities")) {
					labels.put("assets", "Activities");
					labels.put("asset", "Activity");
				} else {
					labels.put("assets", "Assets");
					labels.put("asset", "Asset");
				}
				
				if (docSettings.getItemValueString("incidentNaming").equals("crises")) {
					labels.put("incidents", "Crises");
					labels.put("incident", "Crisis");
				} else if (docSettings.getItemValueString("incidentNaming").equals("emergencies")) {
					labels.put("incidents", "Emergencies");
					labels.put("incident", "Emergency");
				} else {
					labels.put("incidents", "Incidents");
					labels.put("incident", "Incident");
				}
				
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.recycle(docSettings, vwAllByType, dbCurrent);
			
		}
	
	}
	
	public String getLabel(String key) {
		return labels.get(key);
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

	public String getSenderEmail() {
		return senderEmail;
	}

	public String getSenderName() {
		return senderName;
	}
		
}
