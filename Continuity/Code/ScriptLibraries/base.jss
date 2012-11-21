function init( theme:String) {
	
	try {
		
		var currentTheme = context.getSessionProperty("xsp.theme");

		//switch the theme used by this application
		if ( currentTheme == null || !currentTheme.equals(theme)) {
			
			var f = "/"+@RightBack(context.getUrl().getAddress(),"/");
			context.setSessionProperty("xsp.theme",theme);
			context.redirectToPage(f);

		}
		
		//load the application configuration
		loadAppConfig(false);
		
		var currentUser = @UserName();

		//workaround here to re-init the session vars if another user has logged in:
		if ( !sessionScope.configLoaded || sessionScope.get("userName") != currentUser) {
			
			sessionScope.put("configLoaded", true);
			sessionScope.put("userName", currentUser);
			
			var roles = context.getUser().getRoles();
			sessionScope.put("isBCUser", roles.contains("[bcUser]" ));
			sessionScope.put("isBCEditor", roles.contains("[bcEditor]" ));
			sessionScope.put("isDebug", roles.contains("[debug]" ));
		
			//retrieve information from user's profile
			var docUser:NotesDocument = database.getView("vwContactsByUsername").getDocumentByKey( @UserName(), true);
		
			if (null != docUser) {
				
				sessionScope.put("name", docUser.getItemValueString("firstName") + " " + docUser.getItemValueString("lastName") );

				//store user's bcm role id
				sessionScope.put("roleId", docUser.getItemValueString("roleId"));
				sessionScope.put("roleName", docUser.getItemValueString("roleName"));
					
				//store user's org unit
				sessionScope.put("orgUnitId", docUser.getItemValueString("orgUnitId"));
				sessionScope.put("orgUnitName", docUser.getItemValueString("orgUnitName"));
	
			} else {
				
				var nm = session.createName(currentUser);
				sessionScope.put("name", nm.getCommon() );
				
			}
			
			//DEBUG: temporary order for call tree
			var callTreeOrder = [];
			
			callTreeOrder.push("rbf2f648f1ae58bacc1257aa0004cc02e");		//global coord
			callTreeOrder.push("r289B3A8124684D07C1257AA1004B82A5");		//local coord
			callTreeOrder.push("rdcf63d99b25b3be0c1257aa0004cc548");		//team member
			
			sessionScope.put("callTreeOrder", callTreeOrder);
			
		}
	} catch (e) {
		dBar.error(e);
	}
}

//load application config (if needed)
function loadAppConfig( forceUpdate:boolean ) {
	
	try {
	
		if ( !applicationScope.configLoaded || forceUpdate ) {
			
			print("(re)loading application config");
			
			applicationScope.put("configLoaded", true);
			
			var docTemp:NotesDocument = null;
			
			//get all roles
			var vwRoles = database.getView("vwRoles");
			var docRole:NotesDocument = vwRoles.getFirstDocument();
			var roles = new java.util.HashMap();
			while (null != docRole) {
		
				roles.put( docRole.getItemValueString("id"), docRole.getItemValueString("name"))
				
				docTemp = vwRoles.getNextDocument(docRole);
				docRole.recycle();
				docRole = docTemp;
			}
		
			applicationScope.put("roles", roles);
		
			//get all ou's
			var vwOrgUnits = database.getView("vwOrgUnits");
			var docOrgUnit:NotesDocument = vwOrgUnits.getFirstDocument();
			
			var orgUnits = new java.util.HashMap();
			while (null != docOrgUnit) {
		
				orgUnits.put( docOrgUnit.getItemValueString("id"), {
					unid : docOrgUnit.getUniversalID(),
					name : docOrgUnit.getItemValueString("name"),
					alertLevel : docOrgUnit.getItemValueString("alertLevel")
				
				});
						
				docTemp = vwOrgUnits.getNextDocument(docOrgUnit);
				docOrgUnit.recycle();
				docOrgUnit = docTemp;
			}
		
			applicationScope.put("orgUnits", orgUnits);
			
			
		}
	} catch (e) {
		dBar.error(e);	
	}
	
}

//mark a task as 'completed'
function markTaskCompleted( name:String ) {
	
	logActivity( "task completed: " + name);
	
}


//store a user activity
function logActivity( message:String ) {
	
	//get user's activity document
	try {
		
		dBar.debug("logging activity");
		
		//TODO: use sessionscope variable to hold log documents' unid
		var docLog:NotesDocument = database.getView("vwLogsByUser").getDocumentByKey( sessionScope.get("userName"), true);
		var logEntries:java.util.Vector = null;
		
		if (docLog==null) {
			
			dBar.debug("get existing doc");
			
			docLog = database.createDocument();
			docLog.replaceItemValue("form", "fLog");
			docLog.replaceItemValue("userName", sessionScope.get("userName")).setAuthors(true);
			docLog.replaceItemValue("name", sessionScope.get("name"))
			docLog.replaceItemValue("docAuthors", "[bcEditor]").setAuthors(true);
			
			logEntries = new java.util.Vector();
			
		} else {
			
			logEntries = docLog.getItemValue("logEntries");
			
		}
		
		logEntries.add(0, @Text(@Now()) + " - " + message);
		
		docLog.replaceItemValue("logEntries", logEntries);
		docLog.save();
		docLog.recycle();
		
		dBar.debug("done");
	
	} catch (e) {
		dBar.error(e);
	}
	
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
		log.error( e, "sortByField" );
	}
	
	return values;
	
}