package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.HashMap;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.Document;
import lotus.domino.ViewNavigator;

public class Configuration implements Serializable {

	private static final long serialVersionUID = -7047733321266533775L;
	
	private static final String BEAN_NAME = "configBean";
	
	private String settingsUnid;
	private String organisationName;
	private String organisationId;
	private String directoryDbPath;
	private String unpluggedDbPath;
	private String continuityDbPath;
	
	private static String DATA_VERSION = "100";
	
	private String serverName;
	
	private int numAssets;
	private int numScenarios;
	private int numTasks;
	private int numPlans;
	private int numResponsibilities;
	private int numQuickGuides;
	
	private HashMap<String,String> labels = new HashMap<String,String>();
	
	public Configuration() {
		reload();
	}
	
	public String getAppVersion() {
		return "v1.11";		//current application version
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
				
				updateMenuOptionCounts();
				
			}
			
			//Logger.debug("checking access level...");
			
			//Logger.debug("other db using full access: : " + ExtLibUtil.getCurrentSessionAsSignerWithFullAccess().getDatabase("", "uhs\\config.nsf").getCurrentAccessLevel());
			
			//Logger.debug( "current db (using full access): " + ExtLibUtil.getCurrentSessionAsSignerWithFullAccess().getCurrentDatabase().getCurrentAccessLevel() );
			
		//	Logger.debug( "other db: " + ExtLibUtil.getCurrentSessionAsSigner().getDatabase("", "uhs\\config.nsf").getCurrentAccessLevel() );
			
			//Logger.debug( "current db: " + ExtLibUtil.getCurrentSessionAsSigner().getCurrentDatabase().getCurrentAccessLevel() );

			 
			 
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.recycle(docSettings, vwAllByType, dbCurrent);
			
		}
	
	}
	
	public void updateMenuOptionCounts() {
		
		View vwTarget = null;
		
		try {
			
			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			vwTarget = dbCurrent.getView("vwAllByType");
			
			ViewNavigator nav;
			
			nav = vwTarget.createViewNavFromCategory("fSite");
			numAssets = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fScenario");
			numScenarios = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fTask");
			numTasks = nav.getCount();
			Utils.recycle(nav);

			nav = vwTarget.createViewNavFromCategory("fPlan");
			numPlans = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fResponsibility");
			numResponsibilities = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fQuickGuide");
			numQuickGuides = nav.getCount();
			Utils.recycle(nav);
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(vwTarget);
		}
		
	}
	
	public int getNumAssets() {
		return numAssets;
	}
	public int getNumScenarios() {
		return numScenarios;
	}
	public int getNumTasks() {
		return numTasks;
	}
	public int getNumPlans() {
		return numPlans;
	}
	public int getNumResponsibilities() {
		return numResponsibilities;
	}
	public int getNumQuickGuides() {
		return numQuickGuides;
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
		
}
