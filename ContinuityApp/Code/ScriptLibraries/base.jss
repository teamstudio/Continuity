function init() {
	
	try {
		
		//load the application configuration
		loadAppConfig(false);

		var currentUser = @UserName();
		
		//workaround here to re-init the session vars if another user has logged in:
		if ( !sessionScope.configLoaded || sessionScope.get("userName") != currentUser) {
			
			dBar.debug("load sessionScope for " + currentUser);
			
			//clear entire sessionScope first
			var it = sessionScope.keySet().iterator();
			while (it.hasNext() ) {
				var key = it.next();
				if (key != "dBar") {
					sessionScope.put( key, null);
				}
			}
			
			sessionScope.put("configLoaded", true);
			sessionScope.put("userName", currentUser);
			
			//retrieve information from user's profile
			var docUser:NotesDocument = database.getView("vwContactsByUsername").getDocumentByKey( currentUser, true);
			
			if (docUser==null) {
				dBar.error("could not retrieve user profile");	
			}
			
			var isEditor = false;
			//var isUser = false;
			var isDebug = false;
			
			if ( isUnplugged() ) {
				//determine the user's role based on settings in his profile
				
				if (null != docUser) {
					
					var userType:String = docUser.getItemValueString("userType");
					isEditor = userType.equals("editor");
					isDebug = docUser.getItemValueString("debugMode").equals("yes");
					
				}
				
			} else {
				//determine the user's role based on his userroles
				
				var roles = context.getUser().getRoles();
				isEditor = roles.contains("[bcEditor]");
				isDebug = roles.contains("[debug]");
				
			}
			
			sessionScope.put("isEditor", isEditor);
			sessionScope.put("isDebug", isDebug);
			
			if (isDebug) {
				dBar.setEnabled(true);
				dBar.info("Continuity is running in debug mode")
			}
			
			try {
				sessionScope.put("canEdit", ( database.getCurrentAccessLevel()>=3));	
			} catch (ee) { }
			
			if ( !isUnplugged() ) {
				//web ui only functions
											
				//set default menu option
				if (isEditor) {
					
					//show mini guide to editors only once
					if ( currentUserBean.isMiniGuideShown() ) {
						sessionScope.put("selectedMenu", "scenariosList");
					} else {
						sessionScope.put("selectedMenu", "miniConfigGuide");
						currentUserBean.setMiniGuideShown();
					}
				} else {
					sessionScope.put("selectedMenu", "contactsList");
				}
				
			}
			
			var ua = @LowerCase( context.getUserAgent().getUserAgent() );
			
			sessionScope.put("isPhoneSupported", !@Contains( ua, "ipad") && !@Contains( ua, "ipod") );
			
			if (null != docUser) {
				
				//retrieve settings from the user's profile
				
				//dBar.debug("init: found user doc");
				
				var name:string = docUser.getItemValueString("firstName") + " " + docUser.getItemValueString("lastName");
			
				sessionScope.put("name", name );
				sessionScope.put("email", docUser.getItemValueString("email"));
				
				sessionScope.put("profileUnid", docUser.getUniversalID());
				sessionScope.put("profileId", docUser.getItemValueString("id") );

				//get user's bcm role
				sessionScope.put("roleId", docUser.getItemValueString("roleId"));
				sessionScope.put("roleName", docUser.getItemValueString("roleName"));
					
				//get user's org unit
				sessionScope.put("orgUnitId", docUser.getItemValueString("orgUnitId"));
				sessionScope.put("orgUnitName", docUser.getItemValueString("orgUnitName"));
				
				//get number of new updates for this user
				var numNewUpdates = getNumNewItems( docUser.getItemValueString("lastViewedUpdate"), "vwUpdates" )
				sessionScope.put("numUpdates", numNewUpdates );
				
				//get number of new incidents for this user
				var numNewIncidents = getNumNewItems( docUser.getItemValueString("lastViewedIncident"), "vwIncidents" );
				sessionScope.put("numIncidents", numNewIncidents);
				
				//store current login
				docUser.replaceItemValue("lastLogin", session.createDateTime(@Now()) );
				docUser.save();
				docUser.recycle();
					
				//profile photo
				sessionScope.put("profilePhotoUrl", getProfilePhotoUrl(currentUser) );
				
				//get org unit's alert level
				var orgUnitId = sessionScope.get("orgUnitId");
				if (orgUnitId.length>0) {
					
					var _unid = applicationScope.get("orgUnitUnids")[orgUnitId];
					var docOrgUnit = database.getDocumentByUNID( _unid );
					
					if (docOrgUnit != null) {
						sessionScope.put("orgUnitAlertLevel", docOrgUnit.getItemValueString("alertLevel") );
					}
					
				}
				
			} else {
				
				var nm = session.createName(currentUser);
				sessionScope.put("name", nm.getCommon() );
				
			}
			
			//get number of open tasks, assigned to the current user (based on bc role)
			sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("orgUnitId"), sessionScope.get("roleId")) );
			
			if (!isUnplugged() ) {
				
				//web ui only
				checkMaxAlertLevels();
				
			}
						
			dBar.debug("done");
			
		} else {
			
			//dBar.debug("init: sessionScope already loaded for " + currentUser);
		}
		
	} catch (e) {
		dBar.error( e.toString() );
	} finally {

	}
}		//end init()

//check max alert level on all ou's, update if needed
function checkMaxAlertLevels() {
	
	try {
	
		var dbCurrent = sessionAsSigner.getDatabase( database.getServer(), database.getFilePath() );
		var vwOrgUnits = dbCurrent.getView("vwOrgUnits");
		
		var docOU = vwOrgUnits.getFirstDocument();
		while (null != docOU) {
			var id = docOU.getItemValueString("id");
			var name = docOU.getItemValueString("name");
			var alertLevel = docOU.getItemValue("alertLevel");
			
			var maxAlertLevel = getMaxAlertLevel( id );
			
			if (maxAlertLevel != alertLevel) {
				docOU.replaceItemValue("alertLevel", maxAlertLevel);
				docOU.save();
			}
			
			var docTemp = vwOrgUnits.getNextDocument(docOU);
			docOU.recycle();
			docOU = docTemp;
		}
	} catch (e) {
		dBar.error(e);	
	}
}


