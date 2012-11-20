//creata an object containing all tasks and task categories
//for the specified parent id
//and sort the task by the task category order
function getTasks( parentUnid:String ) {
	
	try {

		
		
		var vec:NotesViewEntryCollection = database.getView("vwTasksByParent").getAllEntriesByKey(parentUnid, true);
	
		var taskCats = [];
	
		var ve:NotesViewEntry = vec.getFirstEntry();
		while (null != ve) {
			
			colValues = ve.getColumnValues();
			
			var name = colValues.get(2);
			var order = colValues.get(6);
			
			var categoryName = colValues.get(3);
			var categoryOrder = colValues.get(4);
			var categoryId = colValues.get(5);
			
			var task = { unid : ve.getUniversalID(), name : name };
			
			var found = false;
			
			for (var i=0; i<taskCats.length; i++) {
				if ( taskCats[i].categoryId == categoryId) {
					taskCats[i].tasks.push(task);
					found = true;
				}
				
			}
			
			if (!found) {
				
				taskCats.push( {
					categoryName : categoryName,
					categoryOrder : categoryOrder,
					categoryId : categoryId,
					tasks : [task]
				} )
				
			}
			
			ve = vec.getNextEntry(ve);
		}
		
		//sort tasks by name
		for (var i=0; i<taskCats.length; i++) {
				
		
		}
		
		taskCats.sortByField("categoryOrder");
		
	} catch (e) {
		
		dBar.error(e);
	}
		
	return taskCats;
}