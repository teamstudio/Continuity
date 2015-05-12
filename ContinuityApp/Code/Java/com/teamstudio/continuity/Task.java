package com.teamstudio.continuity;

import java.io.Serializable;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class Task implements Serializable {

	private static final long serialVersionUID = 4523205950146076129L;
	
	private transient Database dbCurrent = null;
	
	public Task() {
		
	}
	
	public void save( com.ibm.xsp.model.domino.wrapped.DominoDocument docTaskUI, String parentId ) {
		
		Session session = null;
		
		Document docScenario = null;
		
		try {
			
			session = ExtLibUtil.getCurrentSession();
			dbCurrent = session.getCurrentDatabase();
			
			Document docTask = docTaskUI.getDocument(true);
				
			View vwAllById = dbCurrent.getView("vwAllById");
			
			if (parentId == null || parentId.length()==0  ) {
				parentId = docTaskUI.getItemValueString("scenarioId");
			}
			
			docScenario = vwAllById.getDocumentByKey(parentId, true);
			
			if (null != docScenario) {
			
				//copy owner from parent
				if (docScenario.hasItem("owner")) {
					docTask.copyItem(docScenario.getFirstItem("owner"));
				}
				//copy authors from parent
				if (docScenario.hasItem("docAuthors")) {
					docTask.copyItem(docScenario.getFirstItem("docAuthors"));
				}
				
				//link to scenario
				docTask.replaceItemValue("scenarioId", docScenario.getItemValueString("id"));
				docTask.replaceItemValue("scenarioName", docScenario.getItemValueString("name"));
				
				//copy org unit settings
				docTask.replaceItemValue("orgUnitIds", docScenario.getItemValue("orgUnitIds"));
				docTask.replaceItemValue("orgUnitNames", docScenario.getItemValue("orgUnitNames"));
				docTask.replaceItemValue("orgUnitTarget", docScenario.getItemValueString("orgUnitTarget") );
				
				docScenario.recycle();

			} else {
			
				Logger.warn("parent document not found with id " + parentId);
			
			}
			
			vwAllById.recycle();
			
			//store categoryName and categoryOrder
			TaskCategory taskCat = TaskCategory.get( docTask.getItemValueString("categoryId") );
			if (null != taskCat) {
				docTask.replaceItemValue("categoryName", taskCat.getName() );
				docTask.replaceItemValue("categoryOrder", taskCat.getOrder() );
				
			}
			
			docTaskUI.save();
			
			//stamp all scenarios with all plannames
			Scenario.stampWithPlans();
			
			com.teamstudio.continuity.User.get().updateMenuOptionCounts();
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docScenario, dbCurrent);
		}
		
	}
	
	public void remove( String taskUnid ) {
		
		Session session = null;
		Document docTask = null;
		
		try {
			
			session = ExtLibUtil.getCurrentSession();
			dbCurrent = session.getCurrentDatabase();
			
			docTask = dbCurrent.getDocumentByUNID( taskUnid);
			
			docTask.remove(true);
			
			com.teamstudio.continuity.User.get().updateMenuOptionCounts();
			
			View vwTasksByParent = dbCurrent.getView("vwTasksByParent");
			
			vwTasksByParent.refresh();
			
			Utils.recycle(vwTasksByParent);
			
			//stamp all scenarios with all plannames
			Scenario.stampWithPlans();
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docTask, dbCurrent, session);
		}
		
	}
	
}