//helper function to work with Maps/ associative arrays in Unplugged or a browser
function getMap() {
	return (isUnplugged() ? [] : new java.util.HashMap() );	
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

/*
function getNumOpenTasks( vwTarget, incidentId, orgUnitId, roleId ) {
	
	var numOpenTasks = 0;
	
	try {
	
		var key = [];
		key.push( incidentId );
		key.push( orgUnitId + "-" + roleId);
		
		var vec = vwTarget.getAllEntriesByKey(key, true);
		numOpenTasks = vec.getCount();
		
	} catch (e) {
		
		dBar.error(e);
		
	}
	
	return numOpenTasks;
	
}
*/


//load application config (if needed)
function loadAppConfig( forceUpdate:boolean ) {
	
	try {
		
		if ( !applicationScope.configLoaded || forceUpdate ) {
			
			dBar.debug("loading application config");
			
			applicationScope.put("configLoaded", true);
			
			applicationScope.put("server", ( isUnplugged() ? "" : database.getServer() ));
			applicationScope.put("thisDbPath", database.getFilePath() );
			applicationScope.put("thisDbUrlDefault", "/" + @ReplaceSubstring(database.getFilePath(), "\\", "/") );
			applicationScope.put("thisDbUrl", ( isUnplugged() ? "" : "/.ibmxspres/domino") + "/" + @ReplaceSubstring(database.getFilePath(), "\\", "/") );
			
			applicationScope.put("toolbarConfig", "[['FontSize'], ['Bold','Italic','Underline','Strike','TextColor'], ['JustifyLeft', 'JustifyCenter', 'JustifyRight', 'JustifyBlock'],  ['Undo', 'Redo', '-', 'Cut', 'Copy', 'Paste', 'PasteText'], ['NumberedList','BulletedList','Blockquote'], ['Link', 'Unlink']]");
			
			var vwAllByType:NotesView = database.getView("vwAllByType");
			var docSettings:NotesDocument = vwAllByType.getDocumentByKey("fSettings", true);
			
			if (docSettings == null ) {
				
				print("error: settings document not found");
				
			} else {
				
				applicationScope.put("isReadOnlyMode", docSettings.getItemValueString("readOnlyMode").equals("yes"));
				
				var callTreeType = docSettings.getItemValueString("callTreeType")
				if (callTreeType == "" ) {
					callTreeType = "role";
				}
				applicationScope.put("callTreeType", callTreeType);
				
				if (callTreeType == "role") {
					applicationScope.put("callTreePrefix", "UnpCallTree");	
				} else {
					applicationScope.put("callTreePrefix", "UnpCallTreeCustom");	
				}
				
				//labels
				var labels = getMap();
				
				//labels
				var riskNaming = docSettings.getItemValueString("riskNaming");
				
				if ( riskNaming.equals("activities")) {
					labels["assets"] = "Activities";
					labels["asset"] = "Activity";
				} else if ( riskNaming.equals("sites")) {	
					labels["assets"] = "Sites";
					labels["asset"] = "Site";
				} else if ( riskNaming.equals("locations")) {	
					labels["assets"] = "Locations";
					labels["asset"] = "Location";
				} else {
					labels["assets"] = "Assets";
					labels["asset"] = "Asset";
				}
				
				if (docSettings.getItemValueString("incidentNaming").equals("crises")) {
					labels["incidents"] = "Crises";
					labels["incident"] = "Crisis";
				} else if (docSettings.getItemValueString("incidentNaming").equals("emergencies")) {
					labels["incidents"] = "Emergencies";
					labels["incident"] = "Emergency";
				} else {
					labels["incidents"] = "Incidents";
					labels["incident"] = "Incident";
				}
				
				applicationScope.put("labels", labels);
				
			}
			
			//get all ou's
			var veTemp:NotesViewEntry = null;
				
			//store a list of all org units in the application scope
			var vecOrgUnits:NotesViewEntryCollection = vwAllByType.getAllEntriesByKey("fOrgUnit", true);
			var veOrgUnit:NotesViewEntry = vecOrgUnits.getFirstEntry();
		
			var orgUnitChoices = [];				//array containing all org units in 'combobox select options' syntax (key|value)
			var orgUnits = getMap();				//map of org unit id / names
			var orgUnitUnids = getMap();			//map of org unit id / unids
						
			while (null != veOrgUnit) {
				
				var colValues = veOrgUnit.getColumnValues();
				
				var id = colValues.get(1);
				var name = colValues.get(2);
				var unid = veOrgUnit.getUniversalID();
				
				orgUnitChoices.push( colValues.get(3) );
				
				orgUnits[id] = name;
				orgUnitUnids[id] = unid;
				
				veTemp = vecOrgUnits.getNextEntry();
				veOrgUnit.recycle();
				veOrgUnit = veTemp;
			}
			
			orgUnitChoices.sort();
			
			applicationScope.put("orgUnitChoices", orgUnitChoices);
			applicationScope.put("orgUnits", orgUnits);
			applicationScope.put("orgUnitUnids", orgUnitUnids);
			
		} else {
			
			//dBar.debug("loadAppConfig: already loaded");
			
		}
		
	} catch (e) {
		dBar.error( e.toString() );
	}
	
}

//retrieve the calltree order based on the bc roles in the calltree, list is cached in the appScope
function getCallTreeOrder() {
	
	var varName:String = "callTreeOrder";
	
	try {
	
		if (applicationScope.containsKey( varName )) {
			
			return applicationScope.get( varName );
			
		} else {
			
			//get call tree order
			var vwRoles:NotesView = database.getView("vwRolesCallTreeOrder");
			var vecRoles:ViewEntryCollection = vwRoles.getAllEntries();
			var veRole:NotesViewEntry = vecRoles.getFirstEntry();
			
			var callTreeOrder = [];
			
			while (null != veRole) {
				
				var colValues = veRole.getColumnValues();
				callTreeOrder.push( colValues.get(1) );
				
				veTemp = vecRoles.getNextEntry();
				veRole.recycle();
				veRole = veTemp;
			}
			
			vecRoles.recycle();
			vwRoles.recycle();
	
			applicationScope.put( varName , callTreeOrder);		//determines order in the calltree
			
			return callTreeOrder;
			
		}
		
	} catch (e) {
		dBar.error(e);
		
	}
		
}

//retrieve a JavaScript object representing the call tree for the specified org unit
function getCallTree( orgUnitId:String ) {
	
	try {
	
		dBar.debug("get call tree for org unit: " + orgUnitId);
		
		//get root user
		var dcRoot = database.search("Form=\"fContact\" & callTreeRoot=\"true\" & orgUnitId=\"" + orgUnitId + "\"");
		
		dBar.debug("found " + dcRoot.getCount() + " root user(s) for org unit " + orgUnitId);
		
		var docContact  = dcRoot.getFirstDocument();
		
		if (null != docContact) {		//no call tree/ root user
			
			var rootUserId = docContact.getItemValueString("id");
			var vwCallTree:NotesView = database.getView("vwAllById");
			return getCallTreeEntry( rootUserId, 1, vwCallTree );
			
		} else {
			
			return null;
		}
	} catch (e) {
		
		dBar.error(e);
		return null;
	}
	
}

function getCallTreeDetails( toCall, level, vwCallTree:NotesView ) {

	if (typeof toCall == 'string') { 
	
		if (toCall.length==0) {
			return [];
		}
		toCall = [toCall];
		
	}
	
	var result = [];
	
	for (var i=0; i<toCall.length; i++) {
		result.push( getCallTreeEntry( toCall[i], level, vwCallTree ));
	}
	
	return result;
}

//retrieve an entry for the call tree for a specific user
function getCallTreeEntry( contactId, level, vwCallTree:NotesView ) {
	
	var entry = {};

	//dBar.debug("get for " + contactId + ", level: " + level);
	
	var doc = vwCallTree.getDocumentByKey(contactId, true);
	
	if (doc == null) {  //end of call tree
		
		//dBar.debug("no result");
		return null;	
	}

	var userName = doc.getItemValueString("userName");
	var name = doc.getItemValueString("name");
	var calls = doc.getItemValue("callTreeContacts");
	
	var phoneTypePrimary = doc.getItemValueString("phoneTypePrimary");
	if (phoneTypePrimary.length==0) {
		phoneTypePrimary="mobile";
	}
	
	var phoneNumber = doc.getItemValueString("phoneMobile");
	
	if (phoneTypePrimary == "work") {
		phoneNumber = doc.getItemValueString("phoneWork");
	} else if (phoneTypePrimary == "home") {
		phoneNumber = doc.getItemValueString("phoneHome");
	}
	
	var newLevel = level + 1;
	
	var entry = {
		"unid" : doc.getUniversalID(),
		"id" : contactId,
		"level" : level,
		"userName" : userName,
		"name" : name, 
		"phoneNumber" : phoneNumber,
		"calls" : getCallTreeDetails(calls, newLevel, vwCallTree)
	};


	return entry;
}

//get the call trees root element
function getCallTreeRootUser( orgUnitId:String ) {
	
	var root = sessionScope.get("callTreeRoot");
	
	if ( !sessionScope.containsKey("callTreeRoot") || root[orgUnitId] == null ) {

		//get root contact for this OU from database
		var vwRoot = database.getView("vwCallTreeRoot");
		var veRoot = vwRoot.getEntryByKey( orgUnitId, true);

		var rootUser = "";
		
		if (veRoot != null) {
			rootUser = veRoot.getColumnValues().get(1);
		}
		
		if (root == null) {
			root = getMap();	
		}
		
		root[orgUnitId] = rootUser;
		
		sessionScope.put("callTreeRoot", root );

	}
	
	return root[orgUnitId];
}


//retrieve a list of bc roles, list is cached in the appScope
function getRoles() {
	
	var varName:String = "roles";
	
	try {
		
		if (applicationScope.containsKey(varName) ) {
			
			return applicationScope.get(varName);
			
		} else {
			
			var vwAllByType:NotesView = database.getView("vwAllByType");
			var vecRoles:NotesViewEntryCollection = vwAllByType.getAllEntriesByKey("fRole", true);
			var veRole:NotesViewEntry = vecRoles.getFirstEntry();
		
			var roles = getMap();
		
			while (null != veRole) {
			
				var colValues = veRole.getColumnValues();
				
				var id:String = colValues.get(1);
				var name:String = colValues.get(2);
				
				roles[id] = name;
				
				var veTemp = vecRoles.getNextEntry();
				veRole.recycle();
				veRole = veTemp;
			}
	
			applicationScope.put(varName, roles);	
			
			vecRoles.recycle();
			vwAllByType.recycle();
			
			return roles;
			
		}
		
	} catch (e) {
		dBar.error(e);
	}

}

//retrieve a list of bc roles, list is cached in the appScope
function getRoleChoices() {
	
	var varName:String = "roleChoices";
	
	try {
		
		if (applicationScope.containsKey(varName) ) {
			
			return applicationScope.get(varName);
			
		} else {
			
			
			var vwAllByType:NotesView = database.getView("vwAllByType");
			var vecRoles:NotesViewEntryCollection = vwAllByType.getAllEntriesByKey("fRole", true);
			var veRole:NotesViewEntry = vecRoles.getFirstEntry();
		
			var roleChoices = [];
			
			while (null != veRole) {
			
				var colValues:java.util.Vector = veRole.getColumnValues();
				
				var id = @Text( colValues.get(1) );
				var option = @Text( colValues.get(2) );
				
				roleChoices.push( option.concat("|", id) );
				
				var veTemp = vecRoles.getNextEntry();
				veRole.recycle();
				veRole = veTemp;
			}
			
			//sort ascending
			roleChoices.sort();
	
			applicationScope.put(varName, roleChoices);	
			
			vecRoles.recycle();
			vwAllByType.recycle();
			
			return roleChoices;
			
		}
		
	} catch (e) {
		dBar.error(e);
	}

}

//returns the maximum alert level based on all open incidents
function getMaxAlertLevel( orgUnitId ) {
	
	var highest = "normal";
	
	try {
		//dBar.debug("get max alert level");
		
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
		
		dBar.error("error while calculating max alert level:");
		dBar.error(e);
	}
	
	//dBar.debug("return: " + highest);
	return highest;

}

//sets the last viewed document for the current user (used to display number of new items on the dashboard)
function setLastViewedItem( targetView, lastViewedItemName ) {
	
	//store the time (in millisecs, stored as string) of the last viewed update in the user's profile
	//this is done so we can show the number of new docs on the dashboard
	
	try {
		
		if (sessionScope.containsKey(lastViewedItemName) && sessionScope.get(lastViewedItemName) ) {
			return;
		}

		if (!sessionScope.profileUnid) {
			dBar.debug("cannot store last update time: user profile id not found");
		
		} else {
		
			var lastViewed = "0";
			var docLastUpdate:NotesDocument = targetView.getFirstDocument();
			
			//open user's profile
			var docUser = database.getDocumentByUNID( sessionScope.profileUnid );
			
			if (docUser != null) {
			
				if (docLastUpdate != null ) {
					lastViewed = docLastUpdate.getItemValueString("createdDateMs");
					dBar.debug("set last update time to " + lastViewed + " (in item " + lastViewedItemName + ") ");		
				}
				
				if ( !docUser.getItemValueString(lastViewedItemName).equals( lastViewed )) {
			
					docUser.replaceItemValue( lastViewedItemName , lastViewed );
					docUser.save();
					
				}
				
			}

		}
		
		//store a variable in the sessionScope indicating that we don't need to perform this action again during the current session
		sessionScope.put(lastViewedItemName, true);
		
	} catch (e) {
		dBar.error(e);
	}
	
}

//retrieve the number of new documents for the current user
function getNumNewItems( lastViewed, targetView ) {

	var numUpdates = 0;
	var vwTarget:NotesView = null;

	try {

		if (lastViewed.length==0) {
			lastViewed = "0";
		}
		
		vwTarget = database.getView(targetView);
		
		if (lastViewed == "0") {		//all docs are 'new'
			
			numUpdates = vwTarget.getEntryCount();
			
		} else {
		
			//check number of 'new' docs
			
			//get the view entry for the last viewed doc and check it's position in the view
			var veLast:NotesViewEntry = vwTarget.getEntryByKey(lastViewed, true);
			
			if (veLast != null) {
				
				//get doc number
				var docNumber = veLast.getColumnValues().get(1);
				numUpdates = docNumber-1;				
			} else {
				
				//last viewed item not found in view -> return total num of updates
				numUpdates = vwTarget.getEntryCount();
				
			}
			
			
		}
		
	} catch (e) {
		dBar.error(e);
	} finally {
		
		vwTarget.recycle();
		
	}
	
	return numUpdates
	
}

//retrieve (and cache) a user's profile photo
function getProfilePhotoUrl( userName:String) {
	return getProfilePhotoUrl( userName, false);
}
function getProfilePhotoUrl( userName:String, forceUpdate:boolean ) {
	
	try {
	
		var profilePhotos = applicationScope.get("profilePhotos") || getMap();
		
		var profilePhotoBaseUrl:String = applicationScope.get("thisDbUrl");
		var profilePhotoUrl:String = profilePhotoBaseUrl + "/noProfile.gif";
		
		if (!profilePhotos[userName] || forceUpdate) {
			
			//dBar.debug("get image for " + userName + " from db");
			
			//retrieve url to user's profile photo
			var vwContacts:NotesView = database.getView("vwContactsByUsername");
			var veUser:NotesViewEntry = vwContacts.getEntryByKey( userName, true);
			
			if ( veUser != null) {
				var photoFileName = veUser.getColumnValues().get(2);
				
				if (photoFileName != null && photoFileName.length>0) {
				
					profilePhotoUrl = profilePhotoBaseUrl + "/0/" + veUser.getUniversalID() + "/$file/" + photoFileName;
					
				}
					
				veUser.recycle();
				
			}
			
			
			
			vwContacts.recycle();
			
			//store actual image or placeholder image in cache
			profilePhotos[userName] = profilePhotoUrl;
			applicationScope.put("profilePhotos", profilePhotos);
			
		} else {
			
			profilePhotoUrl = profilePhotos[userName];
			
		}
	} catch (e) {
		dBar.error(e);	
	
	} finally {
	
	}
		
	//return image url
	return profilePhotoUrl;
	
}

//function to sort an associative array by name
function sortByName(a,b) {
	if (a.name < b.name)
		return -1;
	if (a.name > b.name)
		return 1;
	return 0;
}

//check if we're running in Unplugged (cached in sessionScope)
function isUnplugged() {
	
	//check if we're running in Unplugged
	var isUnplugged:boolean = false;

	if (applicationScope.containsKey("isUnplugged")) {
		isUnplugged = applicationScope.get("isUnplugged");
	} else {
		isUnplugged = (typeof UnpluggedLib != 'undefined' && null != UnpluggedLib);
		applicationScope.put("isUnplugged", isUnplugged);
	}
	
	return isUnplugged;
	
}

/**
 * =======================================================
 * <HEADER>
 * NAME:	xpDates server side javascript library
 * VERSION:	20010826
 * AUTHOR(S):	Mark Leusink / Matt White
 * ORIGINAL SOURCE:	http://mattwhite.me/blog/2009/6/28/human-readable-dates-in-xpages.html
 					based upon code from Jake Howlett:
 					http://www.codestore.net/store.nsf/unid/BLOG-20080909?OpenDocument
 * HISTORY:
 * 20090627:	initial version
 * 20100825:	changed class name from DateHelper to ReadableDate
 *				added ReadableDateConverter to use in Xpage control's converter section 	
 *				added toReadable method	
 *				language strings externalized
 *				use Javascript Date object (instead of NotesDateTime)
 *				every time since < 1 minute is now shown as "less than 1 minute"
 * 20100826:	input date for .toReadable and .timeSince can now also be a Notes date/time object (besides a Java date)
 * 
 * USAGE
 * var date = document1.getItemValueDate("myDateField");
 * var converter = new DateConverter();
 * 
 * var datestring = converter.timeSince(date);		//example output: "1 hour ago", "3 days ago"
 * var datestring = converter.toReadable(date);		//example output: ""Today 8:10", "Yesterday 14:20", "24 July 13:20"
 *
 * Or add a xp:customConverter to the control with getAsObject = value and getAsString = ReadableDateConvert.getAsString()
 * 
 * DISCLAIMER:
 * This code is provided "as-is", and should be used at your own risk. 
 * The authors make no express or implied warranty about anything, 
 * and they will not be responsible or liable for any damage caused by 
 * the use or misuse of this code or its byproducts. No guarantees are 
 * made about anything.
 *
 * That being said, you can use, modify, and distribute this code in any
 * way you want, as long as you keep this header section intact and in
 * a prominent place in the code.
 * </HEADER>
 * =======================================================
*/
var TimeSinceConverter = {
	getAsString : function() {
		var d = new ReadableDate();
		return d.timeSince(value);
	}
};

var ReadableDate = function() {

//default config
this.includeTime = true;
this.includeSeconds = false;
this.includeYearIfCurrent = false;

this.labels = {
	ABOUT : "about",
	TODAY : "Today",
	YESTERDAY : "Yesterday",
	TOMORROW : "Tomorrow",
	AT : " ",
	LESS_MINUTE : "less than 1 minute",
	MINUTE : "minute",
	MINUTES : "minutes",
	HOUR : "hour",
	HOURS : "hours",
	YEAR : "year",
	YEARS : "years",
	MONTH : "month",
	MONTHS : "months",
	DAY : "day",
	DAYS : "days",
	PAST : "{1} ago",
	AWAY : "{1} away"
};
	
this.timePattern = "h:mm:ss aa";
this.datePattern = "d MMMM yyyy";

};

/*
* returns the number of days between a date and today
*/
ReadableDate.prototype.daysAsOf = function(date, dateNow) {
//number of milliseconds in a day
var DAY_MS = 1000 * 60 * 60 * 24;

return Math.round( this.diffInMS(date, dateNow) /DAY_MS);
};

ReadableDate.prototype.diffInMS = function(date, dateNow) {
	var date1_ms = date.getTime();
	var date2_ms = dateNow.getTime();
	
	//difference in milliseconds
	return  date1_ms - date2_ms;
}

ReadableDate.prototype.toDate = function(date, dateNow) {		
if (dateNow.getFullYear() == date.getFullYear()) {		//current year
	if (!this.includeYearIfCurrent) {
		this.datePattern = this.datePattern.replace(" yyyy", "");
	}
}

return I18n.toString(date, this.datePattern, context.getLocale());
};

ReadableDate.prototype.toReadable = function(date) {

	//convert Notes date/time object to Java date	
	if ( (typeof date).toLowerCase().equals("lotus.domino.local.datetime") ) {
		date = date.toJavaDate();
	}	
	
	var dateNow = new Date();
	var dateString;
		
	switch (this.daysAsOf(date, dateNow)) {
		case 0:		
			dateString = this.labels.TODAY;
			break;
		case -1:
			dateString = this.labels.YESTERDAY;
			break;
		case 1:
			dateString = this.labels.TOMORROW;
			break;
		default:
			dateString = this.toDate(date, dateNow);
			break;
	}
	
	if (this.includeTime) {
		if (!this.includeSeconds) {
			this.timePattern = this.timePattern.replace(":ss", "");
		}
		
		dateString += this.labels.AT + I18n.toString(date, this.timePattern, context.getLocale() );
	}
		
	return dateString;
};

ReadableDate.prototype.timeSince = function(date){
	
	try {
		var dateNow = new Date();
		
		//var t:string = (typeof date).toLowerCase();
		var diff_in_milliseconds = 0;
			
		/*if ( t=="lotus.domino.local.datetime" || t=="object" ) {
			dBar.debug("convert it")
			//convert Notes date/time object to Java date
			date = date.toJavaDate();
			dBar.debug("as java date: " + date.toString() );
			diff_in_milliseconds = this.diffInMS(date, dateNow);
		} else if ( t.equals("number")) {
			diff_in_milliseconds = date - dateNow.getTime();
		} else {
			dBar.debug("dezze");
			*/
			diff_in_milliseconds = this.diffInMS(date, dateNow);
		//}
		
		var diff_in_minutes = @Round(Math.abs(diff_in_milliseconds)/1000/60);
			
		var whichway = (diff_in_milliseconds > 0 ? this.labels.AWAY : this.labels.PAST);
		var since;
			
		if(diff_in_minutes == 0){			//< 1 minute
			since = this.labels.LESS_MINUTE;
		} else if(diff_in_minutes == 1) {	//1 minute
			since = "1 " + this.labels.MINUTE;
		} else if(diff_in_minutes >= 2 && diff_in_minutes <= 44) //2 to 44 minutes: show # minutes
			since = diff_in_minutes + " " + this.labels.MINUTES;
		else if(diff_in_minutes >= 45 && diff_in_minutes <= 89) //45 to 89 minutes: show 1 hour
			since = this.labels.ABOUT + " 1 " + this.labels.HOUR;
		else if(diff_in_minutes >= 90 && diff_in_minutes <= 1439) //show # hours
			since = this.labels.ABOUT + " " + @Round(diff_in_minutes / 60) + " " + this.labels.HOURS;
		else if(diff_in_minutes >= 1440 && diff_in_minutes <= 2879)  //1 day
			since = "1 " + this.labels.DAY;
		else if(diff_in_minutes >= 2880 && diff_in_minutes <= 43199)
			since = this.labels.ABOUT + " " + @Round(diff_in_minutes / 1440) + " " + this.labels.DAYS; //show nr of days
		else if(diff_in_minutes >= 43200 && diff_in_minutes <= 86399)
			since = this.labels.ABOUT + " 1 " + this.labels.MONTH;
		else if(diff_in_minutes >= 86400 && diff_in_minutes <= 525599)
			since = this.labels.ABOUT + " " + @Round(diff_in_minutes / 43200) + " " + this.labels.MONTHS;
		else if(diff_in_minutes >= 525600 && diff_in_minutes <= 1051199)
			since = this.labels.ABOUT + " 1 " + this.labels.YEAR;
		else {
			since = this.labels.ABOUT + " " + @Round(diff_in_minutes / 525600) + " " + this.labels.YEARS;
		}
		
		return whichway.replace( "{1}", since);
	} catch (e) {
		dBar.error(e);
	}
	return "?";
};

//creates a new update item with the specified message
function createUpdate( text:String, incidentId, incidentName) {

	if (applicationScope.isReadOnlyMode) {
		return false;
	}
	
	var docUpdate:NotesDocument = null;
		
	try {
		docUpdate = database.createDocument();
		docUpdate.replaceItemValue("form", "fUpdate");
		docUpdate.replaceItemValue("createdBy", sessionScope.get("userName") );
		docUpdate.replaceItemValue("createdByName", sessionScope.get("name") );
		
		docUpdate.replaceItemValue("messageHtml", text);
		
		var textPlain = @ReplaceSubstring(text, ["<b>", "</b>"], "" );
		
		//TOD: @newline doesn't work...
		
		docUpdate.replaceItemValue("message", @ReplaceSubstring( textPlain , ["<br>", "<br />"], "" ) );
		
		docUpdate.replaceItemValue("incidentId", incidentId);
		docUpdate.replaceItemValue("incidentName", incidentName);
		
		var createdInMs = (new Date()).getTime().toString();
	
		docUpdate.replaceItemValue("createdDateMs", createdInMs );
		
		docUpdate.replaceItemValue("docAuthors", "[bcEditor]");	//set as authors field not supported in Unplugged: done in TMSRunAfterPush agent
				
		docUpdate.save();
		
		dBar.debug("done creating update");

	} catch (e) {
		dBar.error(e);
	} finally {
		docUpdate.recycle();
	}
}

//create a new mail
function sendMail( to:string, subject:string, body:string, fromEmail:string, fromName:string ) {
	
	if (applicationScope.isReadOnlyMode) {
		return false;
	}

	var docMemo:NotesDocument = null;
	
	try {
		
		docMemo = database.createDocument();
		docMemo.replaceItemValue("form", "Memo");
		docMemo.replaceItemValue("SendTo", to );
		docMemo.replaceItemValue("Subject", subject );
	
		docMemo.replaceItemValue("From", fromName + "<" + fromEmail + "@NotesDomain>");
		docMemo.replaceItemValue("SMTPOriginator", fromEmail);
		docMemo.replaceItemValue("Sender", fromEmail);
		docMemo.replaceItemValue("Principal", fromEmail + "@NotesDomain" );
		docMemo.replaceItemValue("INETFROM", fromName + "<" + fromEmail + ">");
	
		docMemo.replaceItemValue("Body", body );
		
		docMemo.send();
		
	} catch (e) {
		dBar.error(e);
	} finally {
		docMemo.recycle();
	}
	
}

//set default fields that should be available on all documents
function setDefaultFields( doc:NotesDocument ) {
	
	//fixed id
	doc.replaceItemValue("id", "x" + doc.getUniversalID().toLowerCase() );
	
	//doc creator
	doc.replaceItemValue("createdBy", sessionScope.userName);
	doc.replaceItemValue("createdByName", sessionScope.name) ;
	
	//created date (also in milliseconds - stored as string - used in view lookups
	var now = new Date();
	var createdInMs = (now).getTime().toString();
	
	doc.replaceItemValue("createdDateMs", createdInMs );
	
}

//copy and assign tasks
function assignTasks( docIncident, docScenario ) {

	try {
		
		if (applicationScope.isReadOnlyMode) {		//don't assign tasks in demo mode
			return false;
		}

		var vwTasksByParent = database.getView("vwTasksByParent");
		var parentId = docScenario.getItemValueString("id");
		
		//retrieve all tasks belonging to the selected scenario
		var vecTasks = vwTasksByParent.getAllEntriesByKey( parentId, true);
		dBar.debug("found " + vecTasks.getCount() + " tasks for parent id " + parentId);
		
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
			
			dBar.debug("saved a task, unid " + docTaskNew.getUniversalID() );
			
			veTask = vecTasks.getNextEntry();
		}
		
		dBar.debug("all tasks have been created");
		
		//update number of open tasks, assigned to the current user (based on bc role)
		sessionScope.put( "numAssignedTasks", getNumAssignedTasks(sessionScope.get("orgUnitId"), sessionScope.get("roleId")) );
		
		dBar.debug("number of assigned tasks updated to " + sessionScope.get("numAssignedTasks") );
		
	} catch (e) {
		dBar.error(e);
	}
}

