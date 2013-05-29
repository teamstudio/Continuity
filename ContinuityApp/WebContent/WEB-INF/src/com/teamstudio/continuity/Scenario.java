package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Vector;

import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.MIMEEntity;

import com.ibm.xsp.acf.StripTagsProcessor;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.model.domino.wrapped.DominoDocument;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class Scenario implements Serializable {

	private static final long serialVersionUID = 1593075647180926635L;
	
	public Scenario() { }
	
	@SuppressWarnings("unchecked")
	public boolean save( DominoDocument xspDocScenario ) {

		boolean saved = false;
		Document doc = null;
		
		try {
			boolean blnIsNew = xspDocScenario.isNewNote();

			if (blnIsNew  || xspDocScenario.getItemValueString("id").length() ==0 ) {
				
				//set the authors: [bcEditor]
				doc = xspDocScenario.getDocument(true);
				Utils.setAuthors(doc, Authorizations.ROLE_EDITOR );
			
				if ( xspDocScenario.getItemValueString("id").length() ==0 ) {
					xspDocScenario.replaceItemValue("id", "s" + doc.getUniversalID().toLowerCase() );
				}
			}
			
			HashMap<String,String> orgUnits = (HashMap<String, String>) ExtLibUtil.getApplicationScope().get("orgUnits");
			Vector<String> orgUnitIds = new Vector<String>( orgUnits.keySet() );
			Vector<String> orgUnitNames = new Vector<String>( orgUnits.keySet() );
			
			String orgUnitTarget = xspDocScenario.getItemValueString("orgUnitTarget");
			
			if (orgUnitTarget.equals("all") ) {
				
				xspDocScenario.replaceItemValue("orgUnitIds", orgUnitIds );
				xspDocScenario.replaceItemValue("orgUnitNames", orgUnitNames );
			
			} else {
			
				orgUnitIds.clear();
				orgUnitNames.clear();
				
				orgUnitIds = xspDocScenario.getItemValue("orgUnitIds");
				
				for(String orgUnitId : orgUnitIds ) {
					
					orgUnitNames.add( OrganisationUnit.getName(orgUnitId));
					
				}
				
				xspDocScenario.replaceItemValue("orgUnitNames", orgUnitNames);
			}
			
			//save contents of rich text field as string
			doc = xspDocScenario.getDocument(true);
			
			MIMEEntity itemDescription = doc.getMIMEEntity("description_input");
			String description = (itemDescription != null ? itemDescription.getContentAsText() : "");
			
			//create plain text and html version
			xspDocScenario.replaceItemValue("description", StripTagsProcessor.instance.processMarkup(description) );
			xspDocScenario.replaceItemValue("descriptionHtml", description );
			xspDocScenario.save();
			
			if (blnIsNew) {
				
				com.teamstudio.continuity.User.get().updateMenuOptionCounts();
			
			} else {
			
				String scenarioId = xspDocScenario.getItemValueString("id");
							
				//replace this name everywhere
				Utils.fieldValueChange("scenarioId", scenarioId, "scenarioName", xspDocScenario.getItemValueString("name") );
				
				//copy org units to all tasks in this scenario
				DocumentCollection dcTasks = ExtLibUtil.getCurrentDatabase().search( "Form=\"fTask\" & scenarioId=\"" + scenarioId + "\"");
				
				if (dcTasks.getCount()>0) {
					dcTasks.stampAll( "orgUnitIds", orgUnitIds );
					dcTasks.stampAll( "orgUnitNames", orgUnitNames );
					dcTasks.stampAll( "orgUnitTarget", orgUnitTarget );
				}
			}

			saved = true;
			
		} catch (Exception e) {
			
			Logger.error(e);
			
		}

		return saved;
		
	}

}
