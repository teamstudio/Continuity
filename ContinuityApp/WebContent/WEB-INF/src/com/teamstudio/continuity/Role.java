package com.teamstudio.continuity;

import java.io.Serializable;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;

public class Role implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String id;
	private String name;

	public Role() {
		
	}
	
	//retrieve a role name based on a role id
	public static String getName(String roleId) {
		
		Database dbCurrent = null;
		View vwAllById = null;
		ViewEntry veRole = null;
		
		String roleName = "";
		
		try {
			
			if ( StringUtil.isNotEmpty(roleId) && !roleId.equals("none") ) {
				
				dbCurrent = ExtLibUtil.getCurrentDatabase();
				vwAllById = dbCurrent.getView("vwAllByID");
				
				veRole = vwAllById.getEntryByKey(roleId, true);
				if (veRole != null) {
					roleName = (String) veRole.getColumnValues().get(1);
				}
			}
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(veRole, vwAllById, dbCurrent);
		}
		
		return roleName;
	}
	
	
	//saves the role document, updates related items
	public boolean save( com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocRole ) {
		
		boolean success = false;
		
		try {
			
			boolean isNew = xspDocRole.isNewNote();
			
			if (isNew) {
				xspDocRole.replaceItemValue("id", "r" + xspDocRole.getDocument().getUniversalID().toLowerCase() );
				xspDocRole.replaceItemValue("docAuthors", Authorizations.ROLE_EDITOR);

				Document doc = xspDocRole.getDocument(true);
				doc.getFirstItem("docAuthors").setAuthors(true);
			}

			xspDocRole.save();	
			
			this.id = xspDocRole.getItemValueString("id");
			this.name = xspDocRole.getItemValueString("name");
			
			//update role name in all documents that use this role (e.g. contacts)
			Utils.fieldValueChange("roleId", id, "roleName", name );
			
			success = true;
			
		} catch (Exception e) {
			
			Logger.error(e);
			
		} finally {
			
		}
		
		return success;
		
		
	}
	
}
