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
	
	public void save( com.ibm.xsp.model.domino.wrapped.DominoDocument docTaskUI, String parentUnid ) {
		
		Session session = null;
		
		try {
			
			session = ExtLibUtil.getCurrentSession();
			dbCurrent = session.getCurrentDatabase();
			
			Document docTask = docTaskUI.getDocument(true);
			
			if (docTaskUI.isNewNote()) {
			
				Document docParent = dbCurrent.getDocumentByUNID(parentUnid);
				
				//copy owner from parent
				if (docParent.hasItem("owner")) {
					docTask.copyItem(docParent.getFirstItem("owner"));
				}
				//copy authors from parent
				if (docParent.hasItem("docAuthors")) {
					docTask.copyItem(docParent.getFirstItem("docAuthors"));
				}
				
				//link to scenario
				docTask.replaceItemValue("scenarioId", docParent.getItemValueString("id"));
				docTask.replaceItemValue("scenarioName", docParent.getItemValueString("name"));
					
			}
			
			//store categoryName and categoryOrder
			TaskCategory taskCat = TaskCategory.get( docTask.getItemValueString("categoryId") );
			if (null != taskCat) {
				docTask.replaceItemValue("categoryName", taskCat.getName() );
				docTask.replaceItemValue("categoryOrder", taskCat.getOrder() );
				
			}
			
			docTaskUI.save();
			
			//store number of tasks in parent
			updateNumTasks(parentUnid);
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(dbCurrent, session);
		}
		
	}
	
	public void remove( String taskUnid ) {
		
		Session session = null;
		Document docTask = null;
		
		try {
			
			session = ExtLibUtil.getCurrentSession();
			dbCurrent = session.getCurrentDatabase();
		
			docTask = dbCurrent.getDocumentByUNID( taskUnid);
			
			String parentUnid = docTask.getParentDocumentUNID();
			
			docTask.remove(true);
			
			dbCurrent.getView("vwTasksByParent").refresh();
			
			//store number of tasks in parent
			updateNumTasks(parentUnid);
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docTask, dbCurrent, session);
		}
		
	}
	
	//update number of tasks in parent
	private void updateNumTasks( String parentUnid ) {
		
		Document docParent = null;
		View vwTasksByParent = null;
		
		try {
			
			vwTasksByParent = dbCurrent.getView("vwTasksByParent");
			vwTasksByParent.refresh();

			docParent = dbCurrent.getDocumentByUNID(parentUnid);
			docParent.replaceItemValue("numTasks", vwTasksByParent.getAllEntriesByKey(parentUnid, true).getCount() );
			docParent.save();
			
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docParent, vwTasksByParent);
		}
		
	}
	
}
