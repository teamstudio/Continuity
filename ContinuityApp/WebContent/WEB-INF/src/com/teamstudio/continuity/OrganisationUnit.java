package com.teamstudio.continuity;

import lotus.domino.View;
import lotus.domino.ViewEntry;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
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
	
	
}
