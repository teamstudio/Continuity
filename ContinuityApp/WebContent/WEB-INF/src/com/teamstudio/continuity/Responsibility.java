package com.teamstudio.continuity;

import java.io.Serializable;

import lotus.domino.Document;

import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class Responsibility implements Serializable {

	private static final long serialVersionUID = 1L;

	public Responsibility() { }
	
	public boolean save( com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocResponsibility ) {
		
		boolean success = false;
		
		Document doc = null;
		
		try {

			boolean isNew = xspDocResponsibility.isNewNote();
			
			if (isNew) {
				
				//set default authors
				doc = xspDocResponsibility.getDocument(true);
				
				doc.replaceItemValue("docAuthors", Authorizations.ROLE_EDITOR);
				doc.getFirstItem("docAuthors").setAuthors(true);
				
				//create id
				doc.replaceItemValue("id", "r" + doc.getUniversalID().toLowerCase());
				
			}

			//save the current xsp document
			xspDocResponsibility.save();

			if (isNew) {
				
				Configuration.get().updateMenuOptionCounts();
				
			} else {
				
				//update changed name in all related documents
				
				String id = xspDocResponsibility.getItemValueString("id");
				String name = xspDocResponsibility.getItemValueString("name");
			
				Utils.fieldValueChange("responsibilityIds", id, "responsibilityNames",  xspDocResponsibility.getItemValueString("roleName") + " - " + name );
				Utils.fieldValueChange("responsibilityId", id, "responsibilityName",  name );
				
			}
			
			success = true;
		
		} catch (Exception e) {
			Logger.error(e);
			
		} finally {
		
		}
		
		return success;
	}

	
}