//check if a value is 'empty' (null or blank strings)
function isEmpty( input ) {
	
	if (input == null || typeof input == 'undefined') {
		return true;	
	}
	
	if (typeof input == 'string') {
		return input.equals("");
	} else if (input.toString != null) {
		return input.toString().equals("");
	}
	
	return false;
}

//retrieve an object containing information about the plans for a specific org unit
//results are cached in the sessionScope
function getScenariosByPlan( orgUnit ) {
	
	var orgUnitPlans = [];
	
	try {
		
		var plans = sessionScope.plans;
		
		if (plans == null) {
			plans = getMap();
		}
		
		if ( plans[orgUnit] != null ) {
			return plans[orgUnit];
		}
		
		dBar.debug("retrieving plans for org unit " + orgUnit + "...");
		
		//create an array of all plans for the current orgUnit, with in every plan an array of all scenario's in that plan
		var vwScenariosByOrgUnitId:NotesView = database.getView("vwScenariosByOrgUnitId");
		var nav:NotesViewNavigator = vwScenariosByOrgUnitId.createViewNavFromCategory( orgUnit );
		
		//dBar.debug("found " + nav.getCount() + "nav entries");
		
		var vwPlans:NotesView = database.getView("vwPlansById");
		
		var _plan = null;
		
		var ve:NotesViewEntry = nav.getFirst();
		while (null != ve) {
		
			var colValues = ve.getColumnValues();
		
			if (ve.isCategory() ) {
		
				if (_plan != null) { orgUnitPlans.push( _plan ); }
				
				var planName = colValues.get(1);
				
				_plan = {
					"name" : colValues.get(1),
					"fileId" : null,
					"scenarios" : [],
					"addedIds" : []
				};
		
			} else {
		
				var scenarioId = colValues.get(3);
		
				//dBar.debug("add scenario: " + colValues.get(2));
		
				var alreadyAdded = false;
		
				for (var i=0; i< _plan.addedIds.length && !alreadyAdded; i++) {
					if ( _plan.addedIds[i].equals( scenarioId ) ) {
						//dBar.debug("already added...");
						alreadyAdded = true;
					}
				}
				
				if ( !alreadyAdded ) {
					
					var scenarioName = colValues.get(2);
					var ouTarget = colValues.get(4)
					
					//dBar.debug("adding scenario: " + scenarioName);
		
					_plan.addedIds.push(scenarioId);
			
					_plan.scenarios.push( {
						"name": scenarioName,
						"id" : scenarioId,
						"orgUnitTarget" : ouTarget,
						"numTasks" : 1
					} );
				} else {
				
					_plan.scenarios[ _plan.scenarios.length-1 ].numTasks = _plan.scenarios[ _plan.scenarios.length-1 ].numTasks + 1;
				
				}
				
				if ( _plan.fileId == null ) {		//get plan's file details
					
					//dBar.debug("get deatils for " + _plan.name );
					
					//get plan id
					var doc = ve.getDocument();
					var planIds = doc.getItemValue("planIds");
					var planNames = doc.getItemValue("planNames");
					
					var fileId = "";
					var planId = "";
					
					for (var i=0; i<planNames.length; i++) {
						if (planNames[i] == _plan.name) {
							//dBar.debug("plan id found");
							planId = planIds[i];
							//dBar.debug("id = " + planId);

							var vePlan = vwPlans.getEntryByKey( planId, true);
							if (null != vePlan) {
								
								//dBar.debug("found plan entry");
								
								fileId = vePlan.getColumnValues().get(1);
								
								//dBar.debug("id = " + fileId);
							
							}
							
							break;
						}
					
					}
					
					_plan.fileId = fileId;
					_plan.planId = planId;
					
				}
				
			
			}
			
			ve = nav.getNext();
		}
			
		if (_plan != null) { orgUnitPlans.push( _plan ); }
		
		nav.recycle();
		vwScenariosByOrgUnitId.recycle();
		
		//cache in sessionScope
		plans[orgUnit] = orgUnitPlans;
		sessionScope.put("plans", plans);
		
		//dBar.debug("finished");
		
	} catch (e) {
		dBar.error(e);	

	}
		
	return orgUnitPlans;
}

