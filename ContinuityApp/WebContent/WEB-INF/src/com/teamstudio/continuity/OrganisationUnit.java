package com.teamstudio.continuity;

import lotus.domino.Database;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;

public class OrganisationUnit {

	//retrieve a org unit name based on a orgunit id
	public static String getName(String orgUnitId) {
		
		Session session = null;
		Database dbCore = null;
		View vwAllById = null;
		ViewEntry veOrgUnit = null;
		
		String orgUnitName = "";
		
		try {
			
			if ( StringUtil.isNotEmpty(orgUnitId) && !orgUnitId.equals("none") ) {
				
				Configuration config = Configuration.get();
				
				session = ExtLibUtil.getCurrentSession();
				dbCore = session.getDatabase(config.getServerName(), config.getCoreDbPath() );
				vwAllById = dbCore.getView("vwAllByID");
				
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
