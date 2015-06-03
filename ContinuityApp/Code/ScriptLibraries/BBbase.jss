function configApp() {
	//For BB Public Demo
	//var userName = "CN=ted.barnhouse/O=ZetaComm";
	var userName = session.getEffectiveUserName();
	print("USER: " + userName);
	
	if ( !sessionScope.configLoaded) {
		try{
			
			//Setup User info
			var userView = database.getView("BBUserInfo");
			var userEntry = userView.getAllEntriesByKey(userName);
			var userRow = userEntry.getFirstEntry();
			var userDoc = userRow.getDocument();
			applicationScope.put("thisUNID", userDoc.getUniversalID());
			
			var firstName = userDoc.getItemValueString("firstName");
			var lastName = userDoc.getItemValueString("lastName");
			applicationScope.put("firstName", firstName);
			applicationScope.put("lastName", lastName);
			sessionScope.put("name", firstName + " " + lastName);
			sessionScope.put("userName", userName);
			sessionScope.put("email", userDoc.getItemValueString("email"));
			var userType = userDoc.getItemValueString("userType");
			var isEditor = userType.equals("editor");
			sessionScope.put("isEditor", isEditor);
			sessionScope.put("roleName", userDoc.getItemValueString("roleName"));			
			sessionScope.put("roleId", userDoc.getItemValueString("roleId"));
		
			sessionScope.put("userOrgUnitNames", userDoc.getItemValueString("orgUnitNames"));			
			var orgUnitIds = userDoc.getItemValue("orgUnitIds");
			sessionScope.put("userOrgUnitIds", orgUnitIds);	
			
			sessionScope.put("currentOrgUnitId", (orgUnitIds.length>0 ? orgUnitIds[0] : "" ));
			sessionScope.put("currentOrgUnitName", (sessionScope.get("userOrgUnitNames").length>0 ? sessionScope.get("userOrgUnitNames")[0] : "" ));
			
			
			//Set up settings
			var vwAllByType:NotesView = database.getView("BBvwAllByType");
			var docSettings:NotesDocument = vwAllByType.getDocumentByKey("fSettings", true);
			
			if (docSettings == null ) {
				
				print("ERROR: settings document not found");
				
			} else {
				
				sessionScope.put("isReadOnlyMode", docSettings.getItemValueString("readOnlyMode").equals("yes"));
								
				//labels
				var risksLabel = "Assets";
				var riskLabel = "Asset";
				if (docSettings.getItemValueString("riskNaming").equals("activities")) {
					risksLabel = "Activities";
					riskLabel = "Activity";
				} else if(docSettings.getItemValueString("riskNaming").equals("sites")) {
					risksLabel = "Sites";
					riskLabel = "Site";
				} else if(docSettings.getItemValueString("riskNaming").equals("locations")) {
					risksLabel = "Locations";
					riskLabel = "Location";
				}
				
				var incidentsLabel = "Incidents";
				var incidentLabel = "Incident";
				if (docSettings.getItemValueString("incidentNaming").equals("crises")) {
					incidentsLabel = "Crises";
					incidentLabel = "Crisis";
				} else if (docSettings.getItemValueString("incidentNaming").equals("emergencies")) {
					incidentsLabel = "Emergencies";
					incidentLabel = "Emergency";
				} 
				
				sessionScope.put("incidentLabel", incidentLabel);
				sessionScope.put("incidentsLabel", incidentsLabel);
				sessionScope.put("riskLabel", riskLabel);
				sessionScope.put("risksLabel", risksLabel);
	
			}
			
			//Set Call Tree flag if user has role that can see all OUs
			//var vwAllById:NotesView = database.getView("vwAllById");
			//var docRole:NotesDocument = vwAllById.getDocumentByKey(sessionScope.get("roleId"), true);
			
			//sessionScope.put("globalUser", docRole.getItemValue("canAccessAllOrgUnits"));
			
			//applicationScope.put("status", "High Alert");
			
			//get org unit alert level
			/*if (orgUnitId.length>0) {
			
				var vwOrgUnits = database.getView("BBvwOrgUnitsById");
				var docOrgUnit = vwOrgUnits.getDocumentByKey( orgUnitId, true);
			
				if (docOrgUnit != null) {
					sessionScope.put("currentOrgUnitAlertLevel", docOrgUnit.getItemValueString("alertLevel") );
				}
			}*/
			//Build views for use later (performace)
			/*var vwBBLiveIncidents = database.getView("BBvwLiveIncidents");
			var incEntry = vwBBLiveIncidents.getFirstDocument();
			var vwBBContacts = database.getView("BBContactsByLastname");
			var contactEntry = vwBBContacts.getFirstDocument();
			var vwBBTaskInc = database.getView("BBvwTasksByIncident");
			var taskDoc = vwBBTaskInc.getFirstDocument();
			var vwBBUpdates = database.getView("vwUpdates");
			var updateDoc = vwBBUpdates.getFirstDocument();	
			var vwBBIncByCat = database.getView("BBvwIncidentsByCat");
			var IncCatDoc = vwBBIncByCat.getFirstDocument();	
			var vwScenarios = database.getView("BBvwScenarios");
			var scenarioDoc = vwScenarios.getFirstDocument();	
			var vwTasks = database.getView("BBvwTasksByParentByTaskCat");
			var taskDoc2 = vwTasks.getFirstDocument();	
			
			var vwTypes = database.getView("BBvwAllByType");
			var typeDoc = vwTypes.getFirstDocument();	
			*/
			
			sessionScope.put("configLoaded", true);	
			sessionScope.put("userError", "noerror");
			
		}
		catch (e){
			print("USER DEFINED ERROR: " + e);
			sessionScope.put("userError", "error");
		}	
	} 
}

