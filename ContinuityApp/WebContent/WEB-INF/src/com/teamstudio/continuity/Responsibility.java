package com.teamstudio.continuity;

import java.io.Serializable;

import lotus.domino.Document;

import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;

public class Responsibility implements Serializable {

	private static final long serialVersionUID = 1L;

	public Responsibility() { }
	
	public boolean save( com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocResponsibility ) {
		
		boolean success = false;
		
		Document doc = null;
		
		try {

			if (xspDocResponsibility.isNewNote()) {
				
				//set default authors
				doc = xspDocResponsibility.getDocument(true);
				
				doc.replaceItemValue("docAuthors", Authorizations.ROLE_EDITOR);
				doc.getFirstItem("docAuthors").setAuthors(true);
				
				//create id
				doc.replaceItemValue("id", "r" + doc.getUniversalID().toLowerCase());
				
			}

			//save the current xsp document
			xspDocResponsibility.save();
			
			success = true;
		
		} catch (Exception e) {
			Logger.error(e);
			
		} finally {
		
		}
		
		return success;
	}

	
}

