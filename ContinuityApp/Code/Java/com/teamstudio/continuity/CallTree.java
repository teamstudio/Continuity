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

	//remove a contact (and all children) from a calltree
	@SuppressWarnings("unchecked")
	public boolean removeContact(String unid) {

		boolean succes = false;
		Document docToRemove = null;
		vwAllById = null;

		try {

			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			vwAllById = dbCurrent.getView("vwAllById");
			
			//document of the contact to remove
			docToRemove = dbCurrent.getDocumentByUNID(unid);
			String id = docToRemove.getItemValueString("id");
			String name = docToRemove.getItemValueString("name");

			//find 'parent' contact (if any)
			// Logger.debug("remove user: " + compositeData.entry.userName + // " with id" + compositeData.entry.id);
			String q = "Form=\"fContact\" & @IsMember( \"" + id	+ "\"; callTreeContacts)";
			DocumentCollection dc = ExtLibUtil.getCurrentDatabase().search(q);

			Logger.debug("found " + dc.getCount() + " docs (query: " + q + ")");

			if (dc.getCount() > 0) {
				
				Document doc = dc.getFirstDocument();

				Logger.debug("- removing " + name + " " + id + " from contacts of " + doc.getItemValueString("name") );
				if ( Utils.removeItemValue(doc, "callTreeContacts", id) ) {
					doc.save();
				}
			
				doc.recycle();
				dc.recycle();
			}

			// remove 'parent' contact from callTreeCalledBy field
			if (docToRemove != null) {
				
				Logger.debug("- removing " + name + " from calltree");
				
				Vector<String> childContacts = docToRemove.getItemValue("callTreeContacts");
				
				CallTree.saveRemoveItem(docToRemove, "callTreeCalledBy");
				CallTree.saveRemoveItem(docToRemove, "callTreeContacts");
				CallTree.saveRemoveItem(docToRemove, "callTreeRoot");

				docToRemove.save();
				docToRemove.recycle();
				
				removeChildrenFromCallTree(childContacts);
			}

		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docToRemove);
		}

		return succes;

	}
	
	//remove all children from the call tree, function is caled recursively
	@SuppressWarnings("unchecked")
	private void removeChildrenFromCallTree(Vector<String> contacts) {
		
		try {
			
			for(String contactId : contacts) {
				
				Document docContact = vwAllById.getDocumentByKey(contactId, true);
				if (null != docContact) {
					
					Logger.debug("- removing " + docContact.getItemValueString("name") + " from calltree");
					
					Vector<String> childContacts = docContact.getItemValue("callTreeContacts");
					
					CallTree.saveRemoveItem(docContact, "callTreeCalledBy");
					CallTree.saveRemoveItem(docContact, "callTreeContacts");
					CallTree.saveRemoveItem(docContact, "callTreeRoot");
					docContact.save();
					docContact.recycle();
					
					removeChildrenFromCallTree(childContacts);
					
				}
				
			}
		} catch (NotesException e) {
			Logger.error(e);
		}
		
	}
	
	private static void saveRemoveItem(Document doc, String itemName) throws NotesException {
		if (doc.hasItem(itemName)) {
			doc.removeItem(itemName);
		}
	
	}

}