//creates a new update item with the specified message
function createUpdate( text, incidentId, incidentName) {

	//print("CREATING UPDATE");
	
	if (sessionScope.isReadOnlyMode) {
		return false;
	}
	
	var docUpdate = null;
		
	try {
		docUpdate = database.createDocument();
		docUpdate.replaceItemValue("form", "fUpdate");
		docUpdate.replaceItemValue("createdBy", sessionScope.get("userName") );
		docUpdate.replaceItemValue("createdByName", sessionScope.get("name") );
		
		docUpdate.replaceItemValue("messageHTML", text );
		
		//BB cannot render HTML tags so plain text version must be used
		var textPlain = @ReplaceSubstring(text, ["<b>", "</b>"], "" );
		docUpdate.replaceItemValue("message", @ReplaceSubstring( textPlain , ["<br>", "<br />"], "" ) );
		
		docUpdate.replaceItemValue("incidentId", incidentId);
		docUpdate.replaceItemValue("incidentName", incidentName);
		
		var createdInMs = (new Date()).getTime().toString();
	
		docUpdate.replaceItemValue("createdDateMs", createdInMs );
		
		docUpdate.replaceItemValue("docAuthors", "[bcEditor]");	//set as authors field not supported in Unplugged: done in TMSRunAfterPush agent
				
		docUpdate.save();
		
	} catch (e) {
		print("ERROR: CREATE UPDATE");
	} 
}

//create a new mail
function sendMail( to, subject, body, fromEmail, fromName ) {
	
	if (sessionScope.isReadOnlyMode) {
		return false;
	}

	var docMemo = null;
	
	try {
		
		docMemo = database.createDocument();
		docMemo.replaceItemValue("form", "Memo");
		docMemo.replaceItemValue("SendTo", @Explode(to) );
		docMemo.replaceItemValue("Subject", subject );
	
		docMemo.replaceItemValue("From", fromName + "<" + fromEmail + "@NotesDomain>");
		docMemo.replaceItemValue("SMTPOriginator", fromEmail);
		docMemo.replaceItemValue("Sender", fromEmail);
		docMemo.replaceItemValue("Principal", fromEmail + "@NotesDomain" );
		docMemo.replaceItemValue("INETFROM", fromName + "<" + fromEmail + ">");
	
		docMemo.replaceItemValue("Body", body );
		
		docMemo.send();
		//print("MAIL SENT");
		
	} catch (e) {
		print("ERROR SENDING MAIL: " + e);
	}
	
}

