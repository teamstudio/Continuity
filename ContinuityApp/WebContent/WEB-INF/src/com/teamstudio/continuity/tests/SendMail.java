package com.teamstudio.continuity.tests;

import java.io.Serializable;
import java.util.Date;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.utils.Mail;

public class SendMail implements Test, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Date runAt;
	private String description = "Send an email";
	
	private boolean success;
	private String message;

	public SendMail() {
		this.runAt = null;
	}
	
	public void execute() {
		
		try {
			String to = (String) ExtLibUtil.getViewScope().get("testEmail");
			
			if (StringUtil.isEmpty(to)) {
				success = false;
				message = "Please enter an email address";
			} else {
				Mail m = new Mail();
				
				m.setTo( to );
				m.setSubject("Continuity test message");
				m.addHTML("<p>Test content in the body</p>");
				m.send();
				
				message = "Confirm that a message was received at " + to;
				success = true;
			}
		} catch (Exception e) {
			message = e.getMessage();
			success = false;
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
