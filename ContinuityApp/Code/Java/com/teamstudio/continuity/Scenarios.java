package com.teamstudio.continuity;

import java.util.*;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;

import com.teamstudio.continuity.utils.Utils;
import com.ibm.xsp.extlib.util.*;

import lotus.domino.*;

public class Scenarios {

	@SuppressWarnings("unchecked")
	public static List<SelectItem> getGroupedComboboxOptions() {

	    List<SelectItem> groupedOptions = new ArrayList<SelectItem>();
	    
	    try {
			Database db = ExtLibUtil.getCurrentDatabase();
			View vw = db.getView("vwScenarios");
			ViewNavigator nav = vw.createViewNav();
			
			SelectItemGroup group = null;
			ArrayList<SelectItem> aGroupOptions = new ArrayList<SelectItem>();
			
			groupedOptions.add( new SelectItem("", "Select a scenario"));
			
			ViewEntry ve = nav.getFirst();
			while (ve != null) {
				
				Vector<Object> colValues = ve.getColumnValues();
				
				if (ve.isCategory()) {
					
					if (group != null) {
						 group.setSelectItems(aGroupOptions.toArray(new SelectItem[aGroupOptions.size()]));
						 aGroupOptions.clear();
						 groupedOptions.add(group);
					}
					
					String cat = (String) colValues.get(0);
					group = new SelectItemGroup(cat);
					
				} else {
					
					String l = (String) colValues.get(1);
					String v = (String) colValues.get(3);		//scenario id
					
					aGroupOptions.add(new SelectItem(v, l) );
					
				}
				
				Utils.recycle(colValues);
				
				ViewEntry next = nav.getNext();
				ve.recycle();
				ve = next;
			}
			
			if (group != null) {
				 group.setSelectItems(aGroupOptions.toArray(new SelectItem[aGroupOptions.size()]));
				 aGroupOptions.clear();
				 groupedOptions.add(group);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	   
	    return groupedOptions;
	    
	}
}
