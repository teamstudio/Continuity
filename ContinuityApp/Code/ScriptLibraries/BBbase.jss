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
			var lastName = userDoc.getItemValueString("lastName")
			applicationScope.put("firstName", firstName);
			applicationScope.put("lastName", lastName);
			sessionScope.put("name", firstName + " " + lastName);
			sessionScope.put("userName", userName);
			
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
			
			applicationScope.put("error", "No Error!");
			
			applicationScope.put("configLoaded", true);	
			
			return true;
		}
		catch (e){
			applicationScope.put("error", e);
			print("error: " + e);
			return false;
		}	
	} //End if
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
	} finally {
		docUpdate.recycle();
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
		docMemo.replaceItemValue("SendTo", @Explode(to) );
		docMemo.replaceItemValue("Subject", subject );
	
		docMemo.replaceItemValue("From", fromName + "<" + fromEmail + "@NotesDomain>");
		docMemo.replaceItemValue("SMTPOriginator", fromEmail);
		docMemo.replaceItemValue("Sender", fromEmail);
		docMemo.replaceItemValue("Principal", fromEmail + "@NotesDomain" );
		docMemo.replaceItemValue("INETFROM", fromName + "<" + fromEmail + ">");
	
		docMemo.replaceItemValue("Body", body );
		
		docMemo.send();
		
	} catch (e) {
	} finally {
		docMemo.recycle();
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
	
}

function processNewActivation(doc){
	
	print("GOT TO PROCESS NEW ACTIVATION");
	
	/*var DBScenarioResult = @DbLookup( "", "BBvwAllScenarios", doc.getItemValueString("scenarioName"),2);
	var scenarioId = "None";
	if (DBScenarioResult != undefined){
		var scenarioId = (DBScenarioResult.constructor == Array) ? DBScenarioResult : [ DBScenarioResult ];
	}
	doc.replaceItemValueString("scenarioId", scenarioId);

	var DBSiteIdResult = @DbLookup( "", "BBvwAssetDetails", doc.getItemValueString("siteName"),5);
	if (DBSiteIdResult != undefined){
		var siteId = (DBSiteIdResult.constructor == Array) ? DBSiteIdResult : [ DBSiteIdResult ];
	}
	doc.replaceItemValue("siteId", siteId);
	doc.replaceItemValue("formname", "fIncident");
	
	doc.save();*/
	doc.replaceItemValue("unid", doc.getUniversalID().toLowerCase());
	setDefaultFields(doc);
	processActivation(doc.getUniversalID(), "incident");
}

