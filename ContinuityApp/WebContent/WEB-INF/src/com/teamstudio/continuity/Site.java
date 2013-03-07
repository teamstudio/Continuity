package com.teamstudio.continuity;

import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.NotesException;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.*;

public class Site {

	public boolean remove(com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocContact) {

		boolean success = false;
		Document docSite = null;
		
		try {
			
			Logger.debug("removing site \"" + xspDocContact.getItemValueString("name") + "\"");
			
			docSite = xspDocContact.getDocument(true);
			
			String id = docSite.getItemValueString("id");

			//find all documents that reference this site in the current and core databsae
			removeReferenceFromAllDocs(ExtLibUtil.getCurrentDatabase(), id);
			removeReferenceFromAllDocs(docSite.getParentDatabase(), id);
			
			//remove site document in core database
			docSite.remove(true);
			
			success = true;

		} catch (Exception e) {

			Logger.error(e);

		} finally {
			
		}
		
		return success;
	}
	
	
	private void removeReferenceFromAllDocs(Database dbTarget, String id) {
		
		DocumentCollection dc = null;
		Document doc =  null;
		
		try {
			dc = dbTarget.search( "@IsMember( \"" + id + "\"; siteId) | @IsMember( \"" + id + "\"; siteIds)" );
			
			Document docTemp = null;
			
			doc = dc.getFirstDocument();
			while (null != doc ) {
				
				removeSiteId(doc, id);
				
				doc.save();
				
				Logger.debug("site id/ name removed from document " + doc.getUniversalID() + ", form: " + doc.getItemValueString("form"));
				
				docTemp = dc.getNextDocument(doc);
				doc.recycle();
				doc = docTemp;
			}
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(dc);
			
		}
		
	}
	
	/*
	 * remove the specified id from the siteId(s) fields
	 * if the document has a siteId(s) field: check for a siteName(s) field and remove the value at the
	 * same position as the siteId(s) field.
	 */
	@SuppressWarnings("unchecked")
	private void removeSiteId(Document doc, String id) {
		
		Item item = null;
		Item itemName = null;
		
		try {
			item = doc.getFirstItem("siteId");
			itemName = doc.getFirstItem("siteName");
			
			if (item != null) {
				if (item.containsValue(id) ) {
					Vector<String> values = item.getValues();
					
					//remove site name
					if (itemName != null) {
						int pos = values.indexOf( id );
						Vector<String> valueNames = itemName.getValues();
						valueNames.remove(pos);
						itemName.setValues(valueNames);
						
						itemName.recycle();
					}
					
					values.remove(id);
					item.setValues(values); 
				}
				
				item.recycle();
			}
			
			
			item = doc.getFirstItem("siteIds");
			itemName = doc.getFirstItem("siteNames");
			
			if (item != null) {
				if (item.containsValue(id) ) {
					Vector<String> values = item.getValues();
					
					//remove site name
					if (itemName != null) {
						int pos = values.indexOf( id );
						Vector<String> valueNames = itemName.getValues();
						valueNames.remove(pos);
						itemName.setValues(valueNames);
						
						itemName.recycle();
					}
					
					values.remove(id);
					item.setValues(values); 
				}
				
				item.recycle();
			}
						
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(item);
		}
		
		
	}
}