function setCallTreeLevels(){
	var DBLResult = @DbColumn("", "BBvwCallTreeLevel", 2);
	var roles = [];
	if (DBLResult != undefined){
		var roles = (DBLResult.constructor == Array) ? DBLResult : [ DBLResult ];
	}
	var DBLResultIds = @DbColumn("", "BBvwCallTreeLevel", 3);
	var roleIds = [];
	if (DBLResultIds != undefined){
		var roleIds = (DBLResultIds.constructor == Array) ? DBLResultIds : [ DBLResultIds ];
	}
	//Get roles for users OrgUnit(s)
	var ous = sessionScope.get("userOrgUnitIds"); 
	for (var i=0; i< ous.length; i++) {
		var orgUnitId = ous[i];
		var vc = database.getView("BBvwContactsOrg").getAllEntriesByKey(orgUnitId, true);
		var ve = vc.getFirstEntry();
		var roleName = "";
		var orgRoles = [];
		while (null != ve) {
			
			roleName = ve.getColumnValues()[1];
			if(orgRoles.indexOf(roleName) == -1){
				orgRoles.push(roleName);
			}
			ve = vc.getNextEntry();
		}
	}
	
	print("No of Roles: " + orgRoles.length);
	
	//Strip roles from ordered list if empty in users orgUnit
	var adjRoles = [];
	var adjRoleIds = [];
	
	for(var i=0; i< roles.length; i++){
		var index = orgRoles.indexOf(roles[i]);
		if(index == -1){
			roles = roles.splice([i],1);
			roleIds = roleIds.splice([i],1);
			i--;
		}
	}
	print("Roles length: " + roles.length);
	
	sessionScope.put("callTreeRoles", roles);
	sessionScope.put("callTreeRoleIds", roleIds);	
	
}


//set default fields that should be available on all documents
function setDefaultFields( doc ) {
	
	//fixed id
	doc.replaceItemValue("id", "x" + doc.getUniversalID().toLowerCase());
	
	//doc creator
	doc.replaceItemValue("createdBy", sessionScope.get("userName"));
	doc.replaceItemValue("createdByName", sessionScope.get("name")) ;
	
	//created date (also in milliseconds - stored as string - used in view lookups
	var now = new Date();
	var createdInMs = (now).getTime().toString();
	doc.replaceItemValue("createdDateMs", createdInMs );

	doc.save();
	
}

function processActivation(doc, isNew){
	//print("PROCESS ACTIVATION");
	
	if(isNew){
		doc.replaceItemValue("unid", doc.getUniversalID().toLowerCase());
		doc.replaceItemValue("status", "active");
		setDefaultFields(doc);
		assignTasksToIncident(doc);
	}
	//Set Authors
	var authors = [];
	authors.push( "[bcEditor]");
	authors.push( sessionScope.get("userName") );
	
	doc.replaceItemValue("docAuthors", authors);		//set to authors type in TMSRunAfterPush agent
	
	processOUAlertLevel(doc);
	
	//Create 'update' item for new incidents
	var isRehearsal = doc.getItemValueString("isRehearsal").equals("yes");
	var incidentDesc = doc.getItemValueString("description");
	
	var updateText = ((isRehearsal ? "Exercise " + sessionScope.get('incidentLabel').toLowerCase() : sessionScope.get('incidentLabel')) + ": <b>" + incidentDesc + "</b><br />" +
		"- alert level: " +	doc.getItemValueString("alertLevel") + "<br />" +
			"- org unit: " + doc.getItemValue("orgUnitName") + "<br />" +
			"- " + sessionScope.get('riskLabel').toLowerCase() + ": " + doc.getItemValue("siteName") + "<br />" +
			"- plan: " + doc.getItemValue("scenarioName"));
		
	createUpdate( updateText, doc.getItemValueString("id"), incidentDesc );
	
	//Send email to all BC users in the system	
	var BCUsersResult = @Unique(@DbColumn( "", "BBUserInfo",3));
	if (BCUsersResult != undefined){
		var BCUsers = (BCUsersResult.constructor == Array) ? BCUsersResult : [ BCUsersResult ];
	}
			
	print("SENDING MAIL");
	//print("BCUSERS: " + @Explode(BCUsers))
	//sendMail( @Explode(BCUsers) , 
	var produced = "updated";			
	if(isNew){
		produced ="created";
	}
	sendMail( "Richard_Sharpe@teamstudio.com", 									
					"[continuity] " + (isRehearsal ? "Exercise " + sessionScope.get('incidentLabel').toLowerCase() + " " + produced : sessionScope.get('incidentLabel') + " " + produced), 
					sessionScope.name + " has just "+ produced +" an incident in Continuity:\r\n\r\n incident: " + doc.getItemValueString("description") + "\r\n\r\n" +
		"- alert level: " +	doc.getItemValueString("alertLevel") + "\r\n" +
			"- org unit: " + doc.getItemValue("orgUnitName") + "\r\n" +
			"- " + sessionScope.get('riskLabel').toLowerCase() + ": " +  doc.getItemValue("siteName") + "\r\n" +
			"- plan: " + doc.getItemValue("scenarioName"),
			sessionScope.email, sessionScope.name);
	
}

