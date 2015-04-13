package com.teamstudio.continuity;

import java.io.Serializable;

import lotus.domino.Document;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.Database;
import lotus.domino.ViewNavigator;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

public class User implements Serializable{

	private static final long serialVersionUID = 280445063966025957L;
	
	private static final String BEAN_NAME = "currentUserBean";
	
	private String userName;
	private boolean miniGuideShown;
	
	//these values are stored here, so they are updated if a user logs in
	//(if these are in the configuration bean, they aren't which can cause incorrect values if a user creates documents in the mobile app) 
	private int numAssets;
	private int numScenarios;
	private int numTasks;
	private int numPlans;
	private int numResponsibilities;
	private int numQuickGuides;
	private int numIncidents;
	private int numContacts;
	private int numOrgUnits;
	private int numHazards;
	private int numRoles;
	
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
				
				if (null != docContact) {
					miniGuideShown = docContact.getItemValueString("miniGuideShown").equals("true");
				} else {
					miniGuideShown = true;
				}
				
				updateMenuOptionCounts();
				
			}
		
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docContact, vwContacts);
		}
	}
	
	public static User get() {
		return (User) Utils.resolveVariable(BEAN_NAME);
	}

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
			
			if (null != docContact) {
				
				docContact.replaceItemValue("miniGuideShown", "true");
				docContact.save();
			}
			
			miniGuideShown = true;
				
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(docContact, vwContacts);
		}
	
	}
	
	
	public void updateMenuOptionCounts() {
		
		View vwTarget = null;
		
		try {
			
			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			vwTarget = dbCurrent.getView("vwAllByType");
			
			ViewNavigator nav;
			
			nav = vwTarget.createViewNavFromCategory("fSite");
			numAssets = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fRole");
			numRoles = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fHazard");
			numHazards = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fScenario");
			numScenarios = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fTask");
			numTasks = nav.getCount();
			Utils.recycle(nav);

			nav = vwTarget.createViewNavFromCategory("fPlan");
			numPlans = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fResponsibility");
			numResponsibilities = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fQuickGuide");
			numQuickGuides = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fIncident");
			numIncidents = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fContact");
			numContacts = nav.getCount();
			Utils.recycle(nav);
			
			nav = vwTarget.createViewNavFromCategory("fOrgUnit");
			numOrgUnits = nav.getCount();
			Utils.recycle(nav);
			
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			Utils.recycle(vwTarget);
		}
		
	}
	
	public int getNumAssets() {
		return numAssets;
	}
	public int getNumHazards() {
		return numHazards;
	}
	public int getNumRoles() {
		return numRoles;
	}
	public int getNumScenarios() {
		return numScenarios;
	}
	public int getNumTasks() {
		return numTasks;
	}
	public int getNumPlans() {
		return numPlans;
	}
	public int getNumResponsibilities() {
		return numResponsibilities;
	}
	public int getNumQuickGuides() {
		return numQuickGuides;
	}
	public int getNumIncidents() {
		return numIncidents;
	}
	public int getNumContacts() {
		return numContacts;
	}
	public int getNumOrgUnits() {
		return numOrgUnits;
	}
	
	
}