function getOpenIncidentOptions(orgUnitId, selected) {
	
	var nav = database.getView("vwIncidentsOpen").createViewNavFromCategory( orgUnitId);

	var live = [];
	var excercise = [];

	var ve:NotesViewEntry = nav.getFirst();
	while (null != ve) {
	
		var colValues = ve.getColumnValues();
		var name = colValues.get(2);
		var id = colValues.get(3);
		
		var isSelected = (selected.length>0 && selected.equals( id));
		
		var option = "<option value='" + id + "' " + (isSelected ? "selected" : "") + ">" + name + "</option>"
		
		if (colValues.get(4).equals('yes') ) {
			excercise.push( option);
		} else {
			live.push(option);
		}
		
		ve = nav.getNext();
	}
	
	var html = [];
	
	html.push("<option value=''>- Select an " + applicationScope.labels['incident'].toLowerCase() + " -</option>");
	
	if (live.length>0 ) {
		html.push("<optgroup label='Live " + applicationScope.labels['incidents'].toLowerCase() + "'>");
		html = html.concat(live);
		html.push("</optgroup>");
	}
	if (excercise.length>0 ) {
		html.push("<optgroup label='Exercises'>");
		html = html.concat(excercise);
		html.push("</optgroup>");
	}
	
	return html.join("");
		
}
/**
 * Checks if a pager should be visible (has more than 1 page).
 */
