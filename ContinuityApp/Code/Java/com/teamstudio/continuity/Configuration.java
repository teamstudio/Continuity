package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Vector;

import com.ibm.commons.util.StringUtil;
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
	private String continuityDbUrl;
	private String senderEmail;
	private String senderName;

	private String callTreeType;

	private static String APP_VERSION = "v1.5.2"; //current application version
	private static String DATA_VERSION = "145"; //data version (used for checking if a conversion is needed)

	private String serverName;

	private HashMap<String, String> labels = new HashMap<String, String>();

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

				if (!dataVersion.equals(DATA_VERSION)) {

					convert(dataVersion, DATA_VERSION, dbCurrent, vwAllByType, docSettings);

				}

				serverName = dbCurrent.getServer();
				continuityDbPath = dbCurrent.getFilePath();
				continuityDbUrl = "/" + continuityDbPath.replace("\\", "/");

				settingsUnid = docSettings.getUniversalID();
				organisationName = docSettings.getItemValueString("organisationName");
				organisationId = docSettings.getItemValueString("organisationId");
				directoryDbPath = docSettings.getItemValueString("directoryDbPath");
				unpluggedDbPath = docSettings.getItemValueString("unpluggedDbPath");

				callTreeType = docSettings.getItemValueString("callTreeType");

				if (callTreeType.length() == 0) {
					callTreeType = CALLTREE_TYPE_ROLE; //default: role based
				}

				senderEmail = docSettings.getItemValueString("senderEmail");
				senderName = docSettings.getItemValueString("senderName");

				//defaults
				if (senderEmail.length() == 0) {
					senderEmail = "no-reply@continuity.com";
				}
				if (senderName.length() == 0) {
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

				labels.put("miniConfigGuide", getMiniConfigGuide());
				labels.put("contactsImportGuide", getContactsImportGuide());

			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			Utils.recycle(docSettings, vwAllByType, dbCurrent);

		}

	}

	@SuppressWarnings("unchecked")
	private void convert(String dataVersion, String DATA_VERSION, Database dbCurrent, View vwAllByType, Document docSettings) {

		try {
			DebugToolbar.get().info("Start conversion (current data version: " + dataVersion + ", update to " + DATA_VERSION + ")");

			DebugToolbar.get().info("- convert role > appMenuOptions");

			DocumentCollection dc;
			dc = dbCurrent.search("form=\"fRole\" & @IsUnavailable(appMenuOptions)");
			if (dc.getCount() > 0) {
				dc.stampAll("appMenuOptions", "all");
			}

			DebugToolbar.get().info("- convert contacts (app menu options, org units, org unit in call tree)");

			dc = dbCurrent.search("form=\"fContact\"");
			if (dc.getCount() > 0) {

				boolean changed = false;

				Document doc = dc.getFirstDocument();
				while (null != doc) {

					changed = false;

					//remove app menu options field
					if (doc.hasItem("appMenuOptions")) {
						doc.removeItem("appMenuOptions");
						changed = true;
					}
					if (doc.hasItem("appMenuOptionsActive")) {
						doc.removeItem("appMenuOptionsActive");
						changed = true;
					}

					String callTreeRoot = doc.getItemValueString("callTreeRoot");
					Vector<String> callTreeCalledBy = doc.getItemValue("callTreeCalledBy");
					Vector<String> callTreeContacts = doc.getItemValue("callTreeContacts");

					String orgUnitId = null;

					if (doc.hasItem("orgUnitId")) {
						orgUnitId = doc.getItemValueString("orgUnitId");
					} else {
						Vector<String> o = doc.getItemValue("orgUnitIds");
						if (o.size() > 0) {
							orgUnitId = o.get(0);
						}
					}

					boolean callTreeChanged = false;

					if (StringUtil.isNotEmpty(orgUnitId)) {

						if (callTreeRoot.length() > 0 && callTreeRoot.indexOf("-") == -1) {
							doc.replaceItemValue("callTreeRoot", orgUnitId + "-" + callTreeRoot);
							callTreeChanged = true;
						}

						for (int i = 0; i < callTreeCalledBy.size(); i++) {
							String _this = callTreeCalledBy.get(i);
							if (_this.length() > 0 && _this.indexOf("-") == -1) {
								callTreeCalledBy.set(i, orgUnitId + "-" + _this);
								callTreeChanged = true;
							}
						}

						for (int i = 0; i < callTreeContacts.size(); i++) {
							String _this = callTreeContacts.get(i);
							if (_this.length() > 0 && _this.indexOf("-") == -1) {
								callTreeContacts.set(i, orgUnitId + "-" + _this);
								callTreeChanged = true;
							}
						}

						if (callTreeChanged) {
							changed = true;
							doc.replaceItemValue("callTreeCalledBy", callTreeCalledBy);
							doc.replaceItemValue("callTreeContacts", callTreeContacts);
						}

					}

					//convert org unit to multi-value name
					if (doc.hasItem("orgUnitId")) {
						doc.replaceItemValue("orgUnitIds", doc.getItemValueString("orgUnitId"));
						doc.removeItem("orgUnitId");
						changed = true;
					}
					if (doc.hasItem("orgUnitName")) {
						doc.replaceItemValue("orgUnitNames", doc.getItemValueString("orgUnitName"));
						doc.removeItem("orgUnitName");
						changed = true;
					}

					if (changed) {
						doc.save();
					}

					Document tmp = dc.getNextDocument(doc);
					doc.recycle();
					doc = tmp;
				}

				dc.stampAll("appMenuOptions", "all");
			}

			DebugToolbar.get().info("Conversion finished - update data version");

			//update version in settings document
			docSettings = vwAllByType.getDocumentByKey("fSettings", true); //re-retrieve here using signer access
			docSettings.replaceItemValue("dataVersion", DATA_VERSION);
			docSettings.save();
		} catch (Exception e) {
			DebugToolbar.get().error(e);
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

	private String getContactsImportGuide() {

		StringBuilder sb = new StringBuilder();

		sb.append("<div style=\"margin-top: 15px\">");

		sb.append("<div><div>Continuity allows you to upload batch files to create contacts. Three file formats are supported: ");
		sb.append("<strong>CSV</strong>, <strong>LDIF</strong> and <strong>XML</strong>.</div>");

		sb.append("<div>");

		sb.append("<div style=\"margin: 10px 0; font-weight: bold\">CSV</div>");
		sb
				.append("<p>A CSV files contains rows for every contact, the first (non-comment) row should be the (required) header row specifying the attribute names of the contact rows. ");
		sb
				.append("<span>Microsoft's Active Directory comes with a program called &quot;csvde.exe&quot;. This allows you to create CSV files from (a subset of) Active Directory users. "
						+ "Continuity requires a batch-CSV file to be formatted using that format.</span></p>");
		sb.append("<p><a href=\"" + continuityDbUrl
				+ "/Continuity%20-%20Data%20import%20sample.csv\" target=\"_blank\">Download sample CSV file</a></p>");

		sb.append("<div style=\"margin: 10px 0; font-weight: bold\">LDIF</div>");
		sb
				.append("<p>An LDIF file contains 'block' of attribute names/ -values for every contact. A 'block' ends if an empty line is encountered. "
						+ "Every row in the block should have the syntax:</p>");
		sb.append("<p>&lt;attribute name&gt;: &lt;attribute value&gt;</p>");
		sb
				.append("<p><span>Microsoft's Active Directory comes with a program called <strong>LDIFDE </strong>to create batch files that can be read by Continuity.</span></p>");
		sb.append("<p><a href=\"" + continuityDbUrl
				+ "/Continuity%20-%20Data%20import%20sample.ldif\" target=\"_blank\">Download sample LDIF file</a></p>");

		sb.append("<div style=\"margin: 10px 0; font-weight: bold\">XML</div>");
		sb.append("<p>An XML file being uploaded should have the following structure:</p>");
		sb
				.append("<p>&lt;Employees&gt;<br clear=\"none\">  &lt;Employee id=&quot;unique identifier for this user&quot;&gt;<br clear=\"none\">"
						+ "   &lt;GivenName&gt;Mary&lt;/GivenName&gt;<br clear=\"none\">  &lt;/Employee&gt;<br clear=\"none\">&lt;/Employees&gt;</p>");
		sb.append("<p>The required/ optional attributes are the same as with the other 2 file formats.</p>");
		sb.append("<p><a href=\"" + continuityDbUrl
				+ "/Continuity%20-%20Data%20import%20sample.xml\" target=\"_blank\">Download sample XML file</a></p>");

		sb.append("<div style=\"margin: 10px 0; font-weight: bold\">Attributes: required and optional</div>");

		sb.append("<p>All three file formats support the same list of attributes:</p>");

		sb
				.append("<ul><li><strong>objectClass</strong> (required value for CSV/ LDIF imports: user or person, not used when reading XML files)</li>");
		sb
				.append("<li><strong>userPrincipalName</strong>, objectGUID or id (one of these required, contains a unique identifier for the user)</li>");
		sb.append("<li><strong>givenName</strong> (required, first name)</li>");
		sb.append("<li><strong>sn</strong> (required, last name)</li>");
		sb.append("<li><strong>mail</strong> (required, email address)</li>");
		sb.append("<li><strong>title</strong> (optional, job title)</li>");
		sb.append("<li><strong>telephonenumber</strong> (optional, work phone number)</li>");
		sb.append("</ul>");
		sb.append("All other attributes in the files are skipped.");

		sb.append("</div></div>");

		sb.append("</div>");

		return sb.toString();

	}

	private String getMiniConfigGuide() {
		StringBuilder sb = new StringBuilder();

		sb.append("<div class=\"section\"><div class=\"title\">Teamstudio Continuity Mini Configuration Guide " + APP_VERSION
				+ "</div></div>");

		sb.append("<p>We recommend that Continuity should be configured in an 8 step process, in the sequence described below.</p>");

		sb.append("<ol>");
		sb.append("<li>Define your Terms</li>");
		sb.append("<li>Define the your key entities at risk</li>");
		sb.append("<li>Define your Organization structure i.e. Org Units and Contacts</li>");
		sb.append("<li>Define your list of Business Scenarios</li>");
		sb.append("<li>Define your list of Plan Types (optional)</li>");
		sb.append("<li>Define Roles</li>");
		sb.append("<li>Create the relevant Task sequences for each Scenario</li>");
		sb.append("<li>Define Responsibilities (optional)</li>");
		sb.append("</ol>");

		sb
				.append("<p style=\"margin: 20px 0 5px 0;\">For most configurations there will be only 6 steps - steps 5 and 8 are optional and are only required in more complex set-ups.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 1. Define your Terms</p>");
		sb
				.append("<p>Different organizations use different words to describe the entities that may be at risk e.g. Assets, Activities, Sites or Locations.  To configure the appropriate terms for your organization, go to the Terms screen (just under the Mini Config Guide) and select the terms that fit your organization best.  You also define your Task categories in this screen.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 2: Define your key entities at risk</p>");
		sb
				.append("<p>It is very important to understand which are the key entities at risk in your organization, where a potential incident or crisis will have the greatest impact. You do this outside the Continuity system, as part of your Risk Assessment and Business Impact Analysis exercises.  Once you have identified these key Assets, Activities, Sites or Locations, go to the option listed in the Organization and Contacts section in the Web console (the section labelled Assets, Activities, Sites or Locations - depending on how you configured it in Step 1)  and define your entities.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 3. Define your Organization structure</p>");
		sb
				.append("<p>The Continuity system requires that at least one Organization Unit (Org Unit for short) is present for the system to work. Org Units are equivalent to Departments or Divisions within an organization. An organization may have multiple Org Units. Each individual user (Contact) in the Continuity system needs to be assigned to at least one Org Unit in their Contact profile. You can either create users (Contacts) in the Continuity system one by one or import them in bulk. Instructions to do so are provided within the Contacts section. This is also a good time to define some standard user Roles in your Organization, from within the Other Entities section of the web console. If you have multiple Org Units, you should link your Roles to the appropriate Org Units.  You can also link your Roles to specific entities that are at risk.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 4.  Define your list of Business Scenarios</p>");
		sb
				.append("<p>For each Org Unit there will be a set of specific Business Scenarios e.g Loss of Workplace and Loss of Communications. We provide an example list of Scenarios, but you may wish to customize these to your own particular situation. Go to the Business Scenarios screen in the Other Entities section of the web console to do this.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 5. Define your Plan Types (optional)</p>");
		sb
				.append("<p>Plan Types provide a way of grouping your recovery Task sequences (see Step 6 below) to align with your different types of business continuity plan (e.g. Business Continuity Plan, Crisis Communications Plan, IT Disaster Recovery Plan).  Many organizations just have one type of plan - a Business Continuity plan - and will not need to enter anything into this section.   If you do have multiple Plan Types, you can give them each a description when you set them up, and you can add a File attachment to each Plan Type to provide more guidance on how the different Plan Types should be used.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 6. Define Roles</p>");
		sb
				.append("<p>Now that you have created your Org Structure and Business Scenarios, you need to define some standard Roles for the users in your system.  This is done in the Roles screen and some common roles are provided for you as a starting point.   Once you’ve defined some Roles, this is a good time to go and add some Contacts (users) into the system.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 7. Create the relevant Task sequences for each Scenario</p>");
		sb
				.append("<p>Now that you have created your list of Business Scenarios, click on the Tasks menu in the Other Entities section of the web console. Tasks are individual actions that need to be carried out as part of the business recovery during an Incident or Crisis. For each Scenario, set the sequence of Tasks that would need to be carried out to deal with that particular Scenario and categorize these between Initial Tasks, Ongoing Tasks, and Deactivation Tasks (i.e. Tasks that need to be completed before you can deactivate that particular business continuity plan).  If you have different Plan Types that you have previously set up in the system, then when you create each new Task you should link it to the appropriate Plan Type, otherwise leave this part of the form blank. </p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Step 8. Define your Responsibilities (optional)</p>");
		sb
				.append("<p>Optionally you can define areas of Responsibiliity, which fit in below Roles but above individual Tasks. The following explanation may be helpful in clarifying how this works. A Task is an activity to be done that relates to a particular BCM Scenario. For example in the event of a Loss of Workplace scenario one key Task that will need to be done is to arrange a temporary workplace. At this  point, a Task may be defined without being assigned to anyone - it is just a Task to be carried out be someone in an emergency.  A Responsibility is a bit different. It’s an obligation on a person carrying a specific Role to carry out one or more Tasks in response to particular future events or circumstances.  If the Responsibility is just for a single Task in response to a future event, then the name of the Responsibility can be identical to the name of the Task. (this is where the confusion between these two objects often arises). If the Responsibility is for multiple Tasks in response to that future event, then the Responsibility is created for a grouping of individual Tasks, and named appropriately. For example, in the event of a burst pipe inside a building, four Tasks might be created and named as follows:</p>");
		sb.append("<ul>");
		sb.append("<li>\"Locate mains water supply in office B107\"</li>");
		sb.append("<li>\"Rotate and turn off main water supply tap in closet B5\"</li>");
		sb.append("<li>\"Inform all staff in building that water supply has been turned off\"</li>");
		sb.append("<li>\"Call plumbing company requesting emergency call out\"</li>");
		sb.append("</ul>");
		sb
				.append("<p>These might be grouped under a single Responsibility called: \"Turn off mains utility services in an emergency\" which is assigned to the Role \"Building Manager\". Setting up individual Responsibilities is only necessary when you have a very large number of detailed Tasks in your recovery Task sequences, and you create a clearer organization of these Tasks by grouping them.  Most system configurations will not need to set up Responsibiilties.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Keeping it simple to start with</p>");
		sb
				.append("<p>When configuring your system, make sure you have the configuration done correctly before entering all your data.  Try out the working of the system with just one or two Org Units, a small number of Scenarios and Roles, and no more than 20 or 30 Tasks.  Add your user Contacts and then check that everything is working as you would expect, in both the web and the mobile user interfaces.</p>");

		sb.append("<p style=\"margin: 20px 0 5px 0; font-weight: bold\">Syncing to mobile devices</p>");
		sb
				.append("<p>Once you are happy with the preparation of your system using the web console user interface, you can test it out using the mobile app. The Unplugged mobile app needs to be downloaded from iTunes, or from Google's Play store. Once downloaded, you then need to enter the server URL, a username and a password. Once this is done, and you then sync to the server, the Continuity app will be downloaded to your device ready to use.</p>");
		sb.append("<p>For Apple devices go here to download Teamstudio Unplugged:<br>");
		sb
				.append("<a href=\"https://itunes.apple.com/us/app/unplugged/id498245114?mt=8\" target=\"_blank\">https://itunes.apple.com/us/app/unplugged/id498245114?mt=8</a></p>");
		sb.append("<p>For Android devices go here:<br>");
		sb
				.append("<a href=\"https://play.google.com/store/apps/details?id=com.teamstudio.unplugged.platform.android&hl=en\" target=\"_blank\">https://play.google.com/store/apps/details?id=com.teamstudio.unplugged.platform.android&hl=en</a></p>");

		sb
				.append("<p>You should then try out the mobile app for a small test group of users (between 3 and 10) corresponding to different Roles. These new users of Continuity can be created in the Contacts section with a Standard or Sys Admin account. Any user created there needs to set a password. They can do so by going to the Continuity login screen, clicking the 'Account activation' link at the bottom of the screen and entering their email address. An email will be send to them with instructions and a link to set a password. Once that is done, they can use that password, their username (email address) and server name to configure the Continuity mobile app (Unplugged).</p>");
		sb
				.append("<p>When you and your key users are satisfied with how the mobile app is working you can send out instructions to all your users using the same procedure.</p>");

		return sb.toString();

	}

}
