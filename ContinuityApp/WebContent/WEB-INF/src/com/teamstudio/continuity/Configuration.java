package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.HashMap;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.Document;

public class Configuration implements Serializable {

	private static final long serialVersionUID = -7047733321266533775L;
	
	private static final String BEAN_NAME = "configBean";
	
	public static String CALLTREE_TYPE_ROLE = "role";
	public static String CALLTREE_TYPE_CUSTOM = "custom";
	
	private String settingsUnid;
	private String organisationName;
	private String organisationId;
	private String directoryDbPath;
	private String unpluggedDbPath;
	private String continuityDbPath;
	private String senderEmail;
	private String senderName;
	
	private String callTreeType;
	
	private static String APP_VERSION = "v1.31";		//current application version
	private static String DATA_VERSION = "104";			//data version (used for checking if a conversion is needed)
	
	private String serverName;
	
	
	private HashMap<String,String> labels = new HashMap<String,String>();
	
	public Configuration() {
		reload();
	}
	
	public String getAppVersion() {
		return APP_VERSION;		
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
				
				callTreeType = docSettings.getItemValueString("callTreeType");
				
				if (callTreeType.length()==0) {
					callTreeType = CALLTREE_TYPE_ROLE;		//default: role based
				}
				
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
				String riskNaming = docSettings.getItemValueString("riskNaming");
				String incidentNaming = docSettings.getItemValueString("incidentNaming");
				
				if (riskNaming.equals("activities")) {
					labels.put("assets", "Activities");
					labels.put("asset", "Activity");
				} else if (riskNaming.equals("sites")) {
					labels.put("assets", "Sites");
					labels.put("asset", "Site");
				} else if (riskNaming.equals("locations")) {
					labels.put("assets", "Locations");
					labels.put("asset", "Location");
				} else {
					labels.put("assets", "Assets");
					labels.put("asset", "Asset");
				}
				
				if (incidentNaming.equals("crises")) {
					labels.put("incidents", "Crises");
					labels.put("incident", "Crisis");
				} else if (incidentNaming.equals("emergencies")) {
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
	
	public String getCallTreeType() {
		return callTreeType;
	}
	
	public void setCallTreeCustom() {
		this.callTreeType = CALLTREE_TYPE_CUSTOM;
		
		try {
			Document docSettings = ExtLibUtil.getCurrentDatabase().getDocumentByUNID(settingsUnid);
			docSettings.replaceItemValue("callTreeType", callTreeType);
			docSettings.save();
			docSettings.recycle();
		} catch (NotesException e) {
			Logger.error(e);
		}
	
	}
	public void setCallTreeRoleBased() {
	this.callTreeType = CALLTREE_TYPE_ROLE;
		
		try {
			Document docSettings = ExtLibUtil.getCurrentDatabase().getDocumentByUNID(settingsUnid);
			docSettings.replaceItemValue("callTreeType", callTreeType);
			docSettings.save();
			docSettings.recycle();
		} catch (NotesException e) {
			Logger.error(e);
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