function isPagerVisible(pager: com.ibm.xsp.component.xp.XspPager): boolean {
    var state: com.ibm.xsp.component.UIPager.PagerState = pager.createPagerState();
    return state.getRowCount() > state.getRows(); 
}

/* unpCommon */

/**
 * Copyright 2013 Teamstudio Inc 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 * the specific language governing permissions and limitations under the License
 */

/*
 * Returns the name of the current XPage e.g. UnpMain.xsp
 */
function getCurrentXPage(){
	if (!viewScope.containsKey("currentxpage")){
		var url = context.getUrl().toString();
		url = @Left(url, ".xsp") + ".xsp";
		if (url.indexOf(".nsf/") > -1){
			url = @Right(url, ".nsf/");
		}else{
			url = @Right(url, ".unp/");
		}
		viewScope.currentxpage = url;
	}
	return viewScope.currentxpage;
}

/*
 * Returns the current db path in the format "/dir/mydb.nsf"
 */
function getDbPath(){
	if (!applicationScope.containsKey("dbpath")){
		applicationScope.dbpath = "/" + @ReplaceSubstring(database.getFilePath(), "\\", "/");
	}
	return applicationScope.dbpath;
}

function isUnpluggedServer(){
	if (!applicationScope.containsKey("unpluggedserver")){
		try{
			if (null != UnpluggedLib) {
				applicationScope.unpluggedserver = true;
				applicationScope.dominoserver = false;
			}else{
				applicationScope.unpluggedserver = false;
				applicationScope.dominoserver = true;
			}
		}catch(e){
			applicationScope.unpluggedserver = false;
			applicationScope.dominoserver = true;
		}
	}
	return applicationScope.unpluggedserver;
}

