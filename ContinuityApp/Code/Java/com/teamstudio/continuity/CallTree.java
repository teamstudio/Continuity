package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.View;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class CallTree implements Serializable {

	private static final long serialVersionUID = 1L;

	private transient View vwAllById;

	//remove a contact from a calltree, including all children
	@SuppressWarnings("unchecked")
	public boolean removeContact(String orgUnitId, String unid) {

		boolean succes = false;
		Document docToRemove = null;
		vwAllById = null;

		try {

			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			vwAllById = dbCurrent.getView("vwAllById");

			//document of the contact to remove
			docToRemove = dbCurrent.getDocumentByUNID(unid);
			if (docToRemove == null) {
				throw (new Exception("invalid unid"));
			}

			String id = docToRemove.getItemValueString("id");
			String name = docToRemove.getItemValueString("name");

			Logger.debug("- removing " + name + " from calltree");

			//find 'parent' contact (if any) and remove the current user (the contact that this user should be called by)
			String q = "Form=\"fContact\" & @IsMember( \"" + orgUnitId + "-" + id + "\"; callTreeContacts)";
			DocumentCollection dc = ExtLibUtil.getCurrentDatabase().search(q);

			Logger.debug("found " + dc.getCount() + " docs (query: " + q + ")");

			if (dc.getCount() > 0) {

				Document docParent = dc.getFirstDocument();
				CallTree.removeByPrefix(orgUnitId + "-" + id , docParent, "callTreeContacts");
				docParent.save();
				Utils.recycle(docParent, dc);

			}

			//find all child contacts (contacts that this user needs to call) and 
			//remove him/her from the callTreeCalledBy field,
			//also removing any children of any contact found (the rest of the call tree starting from this user and based on
			//the call tree for a specific org unit	
			Vector<String> childContacts = docToRemove.getItemValue("callTreeContacts");

			CallTree.removeByPrefix(orgUnitId + "-", docToRemove, "callTreeCalledBy");
			CallTree.removeByPrefix(orgUnitId + "-", docToRemove, "callTreeContacts");
			CallTree.removeByPrefix(orgUnitId + "-", docToRemove, "callTreeRoot");

			docToRemove.save();
			docToRemove.recycle();

			removeFromCallTree(orgUnitId, childContacts);

		} catch (Exception e) {
			Logger.error(e);
		} finally {

			Utils.recycle(docToRemove);
		}

		return succes;

	}

	@SuppressWarnings("unchecked")
	private static boolean removeByPrefix(String prefix, Document doc, String fieldName) throws NotesException {

		Vector<String> currentEntries = doc.getItemValue(fieldName);
		Vector<String> newEntries = new Vector<String>();

		boolean removed = false;

		for (int i = 0; i < currentEntries.size(); i++) {

			if (currentEntries.get(i).startsWith(prefix)) {
				removed = true;
			} else {
				newEntries.add(currentEntries.get(i));
			}

		}

		if (removed) {
			doc.replaceItemValue(fieldName, newEntries);
			return true;
		} else {
			return false;
		}

	}

	//remove all children from the call tree, function is called recursively
	@SuppressWarnings("unchecked")
	private void removeFromCallTree(String orgUnitId, Vector<String> callTreeEntries) throws NotesException {

		for (String callTreeEntry : callTreeEntries) {

			String[] comps = callTreeEntry.split("-");
			String _orgUnitId = comps[0];
			String _contactId = comps[1];

			if (_orgUnitId.equalsIgnoreCase(orgUnitId)) {

				Document docContact = vwAllById.getDocumentByKey(_contactId, true);

				if (docContact != null) {

					Logger.debug("- removing " + docContact.getItemValueString("name") + " from calltree");

					Vector<String> childEntries = docContact.getItemValue("callTreeContacts");

					CallTree.removeByPrefix(orgUnitId + "-", docContact, "callTreeCalledBy");
					CallTree.removeByPrefix(orgUnitId + "-", docContact, "callTreeContacts");
					CallTree.removeByPrefix(orgUnitId + "-", docContact, "callTreeRoot");

					docContact.save();
					docContact.recycle();

					removeFromCallTree(orgUnitId, childEntries);
				}

			}
		}

	}

}
