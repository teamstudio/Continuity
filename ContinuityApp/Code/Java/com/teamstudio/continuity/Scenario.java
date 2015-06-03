package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.MIMEEntity;
import lotus.domino.NotesException;
import lotus.domino.View;

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
					
			OrganisationUnit.saveOrgUnits(xspDocScenario, false);
			
			//save contents of rich text field as string
			doc = xspDocScenario.getDocument(true);
			
			MIMEEntity itemDescription = doc.getMIMEEntity("description_input");
			String description = (itemDescription != null ? itemDescription.getContentAsText() : "");
			
			//create plain text and html version
			xspDocScenario.replaceItemValue("description", com.ibm.xsp.acf.StripTagsProcessor.instance.processMarkup(description) );
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
					dcTasks.stampAll( "orgUnitIds", doc.getItemValue("orgUnitIds") );
					dcTasks.stampAll( "orgUnitNames", doc.getItemValue("orgUnitNames") );
					dcTasks.stampAll( "orgUnitTarget", doc.getItemValue("orgUnitTarget") );
				}
			}

			saved = true;
			
		} catch (Exception e) {
			
			Logger.error(e);
			
		}

		return saved;
		
	}
	
	//Updates the plannames on all scenario documents
	//by combining all plans used from the tasks that are linked to that scenario.
	@SuppressWarnings("unchecked")
	public static void stampWithPlans() {
		
		DocumentCollection dcScenarios = null;
		
		try {
			
			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			dcScenarios = dbCurrent.search("Form=\"fScenario\"");
			
			eu.linqed.debugtoolbar.DebugToolbar.get().debug("stamping " + dcScenarios.getCount() + " scenarios");

			Document docScenario = dcScenarios.getFirstDocument();
			
			View vwTasksByParent = dbCurrent.getView("vwTasksByParent");
			
			while (null != docScenario) {
				
				String scenarioId = docScenario.getItemValueString("id");
				
				Vector<String> planNames = new Vector<String>();
				Vector<String> planIds = new Vector<String>();;
			
				//process the scenario: 
				//- find all tasks in that scenario
				//- get the plan name & plan id from those tasks
				//- stamp the scenario with that information
				
				DocumentCollection dcTasks = vwTasksByParent.getAllDocumentsByKey(scenarioId, true);
				
				Document docTask = dcTasks.getFirstDocument();

				while (null != docTask) {

					Vector<String> planTaskNames = docTask.getItemValue("planNames");
					Vector<String> planTaskIds = docTask.getItemValue("planIds");
					
					planNames.addAll(planTaskNames);
					planIds.addAll(planTaskIds);

					Document docTaskTemp = dcTasks.getNextDocument(docTask);
					docTask.recycle();
					docTask = docTaskTemp;
				}
				
				Utils.recycle(dcTasks);
				
				//make vectors unique, compare with values in document and optionally update
				Collection uniqueNames = new LinkedHashSet(planNames);
				planNames.clear();
				planNames.addAll(uniqueNames);
				
				Collection uniqueIds = new LinkedHashSet(planIds);
				planIds.clear();
				planIds.addAll(uniqueIds);
				
				boolean blnUpdated = false;
				
				if ( !Utils.areListsEqual( planIds, docScenario.getItemValue("planIds") ) ) {
					docScenario.replaceItemValue("planIds", Utils.getUniqueValues(planIds) );
					blnUpdated = true;
				}
				if ( !Utils.areListsEqual( planNames, docScenario.getItemValue("planNames") ) ) {
					docScenario.replaceItemValue("planNames", Utils.getUniqueValues(planNames) );
					blnUpdated = true;
				}
				
				if (blnUpdated) {
					docScenario.save();
				}
				
				Document docTemp = dcScenarios.getNextDocument(docScenario);
				docScenario.recycle();
				docScenario = docTemp;
			}
		} catch (NotesException e) {

			Logger.error(e);
		} finally {
			
			Utils.recycle(dcScenarios);
		
		}
		
	}

}
