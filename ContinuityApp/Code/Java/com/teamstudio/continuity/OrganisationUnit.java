package com.teamstudio.continuity;

import java.util.HashMap;
import java.util.Vector;

import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Logger;

public class OrganisationUnit {

	//retrieve a org unit name based on a orgunit id
	public static String getName(String orgUnitId) {
		
		View vwAllById = null;
		ViewEntry veOrgUnit = null;
		
		String orgUnitName = "";
		
		try {
			
			if ( StringUtil.isNotEmpty(orgUnitId) && !orgUnitId.equals("none") ) {
				
				vwAllById = ExtLibUtil.getCurrentDatabase().getView("vwAllByID");
				
				veOrgUnit = vwAllById.getEntryByKey(orgUnitId, true);
				
				if (veOrgUnit != null) {
					orgUnitName = (String) veOrgUnit.getColumnValues().get(1);
				}
			}
		} catch (Exception e) {
			Logger.error(e);
		}
		
		return orgUnitName;
	}
	
	@SuppressWarnings("unchecked")
	public static void saveOrgUnits( DominoDocument xspDoc, boolean clearWhenAll ) throws NotesException {
		
		HashMap<String,String> orgUnits = (HashMap<String, String>) ExtLibUtil.getApplicationScope().get("orgUnits");
		Vector<String> orgUnitIds = new Vector<String>( orgUnits.keySet() );
		Vector<String> orgUnitNames = new Vector<String>( orgUnits.keySet() );
		
		String orgUnitTarget = xspDoc.getItemValueString("orgUnitTarget");
		
		if (orgUnitTarget.equals("all") ) {
			
			xspDoc.replaceItemValue("orgUnitIds", (clearWhenAll ? "" : orgUnitIds)  );
			xspDoc.replaceItemValue("orgUnitNames", (clearWhenAll ? "" : orgUnitNames) );
		
		} else {
		
			orgUnitIds.clear();
			orgUnitNames.clear();
			
			orgUnitIds = xspDoc.getItemValue("orgUnitIds");
			
			for(String orgUnitId : orgUnitIds ) {
				
				orgUnitNames.add( OrganisationUnit.getName(orgUnitId));
				
			}
			
			xspDoc.replaceItemValue("orgUnitNames", orgUnitNames);
		}
	}
	
	
	
}
