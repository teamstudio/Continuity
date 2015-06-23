package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.Vector;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
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
	public Role(String roleId) {

		Database dbCurrent = null;
		View vwAllById = null;
		Document docRole = null;

		try {

			if (StringUtil.isNotEmpty(roleId) && !roleId.equals("none")) {

				dbCurrent = ExtLibUtil.getCurrentDatabase();
				vwAllById = dbCurrent.getView("vwAllByID");

				docRole = vwAllById.getDocumentByKey(roleId, true);
				if (docRole != null) {
					this.id = roleId;
					this.name = docRole.getItemValueString("name");
					this.appMenuOptions = docRole.getItemValueString("appMenuOptions");
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
	public boolean save(com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocRole) {

		boolean success = false;

		try {

			boolean isNew = xspDocRole.isNewNote();

			if (isNew) {
				xspDocRole.replaceItemValue("id", "r" + xspDocRole.getDocument().getUniversalID().toLowerCase());
				xspDocRole.replaceItemValue("docAuthors", Authorizations.ROLE_EDITOR);

				Document doc = xspDocRole.getDocument(true);
				doc.getFirstItem("docAuthors").setAuthors(true);
			}

			xspDocRole.save();

			this.id = xspDocRole.getItemValueString("id");
			this.name = xspDocRole.getItemValueString("name");
			this.appMenuOptions = xspDocRole.getItemValueString("appMenuOptions");
			this.appMenuOptionsActive = xspDocRole.getItemValue("appMenuOptionsActive");

			//update role name in all documents that use this role (e.g. contacts)
			Utils.fieldValueChange("roleId", id, "roleName", name);

			success = true;

			com.teamstudio.continuity.User.get().updateMenuOptionCounts();

		} catch (Exception e) {

			Logger.error(e);

		} finally {

		}

		return success;

	}

	/*
	 * Delete a role from the system, including references
	 */
	public static boolean remove(DominoDocument docRole) {

		boolean success = false;

		try {

			Logger.info("remove role: " + docRole.getItemValueString("name"));

			String roleId = docRole.getItemValueString("id");

			//remove all responsibilities DocumentCollection dcResp =
			DocumentCollection dcResp = docRole.getParentDatabase().search("Form=\"fResponsibility\" & roleId=\"" + roleId + "\"");

			Document docResp = dcResp.getFirstDocument();
			while (docResp != null) {
				Document docTemp = dcResp.getNextDocument(docResp);
				
				String id = docResp.getItemValueString("id");

				Logger.info("removing responsibility: " + docResp.getItemValueString("name"));
				
				removeResponsibilityReference(id);

				docResp.remove(true);
				docResp.recycle();

				docResp = docTemp;
			}

			//remove role from all referenced document (e.g. tasks)
			removeRoleReferences(roleId);

			Logger.debug("removing role " + docRole.getItemValueString("name"));
			docRole.getDocument().remove(true);

			com.teamstudio.continuity.User.get().updateMenuOptionCounts();

			success = true;

		} catch (Exception e) {

			Logger.error(e);
		}

		return success;

	}

	@SuppressWarnings("unchecked")
	private static void removeRoleReferences(String roleId) throws NotesException {

		//find tasks in which this responsibility is listed and remove it
		DocumentCollection dc = ExtLibUtil.getCurrentDatabase().search("Form=\"fTask\" & @IsMember(\"" + roleId + "\"; roleIds)");

		Logger.debug("- found " + dc.getCount() + " related documents to remove role from");

		Document doc = dc.getFirstDocument();
		while (null != doc) {

			Vector<String> roleIds = doc.getItemValue("roleIds");
			Vector<String> roleNames = doc.getItemValue("roleNames");
			boolean hasNames = (roleNames != null && roleNames.size() == roleIds.size());

			int pos = roleIds.indexOf(roleId);
			roleIds.remove(pos);

			doc.replaceItemValue("roleIds", roleIds);

			if (hasNames) {
				roleNames.remove(pos);
				doc.replaceItemValue("roleNames", roleNames);
			}

			doc.save();

			Document next = dc.getNextDocument(doc);
			Utils.recycle(doc);
			doc = next;
		}

		removeResponsibilityReference("role-" + roleId);

	}

	@SuppressWarnings("unchecked")
	private static void removeResponsibilityReference(String respId) throws NotesException {
		DocumentCollection dc = ExtLibUtil.getCurrentDatabase().search("Form=\"fTask\" & @IsMember(\"" + respId + "\"; responsibilityIds)");

		Logger.debug("- found " + dc.getCount() + " related documents to remove role from");

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