function processActivation(unid, formType){

	try{
		doc = database.getDocumentByUNID(unid);
	}catch(e){ 
		print("ERROR: FAILED GETTING DOC BY UNID");	
	}
	print("GOT DOC BY UNID");	
	if (doc != null){
		
		print("DOC NOT NULL");		
		
		//ML: set default authors
		var authors = [];
		authors.push( "[bcEditor]");
		authors.push( sessionScope.get("userName") );
		
		doc.replaceItemValue("docAuthors", authors);		//set to authors type in TMSRunAfterPush agent
		
		if (formType == "incident") {
			doc.replaceItemValue("status", "active");
		}
		doc.save();
		
	} 
	
	/*if (context.getUrlParameter("parentunid") != "" && context.getUrlParameter("parentunid") != "null"){
		var parent:NotesDocument = database.getDocumentByUNID(context.getUrlParameter("parentunid"));
		resp = "creating response to parentunid = " + parent.getUniversalID();
		doc.makeResponse(parent);
	}*/
	var dateformat = /^(19|20)\d\d[- \/.](0[1-9]|1[012])[- \/.](0[1-9]|[12][0-9]|3[01])$/;
	
	try{
		
		//ml: get org unit name
		var scenarioId = doc.getItemValueString("scenarioId");
		
		if (scenarioId.indexOf("#")>-1) {		//see activation form
			var idAndPlan = scenarioId;
			scenarioId = @Left(idAndPlan, "#");
			planName = @Right(idAndPlan, "#");
			
			var vwPlans = database.getView("vwPlans"); 
			var vePlan = vwPlans.getEntryByKey( planName, true); 
			
			if (vePlan != null) {
				planId = vePlan.getColumnValues().get(3);
				
				doc.replaceItemValue("planId", planId);
			}
			
			doc.replaceItemValue("scenarioId", scenarioId);
			doc.replaceItemValue("planName", planName);
			
		}
		
		print("GETTING DOC FIELDS");	
		
		
		var orgUnitId = doc.getItemValueString("orgUnitId");
		var siteId = doc.getItemValueString("siteId");
		var alertLevel = doc.getItemValueString("alertLevel");
		var maxAlertLevel = alertLevel;		
		
		var vwById = database.getView("vwAllById");
		var docOU = null;
		
		if (!applicationScope.isReadOnlyMode) {
		
			if ( orgUnitId.length > 0 ) {
			
				//get org unit name and set/ update alert level
				docOU = vwById.getDocumentByKey(orgUnitId, true);
				
				if (null != docOU) {
					doc.replaceItemValue("orgUnitName", docOU.getItemValueString("name") );
				}
			}
			
			print("DONE OU");	
			
			if ( formType == "isTask" ) {		//saving a new task
				
				doc.replaceItemValue("status", "assigned" );
				orgUnitId = sessionScope.orgUnitId;
				doc.replaceItemValue("orgUnitId", orgUnitId );
					
				//find incident name
				var veTemp = vwById.getEntryByKey( doc.getItemValueString("incidentId"), true);
				if (null != veTemp) {
					doc.replaceItemValue("incidentName", veTemp.getColumnValues().get(1)  );
					veTemp.recycle();
				}
				
			}			
			
			if( orgUnitId.length>0 || scenarioId.length>0 || siteId.length>0 || isIncident ) {
					
				print("GETTING SCENARIO ID");	
				
				//get scenario name (from current db)
				var scenarioName = "";
				if (scenarioId != "none") {
					docTemp = vwById.getDocumentByKey(scenarioId, true);
					if (null != docTemp) {
						
						assignTasks( doc, docTemp );
						
						scenarioName = docTemp.getItemValueString("name");
						docTemp.recycle();
					}
				}
				doc.replaceItemValue("scenarioName", scenarioName);
					
				//get site name
				var siteName = "";
				docTemp = vwById.getDocumentByKey(siteId, true);
				if (null != docTemp) {
					siteName = docTemp.getItemValueString("name");
				}
				doc.replaceItemValue("siteName", siteName );
			}
		
			doc.save();
			
			if ( (formType == "incident") && orgUnitId.length > 0 ) {
			
				print("UPDATING OU");	
				
				
				if (docOU == null) {
					docOU = vwById.getDocumentByKey(orgUnitId, true);
				}
				
				//update alert level in OU document
				/*if ( alertLevel.equals("high") ) {
					maxAlertLevel = "high";
				} else {
					maxAlertLevel = getMaxAlertLevel( sessionScope.get("orgUnitId") );
				}
				
				docOU.replaceItemValue("alertLevel", maxAlertLevel);
				docOU.replaceItemValue("activeScenarioId", scenarioId );
				docOU.save();	
				*/
			}
			
			print("GOT MAX ALERT LEVEL");
			
			if (formType == "incident") {
				//ML: update alert level in session scope (if new incident was registered for current OU)
				if (orgUnitId == sessionScope.get("orgUnitId") ) {
					sessionScope.put("orgUnitAlertLevel", maxAlertLevel);
				}
		
				var isRehearsal = doc.getItemValueString("isRehearsal").equals("yes");

				var incidentDesc = doc.getItemValueString("description");
				
				//ML: create update item for new incidents
				var updateText = (isRehearsal ? "Exercise incident" : "Incident: " + incidentDesc +
					"- alert level: " +	doc.getItemValueString("alertLevel") +
						"- org unit: " + doc.getItemValueString("orgUnitName") +
						"- asset: " + doc.getItemValueString("siteName") +
						"- plan: " + doc.getItemValueString("scenarioName"));
					
				createUpdate( updateText, doc.getItemValueString("id"), incidentDesc );
					
				//ML: send email to all BC users in the system
				/*var vwBCUsers = database.getView("vwBCUsers");
				var nav = vwBCUsers.createViewNav();
				var ve = nav.getFirst();
				while (null != ve) {
				*/	
						//sendMail( ve.getColumnValues().get(0) , 
				print("SENDING MAIL");
						sendMail( "Richard_Sharpe@teamstudio.com", 								
								"[continuity] " + (isRehearsal ? "Exercise incident created" : "Incident created"), 
								sessionScope.name + " has just created an incident in Continuity:\r\n\r\n incident: " + doc.getItemValueString("description") + "\r\n\r\n" +
					"- alert level: " +	doc.getItemValueString("alertLevel") + "\r\n" +
						"- org unit: " + doc.getItemValueString("orgUnitName") + "\r\n" +
						"- asset: " + doc.getItemValueString("siteName") + "\r\n" +
						"- plan: " + doc.getItemValueString("scenarioName"),
						sessionScope.email, sessionScope.name);
					
				//		ve = nav.getNext();
				//}
					
				}
			}
		
			/*if (doc.getItemValueString("form")=="fMessage") {
			
				//send mail to all users
				var toUsers = doc.getItemValue("sendTo");
			
				for (var i=0; i<toUsers.size(); i++) {
				
					var to = toUsers.get(i);
					
					if (to.length > 0) {
						
						sendMail( to, 
							"[continuity] new message", 
							applicationScope.name + " has sent you the following message:\r\n\r\n" +
								(isRehearsal ? "Exercise " + applicationScope.labels['incident'].toLowerCase() : applicationScope.labels['incident']) + ": " + doc.getItemValueString("incidentName") + "\r\n" +
								"Message: " + doc.getItemValueString("message") + "\r\n",
							sessionScope.email, sessionScope.name);
					}
				
				}			
			}*/
	}
	catch(e){
	}
}
//returns the maximum alert level based on all open incidents
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
			var alertLevel = ve.getColumnValues().get(5);
			
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
	}
	
	return highest

}


