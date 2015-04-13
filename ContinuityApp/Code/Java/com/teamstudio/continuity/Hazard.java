package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class Hazard implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public Hazard() {
		
	}
	
	//retrieve a hazard description based on an id
	public static String getName(String hazardId) {
		
		Database dbCurrent = null;
		View vwAllById = null;
		ViewEntry ve = null;
		
		String name = "";
		
		try {
			
			if ( StringUtil.isNotEmpty(hazardId) && !hazardId.equals("none") ) {
				
				dbCurrent = ExtLibUtil.getCurrentDatabase();
				vwAllById = dbCurrent.getView("vwAllByID");
				
				ve = vwAllById.getEntryByKey(hazardId, true);
				if (ve != null) {
					name = (String) ve.getColumnValues().get(1);
				}
			}
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(ve, vwAllById, dbCurrent);
		}
		
		return name;
	}
	
	public static Vector<String> getNames( Vector<String> hazardIds) {
		
		Database dbCurrent = null;
		View vwAllById = null;
		ViewEntry ve = null;
		
		Vector<String> name = new Vector<String>();
		
		try {
			
			dbCurrent = ExtLibUtil.getCurrentDatabase();
			vwAllById = dbCurrent.getView("vwAllByID");
			
			for(String hazardId : hazardIds) {
				
				if ( StringUtil.isNotEmpty(hazardId)) {
					
					ve = vwAllById.getEntryByKey(hazardId, true);
					if (ve != null) {
						name.add( (String) ve.getColumnValues().get(1) );
					}
					
				}
				
			}
	
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(ve, vwAllById, dbCurrent);
		}
		
		return name;
		
	}
	
	
}
