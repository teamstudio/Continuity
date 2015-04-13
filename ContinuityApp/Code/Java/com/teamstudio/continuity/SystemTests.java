package com.teamstudio.continuity;

import java.io.Serializable;
import java.util.ArrayList;

import com.teamstudio.continuity.tests.CanCreateUsers;
import com.teamstudio.continuity.tests.DatabaseExists;
import com.teamstudio.continuity.tests.SendMail;
import com.teamstudio.continuity.tests.SessionAsSigner;
import com.teamstudio.continuity.tests.Test;

/*
 * Set of functions that can be used to check if the current application is setup and configured correctly.
 */

public class SystemTests implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private ArrayList<Object> tests;

	public SystemTests() {
		
		Configuration config = Configuration.get();
		
		this.tests = new ArrayList<Object>();
		
		tests.add( new SessionAsSigner() );
		tests.add( new SendMail() );	
		tests.add( new DatabaseExists( config.getDirectoryDbPath(), "Directory database exists" )) ;
		tests.add( new DatabaseExists( config.getUnpluggedDbPath(), "Unplugged server admin database exists" )) ;
		tests.add( new CanCreateUsers()) ;
	}
	
	public ArrayList<Object> getTests() {
		return tests;
	}
	
	public void executeAll() {
		for (Object t : tests) {
			((Test) t).execute();
		}
	}

}
