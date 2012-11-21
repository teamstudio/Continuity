package com.teamstudio.continuity;

import java.io.Serializable;

import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.Document;

public class Configuration implements Serializable {

	private static final long serialVersionUID = -7047733321266533775L;
	
	private String settingsUnid;
	private String organisationName;
	
	public static final String ROLE_BCEDITOR = "[bcEditor]";
	
	public Configuration() {
	
		reload();
		
	}
	
	public void reload() {
	
		Database dbCurrent = null;
		View vwSettings = null;
		Document docSettings = null;

		try {
			
			dbCurrent = (Database) Utils.resolveVariable("database");
			vwSettings = dbCurrent.getView("vwSettings");
			docSettings = vwSettings.getDocumentByKey("fSettings", true);
			
			if (docSettings == null) {
				
				//create new settings document
				System.out.println("creating settings document...");
				docSettings = dbCurrent.createDocument();
				docSettings.replaceItemValue("Form", "fSettings");
				docSettings.replaceItemValue("docAuthors", ROLE_BCEDITOR).setAuthors(true);
				
				docSettings.save();
			
			}
			
			settingsUnid = docSettings.getUniversalID();
			
			organisationName = docSettings.getItemValueString("organisationName");
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.recycle(docSettings, vwSettings, dbCurrent);
			
		}
	
	}
	
	public String getSettingsUnid() {
		return settingsUnid;
	}
	
	public String getOrganisationName() {
		return organisationName;
	}
		
}
