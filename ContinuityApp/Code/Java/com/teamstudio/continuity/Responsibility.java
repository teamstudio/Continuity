package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.Vector;

import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
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
				
				com.teamstudio.continuity.User.get().updateMenuOptionCounts();
				
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
	
	/*
	 * Delete a responsibility from the system, including references
	 */
	public static boolean remove( DominoDocument docResp) {
		
		boolean success = false;
		
		try {

			Logger.info("remove responsibility: " + docResp.getItemValueString("name") );
			
			String respId = docResp.getItemValueString("id");
			
			removeResponsibility(respId);
			
			docResp.getDocument().remove(true);

			User.get().updateMenuOptionCounts();
			
			success = true;
		} catch (Exception e) {

			Logger.error(e);
		}
		
		return success;
		
		
	}
	
	@SuppressWarnings("unchecked")
	private static void removeResponsibility(String respId) throws NotesException {
		
		//find tasks in which this responsibility is listed and remove it
		DocumentCollection dc = ExtLibUtil.getCurrentDatabase().search( "Form=\"fTask\" & @IsMember(\"" + respId + "\"; responsibilityIds)" );
		
		Logger.debug("- found " + dc.getCount() + " related documents to remove responsibility from");

		Document doc = dc.getFirstDocument();
		while (null != doc) {
			
			Vector<String> respIds = doc.getItemValue("responsibilityIds");
			Vector<String> respNames = doc.getItemValue("responsibilityNames");
			boolean hasRespNames = (respNames != null && respNames.size() == respIds.size());
			
			int pos = respIds.indexOf(respId);
			respIds.remove(pos);
			
			doc.replaceItemValue("responsibilityIds", respIds);
			
			if (hasRespNames) {
				respNames.remove(pos);
				doc.replaceItemValue("responsibilityNames", respNames);
			}
			
			doc.save();
			
			Document next = dc.getNextDocument(doc);
			Utils.recycle(doc);
			doc = next;
		}
		
	}

	
}

