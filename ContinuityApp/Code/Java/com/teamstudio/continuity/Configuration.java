package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.HashMap;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import eu.linqed.debugtoolbar.DebugToolbar;

import lotus.domino.Database;
import lotus.domino.DocumentCollection;
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
	
	private static String APP_VERSION = "v1.4.1";		//current application version
	private static String DATA_VERSION = "105";			//data version (used for checking if a conversion is needed)
	
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
					
					DebugToolbar.get().info("start conversion: role > appMenuOptions");
					DocumentCollection dc;
					dc = dbCurrent.search("form=\"fRole\" & @IsUnavailable(appMenuOptions)");
					if (dc.getCount()>0) {
						dc.stampAll("appMenuOptions", "all");
					}
					
					dc = dbCurrent.search("form=\"fContact\" & @IsUnavailable(appMenuOptions)");
					if (dc.getCount()>0) {
						dc.stampAll("appMenuOptions", "all");
					}
					
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
				
				labels.put("miniConfigGuide", getMiniConfigGuide() );
				
				
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
	
	private String getMiniConfigGuide() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("<div class=\"section\"><div class=\"title\">Teamstudio Continuity Mini Configuration Guide v1.41</div></div>");

		sb.append("<p>We recommend that Continuity should be configured in a 6 step process, in the sequence described below.</p>");

		sb.append("<ol><li>Define the key Assets or Activities at risk</li>");
		sb.append("<li>Define your Organization structure i.e. org units and contacts</li>");
		sb.append("<li>Define your list of Business Scenarios</li>");
		sb.append("<li>Define your list of Plan checklists</li>");
		sb.append("<li>Create the relevant Task sequences under each Scenario</li>");
		sb.append("<li>Define Responsibilities</li></ol>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 1. Define the key Assets or Activities at risk</p>");

		sb.append("It is very important to understand which are the key Assets or Activities at risk in ");
		sb.append("your organization, where a potential incident or crisis will have the greatest ");
		sb.append("impact. You do this outside the Continuity system, as part of your Risk Assessment ");
		sb.append("and Business Impact Analysis exercises.  Once you have identified these key Assets ");
		sb.append("or Activities, they should be entered into the Assets/Activities screen in the ");
		sb.append("Organization & Contacts section in the web console. (Note – you can set your system ");
		sb.append("to use the term Assets or the alternative term Activities in the Terms screen in the Configuration section).</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 2. Define your Organization structure</p>");

		sb.append("<p>The Continuity system requires that at least one Organization Unit (Org Unit for ");
		sb.append("short) is present for the system to work. Org Units are equivalent to Departments or ");
		sb.append("Divisions within an organization. An organization may have multiple Org Units. Each ");
		sb.append("individual user (Contact) in the Continuity system needs to be assigned to an Org Unit ");
		sb.append("in their Contact profile. You can either create users (Contacts) in the Continuity ");
		sb.append("system one by one or import them in bulk. Instructions to do so are provided within ");
		sb.append("the Contacts section. This is also a good time to define some standard user Roles in ");
		sb.append("your Organization, from within the Other Entities section of the web console.</p> ");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 3.  Define your list of Business Scenarios</p>");

		sb.append("<p>For each Org Unit there will be a set of specific business Scenarios e.g Loss of ");
		sb.append("Workplace and Loss of Communications. We provide an example list of Scenarios, ");
		sb.append("but you may wish to customize these to your own particular situation. Go to the ");
		sb.append("Business Scenarios screen in the Other Entities section of the web console to do this.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 4. Define your Plan checklists </p>");

		sb.append("<p>Plan checklists provide a way of grouping your recovery Tasks (see Step 5 below) to ");
		sb.append("align with your business continuity plan documents (e.g. Business Continuity Plan, ");
		sb.append("Incident Management Plan, Crisis Communications Plan). File attachments can be ");
		sb.append("added to each Checklist to provide more guidance to help with recovering from an ");
		sb.append("Incident or Crisis. It is easier if you begin the configuration process with just a single ");
		sb.append("Checklist and then add more Checklists later.  This will keep the starting ");
		sb.append("configuration simple, which is important when you are learning how the system works. </p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 5. Create the relevant Task sequences under each Scenario</p>");

		sb.append("<p>Now that you have created your list of Business Scenarios, click on the Tasks menu in ");
		sb.append("the Other Entities section of the web console. Tasks are individual actions that need ");
		sb.append("to be carried out as part of the business recovery during an incident or crisis. For each ");
		sb.append("Scenario, set the sequence of Tasks that would need to be carried out to deal with that ");
		sb.append("particular Scenario and categorize these between Initial Tasks, Ongoing Tasks, and ");
		sb.append("Deactivation Tasks (i.e. Tasks that need to be completed before you can deactivate ");
		sb.append("that particular business continuity plan). When you create each Task, you can link it to one or more Plan checklists.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 6. Define Responsibilities </p>");

		sb.append("<p>Now that you have created some lists of Tasks for each business Scenario, you need ");
		sb.append("to allocate responsibilities to the people who will have to carry these Tasks out in the ");
		sb.append("event of a business emergency. This is done through the Responsibility screen in the ");
		sb.append("Other Entities section of the web console. First, run through the Tasks and allocate ");
		sb.append("them to whichever of your Roles (created in Step 2) seems the most logical to carry ");
		sb.append("out that Task.  Then, when you have got a good selection of individual Tasks under ");
		sb.append("each Role, create a set of higher-level Responsibilities for each of your Roles ");
		sb.append("(typically 2 to 6 per Role).  Finally, link each of your Tasks to each of these ");
		sb.append("Responsibilities.  Review to check if you have any Tasks that are unallocated to Roles or Responsibilities. </p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Syncing to mobile devices</p>");

		sb.append("<p>Once you are happy with the preparation of your system using the web console user ");
		sb.append("interface, you can test it out using the mobile app. The Unplugged mobile app needs ");
		sb.append("to be downloaded from iTunes, or from Google’s Play store. Once downloaded, you ");
		sb.append("then need to enter the server URL, a username and a password. Once this is done, ");
		sb.append("and you then sync to the server, the Continuity app will be downloaded to your device ready to use.</p>");

		sb.append("<p>For Apple devices go here to download Teamstudio Unplugged:<br />");
		sb.append("<a href=\"https://itunes.apple.com/us/app/unplugged/id498245114?mt=8\" target=\"_blank\">https://itunes.apple.com/us/app/unplugged/id498245114?mt=8</a></p>");
		sb.append("<p>For Android devices go here:<br />");
		sb.append("<a href=\"https://play.google.com/store/apps/details?id=com.teamstudio.unplugged.platform.android&hl=en\" target=\"_blank\">https://play.google.com/store/apps/details?id=com.teamstudio.unplugged.platform.android&hl=en</a></p>");

		sb.append("<p>You should then try out the mobile app for a small test group of users (between 3 and 10) corresponding to different Roles. ");
		sb.append("These new users of Continuity can be created in the Contacts section with a Standard or Sys Admin account. ");
		sb.append("Any user created there needs to set a password. ");
		sb.append("They can do so by going to the Continuity login screen, clicking the 'Account activation' link at the bottom of the screen and entering their email address. ");
		sb.append("An email will be send to them with instructions and a link to set a password. Once that is done, they can use that password, ");
		sb.append("their username (email address) and server name to configure the Continuity mobile app (Unplugged).</p>");
		
		sb.append("<p>When you and your key users are satisfied with how the mobile app is working you can send out instructions to all your users using the same procedure.</p>");
		
		return sb.toString();
		
	}
		
}
