package com.teamstudio.continuity.tests;

import java.io.Serializable;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;

import com.ibm.xsp.extlib.util.ExtLibUtil;

public class DatabaseExists implements Test, Serializable {

	private static final long serialVersionUID = 1L;
	
	private Date runAt;
	private String description = "Check if the sessionAsSigner object exists";
	private boolean success;
	private String message;
	
	private String dbPath;
	
	public DatabaseExists( String dbPath, String description) {
		this.runAt = null;
		this.dbPath = dbPath;
		this.description = description;
	}
	
	public void execute() {
		
		try {
			Session s = ExtLibUtil.getCurrentSession();
			
			Database db = s.getDatabase(s.getServerName(), dbPath);
			
			if (db != null && db.isOpen()) {
				message = "";
				this.success = true;
			} else {
				message = "Database " + dbPath + " could not be found";
				success = false;
			}
			
			runAt = new Date();
		} catch (NotesException e) {
			e.printStackTrace();
			success = false;
		}
	}

	public boolean isSuccess() {
		return success;
	}
	public String getMessage() {
		return message;
	}
	public String getDescription() {
		return description;
	}
	

	public Date getRunAt() {
		return runAt;
	}

	public boolean isRun() {
		return (runAt != null);
	}
	
}