function processOUAlertLevel(doc){
	
		//print("PROCESS OU ALERT LEVEL");	
	
		var orgUnitId = doc.getItemValueString("orgUnitId");
		var siteId = doc.getItemValueString("siteId");
		var alertLevel = doc.getItemValueString("alertLevel");
		var scenarioId = doc.getItemValueString("scenarioId");
		var maxAlertLevel = alertLevel;		
				
		if (orgUnitId.length > 0) {
			try{
			var vwById = database.getView("vwAllById");
			var docOU = vwById.getDocumentByKey(orgUnitId, true);
				
			//update alert level in OU document
			if ( alertLevel.equals("high") ) {
				maxAlertLevel = "high";
			} else {
				//Question ML about this line
				maxAlertLevel = getMaxAlertLevel( sessionScope.get("currentOrgUnitId") );
			}
				
			if (orgUnitId == sessionScope.get("currentOrgUnitId") ) {
				sessionScope.put("currentOrgUnitAlertLevel", maxAlertLevel);
			}
			docOU.replaceItemValue("alertLevel", maxAlertLevel);
			docOU.replaceItemValue("activeScenarioId", scenarioId );
			docOU.save();	
			}
			catch(e){
				print("ERROR getting db: " + e);
			}
			
		}
}

//returns the maximum alert level based on all open incidents
//MLs code from iOS version - small changes due to function support
function getMaxAlertLevel( orgUnitId ) {
	
	var highest = "normal";
	
	try {
		
		//get all open incidents for the current OU
		var vwIncidents = database.getView("vwIncidentsOpen");
		var vec = vwIncidents.getAllEntriesByKey( orgUnitId, true);
		
		//determine highest level
		
		var ve = vec.getFirstEntry();
		while (null != ve) {
			
			//options: normal, elevated, hight
			var alertLevel = ve.getColumnValues()[5];
			
			if ( highest.equals("normal") && (alertLevel.equals("elevated") || alertLevel.equals("high") ) ) {
				//dBar.debug("found one with " + alertLevel);
				highest = alertLevel;
			} else if ( highest.equals("elevated") && alertLevel.equals("high") ) {
				//dBar.debug("found one with " + alertLevel);
				highest = alertLevel;
			}
			
			if (highest.equals("high")) {		//stop processing if high is the highest
				//dBar.debug("return high")
				return highest;
			}
			
			ve = vec.getNextEntry();
		}
	
	} catch (e) {
		print("ERROR: GET MAX ALERT LEVEL: " + e);		
	}
	return highest
}

