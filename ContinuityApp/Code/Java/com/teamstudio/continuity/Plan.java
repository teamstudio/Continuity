package com.teamstudio.continuity;

import lotus.domino.Document;

import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Logger;

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
				com.teamstudio.continuity.utils.Utils.fieldValueChange("planIds", xspDocPlan.getItemValueString("id"), "planNames", xspDocPlan
						.getItemValueString("name"));
			}

		} catch (Exception e) {
			Logger.error(e);
		}

		return success;

	}

}
