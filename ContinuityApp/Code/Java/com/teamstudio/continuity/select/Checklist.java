package com.teamstudio.continuity.select;

import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.ViewEntry;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Logger;

public class Checklist {

	public static boolean saveToDocument(boolean fromList, DominoDocument docTarget, Vector<String> planIds) {

		boolean success = false;

		try {
			//update tasks

			Database database = docTarget.getParentDatabase();

			//get plan names

			Vector<String> planNames = new Vector<String>();

			View vwAllById = database.getView("vwAllById");

			for (String planId : planIds) {

				if (StringUtil.isNotEmpty(planId)) {

					ViewEntry vePlan = vwAllById.getEntryByKey(planId, true);

					String planName = "error: can't find plan";

					if (vePlan != null) {
						planName = (String) vePlan.getColumnValues().get(1);
					}

					planNames.add(planName);
				}
			}

			docTarget.replaceItemValue("planIds", planIds);
			docTarget.replaceItemValue("planNames", planNames);
			
			if (fromList) {
				docTarget.save();
			}

			//stamp all scenarios	
			com.teamstudio.continuity.Scenario.stampWithPlans();

			//clear cached session scope variable: gets re-added when opening the plans section
			ExtLibUtil.getSessionScope().remove("plans");

			//update tasks by plan view to immediately reflect changes in 'plans' menu option
			View v = database.getView("vwTasksByPlanId");
			v.refresh();

			success = true;

		} catch (Exception e) {
			Logger.error(e);
		}

		return success;
	}

}
