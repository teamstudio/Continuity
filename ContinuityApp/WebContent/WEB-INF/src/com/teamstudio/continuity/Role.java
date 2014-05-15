package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.Vector;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.View;

public class Role implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String id;
	private String name;
	private String appMenuOptions;
	private Vector<String> appMenuOptionsActive;

	public Role() {
		
	}
	
	//initialize the role based on a role id
	@SuppressWarnings("unchecked")
	public Role( String roleId ) {
		
		Database dbCurrent = null;
		View vwAllById = null;
		Document docRole = null;
		
		try {
			
			if ( StringUtil.isNotEmpty(roleId) && !roleId.equals("none") ) {
				
				dbCurrent = ExtLibUtil.getCurrentDatabase();
				vwAllById = dbCurrent.getView("vwAllByID");
				
				docRole = vwAllById.getDocumentByKey(roleId, true);
				if (docRole != null) {
					this.id = roleId;
					this.name = docRole.getItemValueString("name");
					this.appMenuOptions =  docRole.getItemValueString("appMenuOptions");
					this.appMenuOptionsActive = docRole.getItemValue("appMenuOptionsActive");
				}
			}
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docRole, vwAllById);
		}
		
	}

	public String getName() {
		return name;
	}
	public String getId() {
		return id;
	}
	public String getAppMenuOptions() {
		return appMenuOptions;
	}
	public Vector<String> getAppMenuOptionsActive() {
		return appMenuOptionsActive;
	}
	
	//saves the role document, updates related items
	@SuppressWarnings("unchecked")
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
			this.appMenuOptions = xspDocRole.getItemValueString("appMenuOptions");
			this.appMenuOptionsActive = xspDocRole.getItemValue("appMenuOptionsActive");
			
			//find contacts for this role: update menuOptions
			DocumentCollection dc = xspDocRole.getParentDatabase().search("Form=\"fContact\" & roleId=\"" + id + "\"");
			if (dc.getCount()>0) {
				dc.stampAll("appMenuOptions", this.appMenuOptions);
				dc.stampAll("appMenuOptionsActive", this.appMenuOptionsActive);
			}
			
			//update role name in all documents that use this role (e.g. contacts)
			Utils.fieldValueChange("roleId", id, "roleName", name );
			
			success = true;

			com.teamstudio.continuity.User.get().updateMenuOptionCounts();
			
		} catch (Exception e) {
			
			Logger.error(e);
			
		} finally {
			
		}
		
		return success;
		
		
	}
	
}
