package com.teamstudio.continuity;

import java.io.Serializable;

import lotus.domino.Document;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.Database;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class User implements Serializable{

	private static final long serialVersionUID = 280445063966025957L;
	
	private String userName;
	private boolean miniGuideShown;
	
	public User() {
		init(false);
	}
	
	//initialize a user object
	public void init( boolean forceReload ) 
	
	{
		
		Session session = null;
		Database dbCurrent = null;
		View vwContacts = null;
		Document docContact = null;
		
		try {
			
			if (forceReload) { userName = null; }
			
			session = ExtLibUtil.getCurrentSession();
			dbCurrent = ExtLibUtil.getCurrentDatabase();
			
			String currentUser = session.getEffectiveUserName();
			
			if ( userName==null || !currentUser.equals(userName) ) {
				
				userName = currentUser;

				vwContacts = dbCurrent.getView("vwContactsByUsername");
				docContact = vwContacts.getDocumentByKey(userName, true);
				
				miniGuideShown = docContact.getItemValueString("miniGuideShown").equals("true");
				
			}
		
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docContact, vwContacts);
		}
	}
	
	/*public static User get() {
		return (User) Utils.resolveVariable(BEAN_NAME);
	}

	public String getUserName() {
		return userName;
	}
*/
	public boolean isMiniGuideShown() {
		return miniGuideShown;
		
	}
	
	public void setMiniGuideShown() {
		
		View vwContacts = null;
		Document docContact = null;
		
		try {
			
			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			vwContacts = dbCurrent.getView("vwContactsByUsername");
			docContact = vwContacts.getDocumentByKey(userName, true);
				
			docContact.replaceItemValue("miniGuideShown", "true");
			docContact.save();
			
			miniGuideShown = true;
				
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docContact, vwContacts);
		}
	
	}
	
	
}
