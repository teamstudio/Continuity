function init( theme:String) {
	
	var dbCore:NotesDatabase = null;
	
	try {
		
		//load the application configuration
		loadAppConfig(false);
		
		if (!isUnplugged()) { 
			var currentTheme = context.getSessionProperty("xsp.theme");

			//switch the theme used by this application
			if ( currentTheme == null || !currentTheme.equals(theme)) {
				
				var f = "/"+@RightBack(context.getUrl().getAddress(),"/");
				context.setSessionProperty("xsp.theme",theme);
				context.redirectToPage(f);
	
			}
		}
		
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
			dbCore = session.getDatabase( applicationScope.get("server"), applicationScope.get("coreDbPath") );
			
			var docUser:NotesDocument = null;
			
			if (dbCore == null || !dbCore.isOpen() ) {
				
				throw("could not open core database at " + applicationScope.get("coreDbPath"));
				
			} else {
				
				docUser = dbCore.getView("vwContactsByUsername").getDocumentByKey( currentUser, true);
					
			}
			
			if (docUser==null) {
				dBar.error("could not retrieve user profile");	
			}
			
			var isEditor = false;
			var isUser = false;
			var isDebug = false;
			
			if ( isUnplugged() ) {
				//determine the user's role based on settings in his profile
				
				if (null != docUser) {
					
					var userType:String = docUser.getItemValueString("userType");
					isEditor = userType.equals("editor");
					isUser = isEditor || userType.equals("user");
					
					isDebug = docUser.getItemValueString("debugMode").equals("yes");
					
				}
				
			} else {
				//determine the user's role based on his userroles
				
				var roles = context.getUser().getRoles();
				isEditor = roles.contains("[bcEditor]");
				isUser = isEditor || roles.contains("[bcUser]");
				isDebug = roles.contains("[debug]");
				
			}
			
			//dBar.debug("editor? " + isEditor + ", user? " + isUser + "");
			
			sessionScope.put("isEditor", isEditor);
			sessionScope.put("isUser", isUser);
			sessionScope.put("isDebug", isDebug);
			
			try {
				sessionScope.put("canEdit", ( database.getCurrentAccessLevel()>=3));	
			} catch (ee) { }
							
			//set default menu option
			sessionScope.put("selectedMenu", (isEditor ? "miniConfigGuide" : "contactsList") );
			
			var ua = @LowerCase( context.getUserAgent().getUserAgent() );
			
			sessionScope.put("isPhoneSupported", !@Contains( ua, "ipad") && !@Contains( ua, "ipod") );
			
			if (null != docUser) {
				//retrieve settings from the user's profile
				
				//dBar.debug("init: found user doc");
				
				var name:string = docUser.getItemValueString("firstName") + " " + docUser.getItemValueString("lastName");
			
				sessionScope.put("name", name );
				sessionScope.put("email", docUser.getItemValueString("email"));
				
				sessionScope.put("profileUnid", docUser.getUniversalID());

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
				
				//get org unit alert level
				var orgUnitId = sessionScope.get("orgUnitId");
				if (orgUnitId.length>0) {
				
					var vwOrgUnits = dbCore.getView("vwAllById");
					var docOrgUnit = vwOrgUnits.getDocumentByKey( orgUnitId, true);
				
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
						
			dBar.debug("done");
			
		} else {
			
			//dBar.debug("init: sessionScope already loaded for " + currentUser);
		}
		
	} catch (e) {
		dBar.error( e.toString() );
	} finally {
		try {
			dbCore.close();
		} catch (e) { }
		
	}
}		//end init()

//helper function to work with Maps/ associative arrays in Unplugged or a browser
function getMap() {
	return (isUnplugged() ? [] : new java.util.HashMap() );	
}

//get number of open tasks, assigned to the current user (based on bc role)
function getNumAssignedTasks(orgUnitId, roleId) {
	
	var numAssignedTasks = 0;
	
	try {
	
		var vwTasks = database.getView("vwTasksAssignedByRoleId");
		var key = orgUnitId + "-" + roleId;
		
		var vec:NotesViewEntryCollection = vwTasks.getAllEntriesByKey(key, true);
		numAssignedTasks = vec.getCount();
		
		//dBar.debug("found " + numAssignedTasks + " assigned tasks for ou " + orgUnitId + " and role " + roleName);
		
	} catch (e) {
		dBar.error(e);	
	}
	
	return numAssignedTasks;
}

//load application config (if needed)
function loadAppConfig( forceUpdate:boolean ) {
	
	try {
		
		if ( !applicationScope.configLoaded || forceUpdate ) {
			
			dBar.debug("loading application config");
			
			applicationScope.put("configLoaded", true);
			
			applicationScope.put("server", ( isUnplugged() ? "" : database.getServer() ));
			applicationScope.put("thisDbPath", database.getFilePath() );
			applicationScope.put("thisDbUrl", "/" + @ReplaceSubstring(database.getFilePath(), "\\", "/") );
			
			applicationScope.put("toolbarConfig", "[['FontSize'], ['Bold','Italic','Underline','Strike','TextColor'], ['JustifyLeft', 'JustifyCenter', 'JustifyRight', 'JustifyBlock'],  ['Undo', 'Redo', '-', 'Cut', 'Copy', 'Paste', 'PasteText'], ['NumberedList','BulletedList','Blockquote'], ['Link', 'Unlink']]");
			
			//find path to core database
			var vwSettings:NotesView = database.getView("vwSettings");
			var docSettings:NotesDocument = vwSettings.getDocumentByKey("fSettings", true);
			
			if (docSettings == null ) {
				
				print("error: path to core database could not be set");
				
			} else {
			
				var coreDbPath:String = docSettings.getItemValueString("coreDbPath");
			
				if (isUnplugged()) { 
					coreDbPath = @Left(coreDbPath, ".nsf") + ".unp";
				}
				
				//replace backward with forward slashes (else we can't open the databases)
				coreDbPath = @ReplaceSubstring(coreDbPath, "\\", "/");
			
				applicationScope.put("coreDbPath", coreDbPath);
				applicationScope.put("coreDbUrl", "/" + coreDbPath );
				
				applicationScope.put("isReadOnlyMode", docSettings.getItemValueString("readOnlyMode").equals("yes"));				
			}
			
			var docTemp:NotesDocument = null;
			
			//get all roles
			var vwRoles = database.getView("vwRoles");
			var vecRoles:NotesViewEntryCollection = vwRoles.getAllEntries();
			var veRole:NotesViewEntry = vecRoles.getFirstEntry();
			
			var veTemp:NotesViewEntry = null;
			
			var roles = getMap();
			
			while (null != veRole) {
				
				var colValues = veRole.getColumnValues();
				
				var id:String = colValues.get(3);
				roles[id] = colValues.get(1);
				
				veTemp = vecRoles.getNextEntry();
				veRole.recycle();
				veRole = veTemp;
			}
		
			applicationScope.put("roles", roles);
			
			vecRoles.recycle();
			vwRoles.recycle();
			
			//get call tree order
			vwRoles = database.getView("vwRolesCallTreeOrder");
			vecRoles = vwRoles.getAllEntries();
			veRole = vecRoles.getFirstEntry();
			
			var callTreeOrder = [];
			
			while (null != veRole) {
				
				var colValues = veRole.getColumnValues();
				callTreeOrder.push( colValues.get(1) );
				
				veTemp = vecRoles.getNextEntry();
				veRole.recycle();
				veRole = veTemp;
			}

			applicationScope.put("callTreeOrder", callTreeOrder);		//determines order in the calltree
			
			vecRoles.recycle();
			vwRoles.recycle();
		
			//get all ou's from core database
			var veTemp:NotesViewEntry = null;

			var dbCore:NotesDatabase = session.getDatabase( applicationScope.get("server"), applicationScope.get("coreDbPath") );
			
			if (!dbCore.isOpen()) {
				
				throw("error: could not open core database at " + applicationScope.get("coreDbPath"));
			
			} else {
				
				//store a list of all org units in the application scope
				var vwOrgUnits:NotesView = dbCore.getView("vwOrgUnits");
				var vecOrgUnits:NotesViewEntryCollection = vwOrgUnits.getAllEntries();
				var veOrgUnit:NotesViewEntry = vecOrgUnits.getFirstEntry();
			
				var orgUnitChoices = [];
				var orgUnits = getMap();
							
				while (null != veOrgUnit) {
					
					var colValues = veOrgUnit.getColumnValues();
					var name = colValues.get(0);
					var id = colValues.get(2);
					
					orgUnitChoices.push( colValues.get(1) );
					
					orgUnits[id] = name;
					
					veTemp = vecOrgUnits.getNextEntry();
					veOrgUnit.recycle();
					veOrgUnit = veTemp;
				}
				
				applicationScope.put("orgUnitChoices", orgUnitChoices);
				applicationScope.put("orgUnits", orgUnits);
				
				vwOrgUnits.recycle();
				
				//store a list of all sites in the application scope (for combobox/ radio selections)
				var vwSites:NotesView = dbCore.getView("vwSites");
				var vecSites:NotesViewEntryCollection = vwSites.getAllEntries();
				var veSite:NotesViewEntry = vecSites.getFirstEntry();
				
				var siteChoices = [];
				
				while (null != veSite) {
					siteChoices.push( veSite.getColumnValues().get(1) );
					
					veTemp = vecSites.getNextEntry();
					veSite.recycle();
					veSite = veTemp;
				}
	
				applicationScope.put("siteChoices", siteChoices);
				
				vwSites.recycle();
			}
			
			dBar.debug("done");

		} else {
			
			//dBar.debug("loadAppConfig: already loaded");
			
		}
		
	} catch (e) {
		dBar.error( e.toString() );
	} finally {
		try {
			dbCore.close();
		} catch (e) { }
	
	}
	
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
			var dbCore:NotesDatabase = session.getDatabase( applicationScope.get("server"), applicationScope.get("coreDbPath") );
			var docUser = dbCore.getDocumentByUNID( sessionScope.profileUnid );
			
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

			dbCore.close()
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
		
		var profilePhotoBaseUrl:String = ( isUnplugged() ? "" : "/.ibmxspres/domino") + applicationScope.get("coreDbUrl");
		var profilePhotoUrl:String = profilePhotoBaseUrl + "/noProfile.gif";
		
		if (!profilePhotos[userName] || forceUpdate) {
			
			//dBar.debug("get image for " + userName + " from db");
			
			//retrieve url to user's profile photo in core database
			var dbCore:NotesDatabase = session.getDatabase( applicationScope.get("server"), applicationScope.get("coreDbPath") );
			var vwContacts:NotesView = dbCore.getView("vwContactsByUsername");
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
		
		try {
			dbCore.close();
		} catch (e) { }
	
	}
		
	//return image url
	return profilePhotoUrl;
	
}

//sort an array of objects by a property of those objects
Array.prototype.sortByField = function( fieldName:String, direction:String, fieldDataType:String ){
	
	var values = this;
	var fieldA, fieldB;
	
	function lowerCaseSort( a, b ){

		fieldA = a[fieldName];
		fieldB = b[fieldName];
		
		if (fieldA=="" && fieldB=="") { 
			return 0;
		} else if (fieldA=="") { 
			return 1;
		} else if (fieldB=="") { 
			return -1; 
		}
		
		fieldA = fieldA.toLowerCase();
		fieldB = fieldB.toLowerCase();
		
		if( fieldA > fieldB ){ return 1 * multiplier; }
		if( fieldA < fieldB ){ return -1 * multiplier; }
		return 0;
	}
	
	function compareToSort(a,b) {
		fieldA = a[fieldName];
		fieldB = b[fieldName];
		
		if (fieldA==null && fieldB==null) { 
			return 0;
		} else if (fieldA==null) { 
			return 1;
		} else if (fieldB==null) { 
			return -1; 
		}
		
		return ( fieldA.compareTo(fieldB) * multiplier);
	}
	
	function genericSort( a, b ){
		
		fieldA = a[fieldName];
		fieldB = b[fieldName];
		
		if (fieldA==null && fieldB==null) { 
			return 0;
		} else if (fieldA==null) { 
			return 1;
		} else if (fieldB==null) { 
			return -1; 
		}
		
		if( fieldA > fieldB ){ return 1 * multiplier; }
		if( fieldA < fieldB ){ return -1 * multiplier; }
		return 0;
	}
	
	try {
		
		if( !fieldName || values.length === 0 ){ return values; }
		
		direction = direction || 'ascending';
		fieldDataType = fieldDataType || 'string';
		
		var multiplier = (direction === 'ascending') ? 1 : -1;
		
		var sortFunction;
		if (fieldDataType.indexOf('string')>=0) {
			sortFunction = lowerCaseSort;
		} else if (fieldDataType.indexOf('date')>=0) {
			sortFunction = compareToSort;
		} else {
			sortFunction = genericSort;
		}
		
		values.sort(sortFunction);	
		
	} catch (e) {
		dBar.error( e, "sortByField" );
	}
	
	return values;
	
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
		
		docUpdate.replaceItemValue("message", text );
		
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
			
			docTaskNew.replaceItemValue("order", docTask.getItemValue("order") );
			
			docTaskNew.replaceItemValue("docAuthors", "*" );	//TODO: who should be able to edit a task?
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

function getScenariosByPlan( orgUnit ) {
	
	var plans = [];
	
	try {
		
		dBar.debug("retrieving plans for org unit " + orgUnit + "...");
		
		//create an array of all plans for the current orgUnit, with in every plan an array of all scenario's in that plan
		var vwScenariosByOrgUnitId:NotesView = database.getView("vwScenariosByOrgUnitId");
		var nav:NotesViewNavigator = vwScenariosByOrgUnitId.createViewNavFromCategory( orgUnit );
		
		//dBar.debug("found " + nav.getCount() + "nav entries");
		
		var _plan = null;
		
		var ve:NotesViewEntry = nav.getFirst();
		while (null != ve) {
		
			var colValues = ve.getColumnValues();
		
			if (ve.isCategory() ) {
		
				//dBar.debug("found cat: " + colValues.get(1) );
		
				if (_plan != null) { plans.push( _plan ); }
								
				
				_plan = {
					"name" : colValues.get(1),
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
					
					dBar.debug("adding scenario: " + scenarioName);
		
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
			
			}
			
			ve = nav.getNext();
		}
			
		if (_plan != null) { plans.push( _plan ); }
		
		nav.recycle();
		vwScenariosByOrgUnitId.recycle();
		
		dBar.debug("finished");
		
	} catch (e) {
		dBar.error(e);	

	}
		
	return plans;
}
