package com.teamstudio.continuity;

import java.util.Vector;

import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class Plan {

	public static boolean save(DominoDocument xspDocPlan) {

		boolean success = false;

		try {

			boolean blnIsNew = xspDocPlan.isNewNote();

			if (blnIsNew) {

				//set the owners: [bcEditor] and owner
				xspDocPlan.replaceItemValue("docAuthors", "[bcEditor]");

				Document docBE = xspDocPlan.getDocument(true);
				docBE.getFirstItem("docAuthors").setAuthors(true);
				docBE.getFirstItem("owner").setAuthors(true);

				xspDocPlan.replaceItemValue("id", "p" + xspDocPlan.getDocument().getUniversalID().toLowerCase());

			}

			OrganisationUnit.saveOrgUnits(xspDocPlan, true);

			success = xspDocPlan.save();

			if (blnIsNew) {

				User.get().updateMenuOptionCounts();

			} else {

				//replace this name everywhere
				com.teamstudio.continuity.utils.Utils.fieldValueChange("planIds", xspDocPlan.getItemValueString("id"), "planNames",
						xspDocPlan.getItemValueString("name"));
			}

		} catch (Exception e) {
			Logger.error(e);
		}

		return success;

	}

	public static boolean remove(DominoDocument docPlan) {
		
		boolean success = false;
		
		try {
			
			String id = docPlan.getItemValueString("id");
			
			removeReferences(id);
			
			if ( docPlan.getDocument().remove(true) ) {
				success = true;
				
				//find tasks where this plan was listed
			
				User.get().updateMenuOptionCounts();
			}

		} catch (Exception e) {
			Logger.error(e);
		}
		
		return success;
		
	}
	
	@SuppressWarnings("unchecked")
	private static void removeReferences(String planId) throws NotesException {

		//find tasks in which this responsibility is listed and remove it
		DocumentCollection dc = ExtLibUtil.getCurrentDatabase().search("Form=\"fTask\" & @IsMember(\"" + planId + "\"; planIds)");

		Logger.debug("- found " + dc.getCount() + " related documents to remove plan reference from");

		Document doc = dc.getFirstDocument();
		while (null != doc) {

			Vector<String> planIds = doc.getItemValue("planIds");
			Vector<String> planNames = doc.getItemValue("planNames");
			boolean hasNames = (planNames != null && planNames.size() == planIds.size());

			int pos = planIds.indexOf(planId);
			planIds.remove(pos);

			doc.replaceItemValue("planIds", planIds);

			if (hasNames) {
				planNames.remove(pos);
				doc.replaceItemValue("planNames", planNames);
			}

			doc.save();

			Document next = dc.getNextDocument(doc);
			Utils.recycle(doc);
			doc = next;
		}

	}

}