function $A( object ){
	if( typeof object === 'undefined' || object === null ){ return []; }
	if( typeof object === 'string' ){ return [ object ]; }
	if( typeof object.toArray !== 'undefined' ){
		return object.toArray();
	}
	if( object.constructor === Array ){ return object; }  
	return [ object ];
}


/* unpDebugToolbar */

/**
 * Copyright 2013 Teamstudio Inc 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 * the specific language governing permissions and limitations under the License
 */

if ( isUnplugged() ) {

	dBar = {
			
		TYPE_DEBUG : "debug",
		TYPE_INFO : "info",
		TYPE_ERROR : "error",
		TYPE_WARNING : "warning",
		
		_get : function() {
		
			return sessionScope.get("dBar") ||
				{
					isCollapsed : false,
					isEnabled : false,
					messages : [],
					isInit : false
				};
		
		}, 
		
		init : function( collapsed:boolean ) {
			
			var dBar = this._get();
			if (!dBar.isInit) {
				dBar.isInit = true;
				dBar.isCollapsed = collapsed;
			}
			sessionScope.put("dBar", dBar);
			
		},
	
		setCollapsed : function( to:boolean ) {
			
			var dBar = this._get();
			dBar.isCollapsed = to;
			sessionScope.put("dBar", dBar);
			
		},
		
		setEnabled : function( to:boolean ) {
			
			var dBar = this._get();
			dBar.isEnabled = to;
			sessionScope.put("dBar", dBar);
			
		},
		
		//check if the toolbar is enabled
		isEnabled : function() {
			return this._get().isEnabled;
		},
		
		//returns if the toolbar is in a collapsed or expanded state
		isCollapsed : function() {
			return this._get().isCollapsed;
		},
		
		//retrieve a list of messages
		getMessages : function() {
			return this._get().messages;
		},
		
		//clears the list of messages
		clearMessages : function() {
			var dBar = this._get();
			dBar.messages = [];
			sessionScope.put("dBar", dBar);
		},
			
		//add a message to the toolbar
		//note: this function doesn't do anything if the toolbar is disabled
		addMessage : function(msg, type:String) {
			
			try {
			
				var dBar = this._get();
				
				if ( !dBar.isEnabled ) { return; }
				
				var messages = dBar.messages;
				
				if (typeof msg != "string") {
					msg = msg.toString();
				}
				
				var m = {"text": msg, "date" : @Now(), "type" : type};
				messages.unshift( m );
				
				dBar.messages = messages;
				
				sessionScope.put("dBar", dBar);
				
			} catch (e) {		//error while logging
				print(e.toString() );
			}
	
		},
		
		//function to log different types of messages
		debug : function(msg) {
			this.addMessage(msg, this.TYPE_DEBUG);	
		},
		info : function(msg) {
			this.addMessage(msg, this.TYPE_INFO);	
		},
		error : function(msg) {
			this.addMessage(msg, this.TYPE_ERROR);	
		},
		warn : function(msg) {
			this.addMessage(msg, this.TYPE_WARNING);	
		}
			
	}
}