function deactivateIncident(doc){
	
	if (!sessionScope.isReadOnlyMode) {
			
			var isRehearsal = doc.getItemValueString("isRehearsal").equals("yes");
			
			//ML: close any open tasks in this incident
			var vecOpenTasks = database.getView("vwTasksAssignedOpen").getAllEntriesByKey( doc.getItemValueString("id"), true);
			var veTask = vecOpenTasks.getFirstEntry();
			while (null != veTask) {
				docTask = veTask.getDocument();
			
				docTask.replaceItemValue("status", "complete");
				docTask.replaceItemValue("statusChangedBy", sessionScope.get("userName") );
				docTask.replaceItemValue("statusChangedByName", sessionScope.get("name") );
				docTask.save();
				
				veTask = vecOpenTasks.getNextEntry();
			}
			
			doc.replaceItemValue("status", "deactivated");
			doc.save();
			
			try{
				var orgUnitId = doc.getItemValueString("orgUnitId");
				var vwById = database.getView("vwAllById");
				var docOU = vwById.getDocumentByKey(orgUnitId, true);
				
				if (null != docOU) {		
					var maxAlertLevel = getMaxAlertLevel( orgUnitId );
				
					//update alert level in OU document
					docOU.replaceItemValue("alertLevel", maxAlertLevel);
					docOU.replaceItemValue("activeScenarioId", "" );
					docOU.save();
				}
				//ML: update alert level in session scope (if new incident was registered for current OU)
				if (orgUnitId == sessionScope.get("currentOrgUnitId") ) {
					sessionScope.put("currentOrgUnitAlertLevel", maxAlertLevel);
				}
			}
			catch(e){
				print("ERROR IN DEACTIVATE: " + e);
			}

			//ML: update number of assigned tasks
			sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("userOrgUnitIds"), sessionScope.get("roleId")) );
			
			//ML: create update item for this deactivation
			var incidentDesc = doc.getItemValueString("description")				
			
			var updateText = ((isRehearsal ? "Deactivated exercise incident: " : "Deactivated incident: <b>") + incidentDesc + "</b><br />" +
				"- alert level: " +	doc.getItemValueString("alertLevel") + "<br />" +
				"- org unit: " + doc.getItemValue("orgUnitName")  + "<br />" +
				"- asset: " + doc.getItemValueString("siteName") + "<br />" +
				"- plan: " + doc.getItemValueString("scenarioName"));
			
			createUpdate( updateText, doc.getItemValueString("id"), incidentDesc );
			
			//Send email to all BC users in the system
			
			var BCUsersResult = @Unique(@DbColumn( "", "BBvwBCUsers",3));
			if (BCUsersResult != undefined){
				var BCUsers = (BCUsersResult.constructor == Array) ? BCUsersResult : [ BCUsersResult ];
			}
			
			//sendMail( @Explode(BCUsers) ,
					
				sendMail( "Richard_Sharpe@teamstudio.com" ,
						"[BB continuity] " + (isRehearsal ? "Exercise incident deactivated" : "Incident deactivated"), 
						sessionScope.name + " has just deactivated the following incident:\r\n\r\n" +
							"incident: " + doc.getItemValueString("description") + "\r\n\r\n" +
			"- alert level: " +	doc.getItemValueString("alertLevel") + "\r\n" +
				"- org unit: " + doc.getItemValue("orgUnitName") + "\r\n" +
				"- asset: " + doc.getItemValueString("siteName") + "\r\n" +
				"- plan: " + doc.getItemValueString("scenarioName"),
						sessionScope.email, sessionScope.name);
		}
	}		
//get number of open tasks, assigned to the current user (based on bc role)
function getNumAssignedTasks(orgUnitIds, roleId) {
	
	var numAssignedTasks = 0;
	
	try {
	
		var vwTasks = database.getView("vwTasksAssignedByRoleId");
		for (var i=0; i<orgUnitIds.length; i++) {
			var key = orgUnitIds[i] + "-" + roleId;
		
			var vec:NotesViewEntryCollection = vwTasks.getAllEntriesByKey(key, true);
			numAssignedTasks += vec.getCount();
			vec.recycle();
			
		}
		
	} catch (e) {
		print("ERROR ASSIGN # TASKS:" + e);	
	}
	
	return numAssignedTasks;
}

