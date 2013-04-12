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
		
		View vwAllById = null;
		Document docScenario = null;
		
		try {
			
			session = ExtLibUtil.getCurrentSession();
			dbCurrent = session.getCurrentDatabase();
			
			Document docTask = docTaskUI.getDocument(true);
			
			boolean isNew = docTaskUI.isNewNote();
			
			if (isNew) {
				
				vwAllById = dbCurrent.getView("vwAllById");
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
					
			}
			
			//store categoryName and categoryOrder
			TaskCategory taskCat = TaskCategory.get( docTask.getItemValueString("categoryId") );
			if (null != taskCat) {
				docTask.replaceItemValue("categoryName", taskCat.getName() );
				docTask.replaceItemValue("categoryOrder", taskCat.getOrder() );
				
			}
			
			docTaskUI.save();
			
			if (isNew) {
				
				com.teamstudio.continuity.User.get().updateMenuOptionCounts();
			}
			
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docScenario, vwAllById, dbCurrent);
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
			
			dbCurrent.getView("vwTasksByParent").refresh();
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docTask, dbCurrent, session);
		}
		
	}
	
}
