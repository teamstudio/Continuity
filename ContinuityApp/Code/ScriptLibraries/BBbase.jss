function configApp() {
	var userName = "CN=ted.barnhouse/O=ZetaComm";
	if ( !applicationScope.configLoaded) {
		try{
			var userView = database.getView("BBUserInfo");
			var userEntry = userView.getAllEntriesByKey(userName);
			var userRow = userEntry.getFirstEntry();
			var userDocId = userRow.getColumnValues()[1];
			applicationScope.put("UNID", userDocId);
			
			var userDoc = database.getDocumentByUNID(userDocId);
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
			sessionScope.put("orgUnitName", userDoc.getItemValueString("orgUnitName"));			
			var orgUnitId = userDoc.getItemValueString("orgUnitId");
			sessionScope.put("orgUnitId", orgUnitId);			
			
			applicationScope.put("status", "High Alert");
			
			//get org unit alert level
			if (orgUnitId.length>0) {
			
				var vwOrgUnits = database.getView("BBvwOrgUnitsById");
				var docOrgUnit = vwOrgUnits.getDocumentByKey( orgUnitId, true);
			
				if (docOrgUnit != null) {
					sessionScope.put("orgUnitAlertLevel", docOrgUnit.getItemValueString("alertLevel") );
				}
			}
			
			applicationScope.put("configLoaded", true);	
			
			return true;
		}
		catch (e){
			applicationScope.put("error", e);
			print("error: " + e);
			return false;
		}	
	} 
}

//creates a new update item with the specified message
function createUpdate( text, incidentId, incidentName) {

	print("CREATING UPDATE");
	
	if (applicationScope.isReadOnlyMode) {
		return false;
	}
	
	var docUpdate = null;
		
	try {
		docUpdate = database.createDocument();
		docUpdate.replaceItemValue("form", "fUpdate");
		docUpdate.replaceItemValue("createdBy", sessionScope.get("userName") );
		docUpdate.replaceItemValue("createdByName", sessionScope.get("name") );
		
		docUpdate.replaceItemValue("message", text );
		
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
	
	if (applicationScope.isReadOnlyMode) {
		return false;
	}

	var docMemo = null;
	
	try {
		
		docMemo = database.createDocument();
		docMemo.replaceItemValue("form", "Memo");
		//docMemo.replaceItemValue("SendTo", to );
		docMemo.replaceItemValue("SendTo", @Explode(to) );
		docMemo.replaceItemValue("Subject", subject );
	
		docMemo.replaceItemValue("From", fromName + "<" + fromEmail + "@NotesDomain>");
		docMemo.replaceItemValue("SMTPOriginator", fromEmail);
		docMemo.replaceItemValue("Sender", fromEmail);
		docMemo.replaceItemValue("Principal", fromEmail + "@NotesDomain" );
		docMemo.replaceItemValue("INETFROM", fromName + "<" + fromEmail + ">");
	
		docMemo.replaceItemValue("Body", body );
		
		docMemo.send();
		print("MAIL SENT");
		
	} catch (e) {
		print("ERROR SENDING MAIL: " + e);
	}
	
}

//set default fields that should be available on all documents
function setDefaultFields( doc ) {
	
	print("GOT TO SET DEFAULT FIELDS");
	
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
	print("PROCESS ACTIVATION");
	
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
	
	var updateText = ((isRehearsal ? "Exercise incident: " : "Incident: ") + incidentDesc +
		"- alert level: " +	doc.getItemValueString("alertLevel") +
			"- org unit: " + doc.getItemValue("orgUnitName") +
			"- asset: " + doc.getItemValueString("siteName") +
			"- plan: " + doc.getItemValueString("scenarioName"));
		
	createUpdate( updateText, doc.getItemValueString("id"), incidentDesc );
	
	//Send email to all BC users in the system	
	var BCUsersResult = @Unique(@DbColumn( "", "BBvwBCUsers",3));
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
					"[continuity] " + (isRehearsal ? "Exercise incident " + produced : "Incident " + produced), 
					sessionScope.name + " has just "+ produced +" an incident in Continuity:\r\n\r\n incident: " + doc.getItemValueString("description") + "\r\n\r\n" +
		"- alert level: " +	doc.getItemValueString("alertLevel") + "\r\n" +
			"- org unit: " + doc.getItemValue("orgUnitName") + "\r\n" +
			"- asset: " + doc.getItemValueString("siteName") + "\r\n" +
			"- plan: " + doc.getItemValueString("scenarioName"),
			sessionScope.email, sessionScope.name);
	
}

function processOUAlertLevel(doc){
	
		print("PROCESS OU ALERT LEVEL");	
	
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
				maxAlertLevel = getMaxAlertLevel( sessionScope.get("orgUnitId") );
			}
				
			if (orgUnitId == sessionScope.get("orgUnitId") ) {
				sessionScope.put("orgUnitAlertLevel", maxAlertLevel);
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
		print("ERROR: GET MAX ALERT LEVEL");
		print("ERROR IS: " + e);
		
	}
	return highest
}

function deactivateIncident(doc){
	
	if (!applicationScope.isReadOnlyMode) {
			
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
				if (orgUnitId == sessionScope.get("orgUnitId") ) {
					sessionScope.put("orgUnitAlertLevel", maxAlertLevel);
				}
			}
			catch(e){
				print("ERROR IN DEACTIVATE: " + e);
			}

			//ML: update number of assigned tasks
			sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("orgUnitId"), sessionScope.get("roleId")) );
			
			//ML: create update item for this deactivation
			var incidentDesc = doc.getItemValueString("description")
			
			var updateText = ((isRehearsal ? "Deactivated exercise incident: " : "Deactivated incident: ") + incidentDesc +
				"- alert level: " +	doc.getItemValueString("alertLevel") +
				"- org unit: " + doc.getItemValue("orgUnitName")  +
				"- asset: " + doc.getItemValueString("siteName") +
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
function getNumAssignedTasks(orgUnitId, roleId) {
	
	//dBar.debug("get num assigned...")
	
	var numAssignedTasks = 0;
	
	try {
	
		var vwTasks = database.getView("vwTasksAssignedByRoleId");
		var key = orgUnitId + "-" + roleId;
		
		var vec:NotesViewEntryCollection = vwTasks.getAllEntriesByKey(key, true);
		numAssignedTasks = vec.getCount();
		
		//dBar.debug("found " + numAssignedTasks + " assigned tasks for ou " + orgUnitId + " and role " + roleId);
		
	} catch (e) {
		dBar.error(e);	
	}
	
	return numAssignedTasks;
}

//copy and assign tasks to new incidents
function assignTasksToIncident(docIncident) {

	print("IN ASSIGN TASKS TO INCIDENT!");
	try {
		
		if (applicationScope.isReadOnlyMode) {		//don't assign tasks in demo mode
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
			sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("orgUnitId"), sessionScope.get("roleId")) );
			print("Num Assigned Tasks = " + sessionScope.get("numAssignedTasks"));
		}
		
	} catch (e) {
		print("ERROR ASSIGNING TASKS: " + e);
	}
}

