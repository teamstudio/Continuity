package com.teamstudio.continuity;

import com.teamstudio.continuity.utils.Utils;

import eu.linqed.debugtoolbar.DebugToolbar;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.Session;

//helper functions for required data conversion in new versions 
public class Conversion {

	public static void startConversion() {

		try {
			Session session = Utils.getCurrentSessionAsSigner();

			DebugToolbar.get().info("Starting Continuity conversion");

			boolean updated = false;

			Database dbCurrent = session.getCurrentDatabase();

			// updates with incorrect updated field
			DocumentCollection dc = dbCurrent.getAllDocuments();

			DebugToolbar.get().info("- found " + dc.getCount() + " documents in current database");

			Document docTemp = null;

			int numUpdated = 0;

			Document doc = dc.getFirstDocument();
			while (null != doc) {
				
				String form = doc.getItemValueString("form");

				updated = false;

				// update: add authors field and check createdDateMs field
				if (form.equals("fUpdate")) {
					
					if (!doc.hasItem("docAuthors")) {
						
						// set authors field
						Item itAuthors = null;
						
						itAuthors = doc.replaceItemValue("docAuthors", "[bcEditor]");
						itAuthors.setAuthors(true);
						itAuthors.recycle();
						updated = true;

					}

					// make createdDateMs a string
					Item createdDateMs = doc.getFirstItem("createdDateMs");
					if (createdDateMs != null && createdDateMs.getType() == Item.NUMBERS) {
						createdDateMs.setValueString(Double.toString(createdDateMs.getValueDouble()));
						updated = true;
					}
					
					Utils.recycle(createdDateMs);

				} else if (form.equals("fScenario")) {

					// check if field id exists
					if (!doc.hasItem("id")) {
						doc.replaceItemValue("id", "s" + doc.getUniversalID().toLowerCase());
						updated = true;
					}
					
					if ( doc.hasItem("orgUnitId") ) {
						doc.replaceItemValue("orgUnitIds", doc.getItemValueString("orgUnitId"));
						doc.removeItem("orgUnitId");
						updated = true;
					}
					if (doc.hasItem("orgUnitName")) {
						doc.replaceItemValue("orgUnitNames", doc.getItemValueString("orgUnitName"));
						doc.removeItem("orgUnitName");
						updated = true;
					}
					
				} else if (form.equals("fResponsibility")) {

					// check if field id exists
					if (!doc.hasItem("id")) {
						doc.replaceItemValue("id", "r" + doc.getUniversalID().toLowerCase());
						updated = true;
					}

				} else if (form.equals("fIncident")) {

					// check if field id exists
					if (!doc.hasItem("id")) {
						doc.replaceItemValue("id", "i" + doc.getUniversalID().toLowerCase());
						updated = true;
					}

				} else if ( form.equals("fTaskCategory")) {
					
					Item order = doc.getFirstItem("order");
					
					if (order != null && order.getType() != Item.NUMBERS) {
						order.setValueInteger( Integer.parseInt(order.getValueString()) ) ;
						updated = true;
					}
						
					Utils.recycle(order);
					
				} else if (form.equals("fTask")) {
					
					if ( doc.isResponse() ) {
					
						Document docScenario = dbCurrent.getDocumentByUNID( doc.getParentDocumentUNID() );
						
						doc.replaceItemValue("scenarioName", docScenario.getItemValueString("name"));
						doc.replaceItemValue("scenarioId", docScenario.getItemValueString("id"));
						
						doc.removeItem("$Ref");
						
						updated = true;
						
						Utils.recycle(docScenario);
					}
					
					if ( doc.hasItem("responsibilityId") ) {
						
						if (!doc.hasItem("responsibilityIds")) {
							doc.replaceItemValue("responsibilityIds", doc.getItemValueString("responsibilityId") );
						}
						doc.removeItem("responsibilityId");
						updated = true;
					}
					
					if ( doc.hasItem("responsibilityName") ) {
						if (!doc.hasItem("responsibilityNames")) {
							doc.replaceItemValue("responsibilityNames", doc.getItemValueString("responsibilityName") );
						}
						doc.removeItem("responsibilityName");
						updated = true;
					}
					
					if (!doc.hasItem("type")) {
						
						doc.replaceItemValue("type", "one-time");
						updated = true;
						
					}
					
				} else if (form.equals("fCommGuidelines")) {
					
					doc.replaceItemValue("Form", "fQuickGuide");
					doc.replaceItemValue("name", "Communication guidelines");
					
					if (!doc.hasItem("id")) {
						doc.replaceItemValue("id", "q" + doc.getUniversalID().toLowerCase());
					}
					
					updated = true;

				}

				if (!doc.hasItem("createdDateMs")) {

					doc.replaceItemValue("createdDateMs", Long.toString(doc.getCreated().toJavaDate().getTime()));
					updated = true;

				}
				
				if (doc.hasItem("categoryOrder")) {
					Item categoryOrder = doc.getFirstItem("categoryOrder");
					if (categoryOrder != null && categoryOrder.getType() != Item.NUMBERS) {
						categoryOrder.setValueInteger( Integer.parseInt(categoryOrder.getValueString()) ) ;
						updated = true;
					}
					Utils.recycle(categoryOrder);
				}

				if ( doc.hasItem("title") ) {
					doc.replaceItemValue("name", doc.getItemValueString("title") );
					doc.removeItem("title");
					updated = true;
				}
				
				if (updated) {
					
					DebugToolbar.get().info("- updating document, form: " + doc.getItemValueString("form") + ", unid " + doc.getUniversalID());
					
					numUpdated++;
					doc.save();
				}

				docTemp = dc.getNextDocument();
				doc.recycle();
				doc = docTemp;
			}
			
			DebugToolbar.get().info("- finished processing docs, updated " + numUpdated + " docs");
			
			updateACL(dbCurrent);
			
			DebugToolbar.get().info("Finished Continuity conversion");

		} catch (Exception e) {
			DebugToolbar.get().error(e);
		}

	}

	private static void updateACL(Database dbCurrent) throws NotesException {
		boolean updated = false;

		// acl: change bcEditors back to authors
		ACL acl = dbCurrent.getACL();
		ACLEntry aclEntry = acl.getFirstEntry();
		while (aclEntry != null) {

			if (aclEntry.isRoleEnabled("[bcEditor]")
					&& aclEntry.getLevel() == ACL.LEVEL_EDITOR) {
				DebugToolbar.get().info("found editors: " + aclEntry.getName());
				aclEntry.setLevel(ACL.LEVEL_AUTHOR);
				aclEntry.setCanCreateDocuments(true);
				aclEntry.setCanDeleteDocuments(true);
				updated = true;
			}

			aclEntry = acl.getNextEntry();
		}

		if (updated) {
			DebugToolbar.get().info("saving updated acl");
			acl.save();
		}

	}

}
