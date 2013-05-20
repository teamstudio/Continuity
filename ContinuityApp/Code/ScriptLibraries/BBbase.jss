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
			applicationScope.put("name", firstName + " " + lastName);
			sessionScope.put("roleName", userDoc.getItemValueString("roleName"));			
			sessionScope.put("roleId", userDoc.getItemValueString("roleId"));			
			sessionScope.put("orgUnitName", userDoc.getItemValueString("orgUnitName"));			
			var orgUnitId = userDoc.getItemValueString("orgUnitId");
			sessionScope.put("orgUnitId", orgUnitId);			
			//applicationScope.put("siteNames", userDoc.getItemValue("siteNames"));			
			
			//sessionScope.put("roleId", "rbf2f648f1ae58bacc1257aa0004cc02e");
			var siteNames = new Array();
			siteNames[0] = "site 1"
			siteNames[1] = "site 2"
			siteNames[2] = "site 3"
			sessionScope.put("siteNames", siteNames);			
			
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