//return the name of the image to be used for a file type
function getFileImage( type:String ) {

	if (type == null || type.length==0 ) {
		return "";
	} else if (type.equals("document") ) {
		return "file_extension_doc.png";
	} else if (type.equals("pdf") ) {
		return "file_extension_pdf.png";
	} else if (type.equals("presentation") ) {
		return "file_extension_pps.png";
	} else if (type.equals("spreadsheet") ) {
		return "file_extension_xls.png";
	} else if (type.equals("image") ) {
		return "file_extension_jpg.png";
	} else if (type.equals("video") ) {
		return "file_extension_mpg.png";
	} else {
		return "";
	}
	
}

/*
 * retrieve the name of a document based on it's id
 */
function getName( id:String ) {
	
	var names = applicationScope.names || getMap();
	
	var name = names[id];
	
	if (name == null) {
		
		var vw = database.getView("vwAllById");
		var ve = vw.getEntryByKey(id, true);
		
		if (ve != null) {
			name = ve.getColumnValues().get(1);
		} else {
			name = "?";
		}
		
		names[id] = name;
		applicationScope.put("names", names);
	
	} else {
		//return from cache
	}
	
	return name;
	
}

/*
 * retrieve info (unid, type) of a file based on it's id
 * results are cached in the sessionScope
 */
function getFileInfo( fileId:String ) {
	
	if (isEmpty(fileId) ) {
		return { valid : false };
	}
	
	//dBar.debug("get for " + fileId);
	
	var file = null;
	
	try {
		var files = sessionScope.files;
	
		if (files == null) {
			files = getMap();
		}
		
		if ( files[fileId] != null ) {
			return files[fileId];
		}
		
		var vwAll = database.getView("vwQuickGuidesById");
		var ve = vwAll.getEntryByKey(fileId, true);
		
		if (null == ve ) {
			
			file = { valid : false }
			files[fileId] = file;		//entry not found
			
		} else {
			
			var unid = ve.getUniversalID();
			var type = ve.getColumnValues().get(1);
			var image = getFileImage(type);
			
			file = {
				valid : true,
				unid : unid,
				type : type,
				image : image 
			}
			
			ve.recycle();
			
			files[fileId] = file;
		}
		
		sessionScope.files = files;
		
		vwAll.recycle();
		
	} catch (e) {
		dBar.error(e);
	}
	
	return file;
	
}
