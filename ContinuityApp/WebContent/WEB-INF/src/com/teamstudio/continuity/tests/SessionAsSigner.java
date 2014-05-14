package com.teamstudio.continuity.tests;

import java.io.Serializable;
import java.util.Date;

import lotus.domino.Session;

import com.ibm.xsp.extlib.util.ExtLibUtil;

public class SessionAsSigner implements Test, Serializable {

	private static final long serialVersionUID = 1L;
	
	private Date runAt;
	private String description = "Check if the sessionAsSigner object exists";
	private boolean success;
	private String message;
	
	public SessionAsSigner() {
		this.runAt = null;
	}
	
	public void execute() {
		
		Session s = ExtLibUtil.getCurrentSessionAsSigner();
		
		if (s == null) {
			message = "sessionAsSigner object could not be found: check if this database has been signed correctly";
			success = false;
		} else {
			message = "";
			this.success = true;
		}
		
		runAt = new Date();
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