//copy and assign tasks to new incidents
function assignTasksToIncident(docIncident) {

	try {
		
		if (sessionScope.isReadOnlyMode) {		//don't assign tasks in demo mode
			return false;
		}
		
		var vwById = database.getView("vwAllById");	
		var docScenario = vwById.getDocumentByKey(docIncident.getItemValue("scenarioId", true));
		if (null != docScenario) {

			var vwTasksByParent = database.getView("vwTasksByParent");
			var parentId = docScenario.getItemValueString("id");
		
			//retrieve all tasks belonging to the selected scenario
			var vecTasks = vwTasksByParent.getAllEntriesByKey( parentId, true);
			print("found " + vecTasks.getCount() + " tasks for parent id " + parentId);
		
			var veTask = vecTasks.getFirstEntry();
			while (null != veTask) {
			
				var docTask = veTask.getDocument();
			
				//copy all scenario tasks
				var docTaskNew = database.createDocument();
				docTaskNew.replaceItemValue("form", "fTask");
				docTaskNew.replaceItemValue("incidentId", docIncident.getItemValueString("id") );
				docTaskNew.replaceItemValue("incidentName", docIncident.getItemValueString("description") );
			
				docTaskNew.replaceItemValue("isRehearsal", docIncident.getItemValueString("isRehearsal") );
			
				docTaskNew.replaceItemValue("orgUnitId", docIncident.getItemValue("orgUnitId") );
				docTaskNew.replaceItemValue("orgUnitName", docIncident.getItemValue("orgUnitName") );
			
				docTaskNew.replaceItemValue("status", "assigned");
			
				docTaskNew.replaceItemValue("name", docTask.getItemValueString("name") );
				docTaskNew.replaceItemValue("description", docTask.getItemValueString("description") );
				docTaskNew.replaceItemValue("type", docTask.getItemValueString("type") );
			
				docTaskNew.replaceItemValue("categoryId", docTask.getItemValueString("categoryId") );
				docTaskNew.replaceItemValue("categoryName", docTask.getItemValueString("categoryName") );
				docTaskNew.replaceItemValue("categoryOrder", docTask.getItemValueInteger("categoryOrder") );
			
				docTaskNew.replaceItemValue("responsibilityIds", docTask.getItemValue("responsibilityIds") );
				docTaskNew.replaceItemValue("responsibilityNames", docTask.getItemValue("responsibilityNames") );
			
				docTaskNew.replaceItemValue("roleIds", docTask.getItemValue("roleIds") );
				docTaskNew.replaceItemValue("roleNames", docTask.getItemValue("roleNames") );
			
				docTaskNew.replaceItemValue("planNames", docTask.getItemValue("planNames") );
				docTaskNew.replaceItemValue("planIds", docTask.getItemValue("planIds") );
				docTaskNew.replaceItemValue("scenarioId", docTask.getItemValueString("scenarioId") );
				docTaskNew.replaceItemValue("scenarioName", docTask.getItemValueString("scenarioName") );
			
				docTaskNew.replaceItemValue("quickGuideIds", docTask.getItemValue("quickGuideIds") );
			
				docTaskNew.replaceItemValue("order", docTask.getItemValue("order") );
			
				var authors = [];
				authors.push(sessionScope.userName);
				authors.push( "[bcEditor]");
				authors.push( "[bcUser]");
			
				docTaskNew.replaceItemValue("docAuthors", authors );
				docTaskNew.save();
			
				//dBar.debug("saved a task, unid " + docTaskNew.getUniversalID() );
			
				veTask = vecTasks.getNextEntry();
			}

			//update number of open tasks, assigned to the current user (based on bc role)
			//IS THIS NEEDED FOR BB VERSION?
			sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("userOrgUnitIds"), sessionScope.get("roleId")) );

			//print("Num Assigned Tasks = " + sessionScope.get("numAssignedTasks"));
		}
		
	} catch (e) {
		print("ERROR ASSIGNING TASKS: " + e);
	}
}
function processTask(docUnid, status){
	//mark a task as done/ undone
  print("IN PROCESS TASK");
  try{
	print("GETTING DOC");  
	var doc = database.getDocumentByUNID(docUnid);
	if(null != doc) {
		print("DOC NOT NULL");  
		//var isRehearsal = doc.getItemValueString("isRehearsal").equals("yes");
		var isRehearsal = "yes";

		print("REPLACING DOC ITEMS");
		doc.replaceItemValue("status", (status.equals("complete") ? "complete" : "assigned") );
		doc.replaceItemValue("statusChangedBy", sessionScope.get("userName") );
		doc.replaceItemValue("statusChangedByName", sessionScope.get("name") );
		doc.save();

		print("CREATING UPDATE");
		//Create update item for this completion
		createUpdate( (isRehearsal ? "Exercise task" : "Task") + " marked as " + (status.equals("done") ? "completed" : "incomplete") + ": <b>" + doc.getItemValueString("name") + "</b><br />" +
			"- incident: " + doc.getItemValueString("incidentName") + "<br />" +
			"- by: " +	sessionScope.get("name")
		);

		print("UPDATING NUM TASKS");
		//update number of open tasks for this user
		sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("userOrgUnitIds"), sessionScope.get("roleId")) );
  	}
	print("DOC MUST BE NULL!! Unid = " + docUnid );  
  }
  catch(e){
	  print("ERROR PROCESSING TASK: " + e);
  }
 }
