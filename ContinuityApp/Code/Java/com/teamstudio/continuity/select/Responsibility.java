package com.teamstudio.continuity.select;

import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.View;
import lotus.domino.ViewEntry;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Logger;

public class Responsibility {

	/*
	 * Save the selected responsibilities to a target document (selected in the 'assign to responsibility' modal)
	 */
	public static boolean saveToDocument(boolean fromList, DominoDocument docTarget, Vector<String> selected) {

		boolean success = false;

		try {
			
			Database db = docTarget.getParentDatabase();

			docTarget.replaceItemValue("responsibilityIds", selected);

			Vector<String> respNames = new Vector<String>();
			Vector<String> roleIds = new Vector<String>();
			Vector<String> roleNames = new Vector<String>();

			if (selected != null) {

				View vwAllById = db.getView("vwAllById");

				for (String sel : selected) {
					if (StringUtil.isNotEmpty(sel)) {

						//check wether a role was selected or a responsibility

						if (sel.indexOf("role") == 0) {

							//assigned to a role: get the role name
							String roleId = sel.substring(5);

							ViewEntry ve = vwAllById.getEntryByKey(roleId, true);
							if (null != ve) {

								String roleName = (String) ve.getColumnValues().get(1);

								roleIds.add(roleId);
								roleNames.add(roleName);
								respNames.add(roleName);

								ve.recycle();
							}

						} else {

							//assigned to a responsibility		
							ViewEntry ve = vwAllById.getEntryByKey(sel, true);

							if (null != ve) {
								Document docResp = ve.getDocument();

								String roleId = docResp.getItemValueString("roleId");
								String roleName = docResp.getItemValueString("roleName");

								if (!roleIds.contains(roleId)) {
									roleIds.add(roleId);
									roleNames.add(roleName);
								}

								respNames.add(roleName + " - " + docResp.getItemValueString("name"));

								docResp.recycle();
								ve.recycle();
							}
						}
					}
				}
			}

			docTarget.replaceItemValue("roleIds", roleIds);
			docTarget.replaceItemValue("roleNames", roleNames);
			docTarget.replaceItemValue("responsibilityNames", respNames);

			if (fromList) {
				docTarget.save();

				//update tasks by plan view to immediately reflect changes in 'plans' menu option
				View v = db.getView("vwTasksByPlanId");
				v.refresh();
			}

			success = true;

		} catch (Exception e) {
			Logger.error(e);
		}

		return success;

	}
}
