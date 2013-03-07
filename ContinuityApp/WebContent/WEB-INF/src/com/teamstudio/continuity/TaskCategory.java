package com.teamstudio.continuity;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

/*
 * Class represengint a 'task category' (e.g. "Initial tasks")
 */
public class TaskCategory {

	private String id;
	private String name;
	private int order;
	private String unid;
	
	public static TaskCategory get( String id) {
		
		Database dbCurrent = null;
		View vwAllById = null;
		Document docTaskCat = null;
		
		TaskCategory taskCat = null;
		
		try {
			
			if ( StringUtil.isNotEmpty(id) ) {
				
				dbCurrent = ExtLibUtil.getCurrentDatabase();
				vwAllById = dbCurrent.getView("vwAllByID");
				
				docTaskCat = vwAllById.getDocumentByKey(id, true);
				if (docTaskCat != null) {
					
					taskCat = new TaskCategory();
					taskCat.id = id;
					taskCat.name = docTaskCat.getItemValueString("name");
					taskCat.order = docTaskCat.getItemValueInteger("order");
					taskCat.unid = docTaskCat.getUniversalID();
					
				}
			}
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			
			Utils.recycle(docTaskCat, vwAllById, dbCurrent);
		}
		
		return taskCat;
		
		
	}
	
	public String getId() {
		return id;
	}
	public int getOrder() {
		return order;
	}
	public String getName() {
		return name;
	}
	public String getUnid() {
		return unid;
	}
	
}
