package com.teamstudio.continuity;

import java.util.Vector;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.acf.StripTagsProcessor;
import com.teamstudio.apps.Unplugged;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import eu.linqed.debugtoolbar.DebugToolbar;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.MIMEEntity;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;

//helper functions for required data conversion in new versions 
public class Conversion {
	
	@SuppressWarnings("unchecked")
	public static void startConversion( View vwAllByType ) {

		DocumentCollection dc = null;
		View vwAllById = null;
		Document docSettings = null;
		
		try {
			
			//need to perform a data conversion first

			DebugToolbar.get().info("Starting Continuity conversion");

			boolean updated = false;
			Document doc  = null;
			String form;
			int numUpdated = 0;
			
			Database dbCurrent = vwAllByType.getParent();
			vwAllById = dbCurrent.getView("vwAllById");
			docSettings = vwAllByType.getDocumentByKey("fSettings", true);
			
			if (docSettings.hasItem("coreDbPath")) {
			
				//move data from (obsolete) Core database to Continuity database
				Conversion.moveCoreDbData(docSettings);
				
				vwAllById.refresh();
			}
			
			//create lists with all orgUnitIds/ orgUnitNames
			Vector<String> allOrgUnitIds = new Vector<String>();
			Vector<String> allOrgUnitNames = new Vector<String>();
			
			View vwOrgUnits = dbCurrent.getView("vwOrgUnits");
			
			ViewEntry veTmp;
			
			ViewEntryCollection vec = vwOrgUnits.getAllEntries();
			ViewEntry veOrgUnit = vec.getFirstEntry();
			while (null != veOrgUnit) {
				
				Vector<String> colValues = veOrgUnit.getColumnValues();
				
				allOrgUnitNames.add( colValues.get(0) );
				allOrgUnitIds.add( colValues.get(2) );

				veTmp = vec.getNextEntry(veOrgUnit);
				veOrgUnit.recycle();
				veOrgUnit = veTmp;
			}
			
			vwOrgUnits.recycle();

			dc = dbCurrent.getAllDocuments();

			DebugToolbar.get().info("- found " + dc.getCount() + " documents in current database");

			Document docTemp = null;

			doc = dc.getFirstDocument();
			while (null != doc) {
				
				form = doc.getItemValueString("form");
				updated = false;
				
				// update: add authors field, check createdDateMs field, add messageHtml field
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
					
					if ( !doc.hasItem("messageHtml") ) {
						doc.replaceItemValue("messageHtml", doc.getItemValueString("message") );
						updated = true;
					}

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
					
					//check if all org unit ids have been filled in the orgUnitIds field
					if (doc.getItemValueString("orgUnitTarget").equals("all")) {
						
						if (doc.getItemValue("orgUnitIds").size() != allOrgUnitIds.size()) {
							doc.replaceItemValue("orgUnitIds", allOrgUnitIds);
							doc.replaceItemValue("orgUnitNames", allOrgUnitNames);
							updated = true;
						}
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
					
					Document docScenario = null;
					
					if ( doc.isResponse() ) {		//convert to non-response
						
						docScenario = dbCurrent.getDocumentByUNID( doc.getParentDocumentUNID() );		//parent = scenario
					
						doc.replaceItemValue("scenarioName", docScenario.getItemValueString("name"));
						doc.replaceItemValue("scenarioId", docScenario.getItemValueString("id"));
						doc.removeItem("$Ref");
					
						updated = true;
					}
				
					if (docScenario == null) {
						docScenario = vwAllById.getDocumentByKey( doc.getItemValueString("scenarioId"), true);
					}
						
					if (null != docScenario) {
						
						String orgUnitTarget = docScenario.getItemValueString("orgUnitTarget");
						
						if (orgUnitTarget.equals("all")) {			//task in a scenarion for all  org units
							
							if (doc.getItemValue("orgUnitIds").size() != allOrgUnitIds.size()) {
								
								doc.replaceItemValue("orgUnitIds", docScenario.getItemValue("orgUnitIds"));
								doc.replaceItemValue("orgUnitNames", docScenario.getItemValue("orgUnitNames"));
								updated = true;
							}
							
						}
						
						if ( !orgUnitTarget.equals( doc.getItemValueString("orgUnitTarget") ) ) {
							doc.replaceItemValue("orgUnitTarget", orgUnitTarget );
							updated = true;
						}
						
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
					
					//add unique list of roleIds (based on the selected responsibilities)
					if ( !doc.hasItem("roleIds") ) {
						
						Vector<String> respIds = doc.getItemValue("responsibilityIds");
						Vector<String> roleIds = new Vector<String>();
						Vector<String> roleNames = new Vector<String>();
						
						for(String respId : respIds) {
							ViewEntry veRes = vwAllById.getEntryByKey( respId, true );
							
							if (veRes != null) {
								
								Document docResp = veRes.getDocument();
								String roleId = docResp.getItemValueString("roleId");
								String roleName = docResp.getItemValueString("roleName");
								
								if (!roleIds.contains(roleId)) {
									roleIds.add(roleId);
									roleNames.add(roleName);
								}
								
								docResp.recycle();
								veRes.recycle();
							}
						}
						
						doc.replaceItemValue("roleIds", roleIds);
						doc.replaceItemValue("roleNames", roleNames);
						updated = true;
					}
					
				} else if (form.equals("fCommGuidelines")) {
					
					doc.replaceItemValue("Form", "fQuickGuide");
					doc.replaceItemValue("name", "Communication guidelines");
					
					if (!doc.hasItem("id")) {
						doc.replaceItemValue("id", "q" + doc.getUniversalID().toLowerCase());
					}
					
					updated = true;
					
					if (doc.hasItem("description_input") && !doc.hasItem("descriptionConverted") ) {
						//convert description to text only field
						
						Item d = doc.getFirstItem("description_input");
						
						if (d.getType() == Item.MIME_PART) {
							d.recycle();
							
							MIMEEntity desc = doc.getMIMEEntity("description_input");
							String text = StripTagsProcessor.instance.processMarkup( (desc != null ? desc.getContentAsText() : "") );
							doc.replaceItemValue("description", text );
							Utils.recycle(desc);
							
						} else if (d.getType() == Item.RICHTEXT) {
							
							RichTextItem rtDesc = (RichTextItem) d;
							
							String text = StripTagsProcessor.instance.processMarkup( rtDesc.getUnformattedText() );
							doc.replaceItemValue("description", text);
									
							Utils.recycle(d);
						
						}
						
						//doc.removeItem("description_input");		//let's leave the old field intact for now
						doc.replaceItemValue("descriptionConverted", "1");
						updated=true;
												
					}
					

				} else if (form.equals("fQuickGuide")) {
					
					if (doc.hasItem("description_input") && !doc.hasItem("descriptionConverted") ) {
						//convert description to text only field
						
						Item d = doc.getFirstItem("description_input");
						
						if (d.getType() == Item.MIME_PART) {
							d.recycle();
							
							MIMEEntity desc = doc.getMIMEEntity("description_input");
							String text = StripTagsProcessor.instance.processMarkup( (desc != null ? desc.getContentAsText() : "") );
							doc.replaceItemValue("description", text );
							Utils.recycle(desc);
							
						} else if (d.getType() == Item.RICHTEXT) {
							
							RichTextItem rtDesc = (RichTextItem) d;
							
							String text = StripTagsProcessor.instance.processMarkup( rtDesc.getUnformattedText() );
							doc.replaceItemValue("description", text);
									
							Utils.recycle(d);
						
						}
						
						//doc.removeItem("description_input");		//let's leave the old field intact for now
						doc.replaceItemValue("descriptionConverted", "1");
						updated=true;
												
					}
					
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

				docTemp = dc.getNextDocument(doc);
				doc.recycle();
				doc = docTemp;
			}
			
			DebugToolbar.get().info("- finished processing docs in Continuity database, updated " + numUpdated + " docs");
			
			updateACL(dbCurrent);
			
			DebugToolbar.get().info("Finished Continuity conversion");

		} catch (Exception e) {
			DebugToolbar.get().error(e);
		} finally {
			
			Utils.recycle(vwAllById, dc, docSettings);
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
	
	private static void moveCoreDbData(Document docSettings) {
		
		Database dbCore = null;
		DocumentCollection dcCoreDocs = null;
		Document docSettingsCore = null;
		
		try {
			
			String coreDbPath = docSettings.getItemValueString("coreDbPath");
			Database dbCurrent = docSettings.getParentDatabase();
			
			
			if (StringUtil.isNotEmpty(coreDbPath)) {
				
				dbCore = dbCurrent.getParent().getDatabase( dbCurrent.getParent().getServerName(), coreDbPath );
				docSettingsCore = dbCore.getView("vwSettings").getFirstDocument();
				
				//cleanup reference to core database in Unplugged config
				if ( Unplugged.deleteApplication(dbCurrent.getParent(), coreDbPath, docSettingsCore.getItemValueString("unpluggedDbPath") ) ) {
					
					DebugToolbar.get().info("Move data from Core database (" + coreDbPath + ") to current");
					
					dcCoreDocs = dbCore.search( "Form != \"fSettings\"");
					
					DebugToolbar.get().info("- found " + dcCoreDocs.getCount() + " documents in core database");
					
					Document docCore = dcCoreDocs.getFirstDocument();
					while (null != docCore) {
						
						Document docTemp = dcCoreDocs.getNextDocument(docCore);
						docCore.copyToDatabase(dbCurrent);
						
						Utils.recycle(docCore);
						docCore = docTemp;
					}
					
					//copy values from settings document in core database to current
					docSettings.replaceItemValue( "organisationId", docSettingsCore.getItemValueString("organisationId") );
					docSettings.replaceItemValue( "organisationName", docSettingsCore.getItemValueString("organisationName") );
					docSettings.replaceItemValue( "unpluggedDbPath", docSettingsCore.getItemValueString("unpluggedDbPath") );
					docSettings.replaceItemValue( "directoryDbPath", docSettingsCore.getItemValueString("directoryDbPath") );
					
					docSettings.replaceItemValue( "docAuthors", Authorizations.ROLE_EDITOR ).setAuthors(true);
					
					//all docs copied: remove core db reference
					docSettings.removeItem("coreDbPath");
					docSettings.save();
					
					
					DebugToolbar.get().info("Finished moving data from Core database");
					
				} else {
					
					Logger.error("error while removing Unplugged database configuration - aborted");
					
				}
				
			}
			
			
		} catch (NotesException e) {
			
			Logger.error(e);
			
		} finally {
			
			Utils.recycle(dcCoreDocs, docSettingsCore, dbCore);
		}
		
	}

}