package com.teamstudio.continuity.tests;

import java.io.Serializable;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.Configuration;


public class CanCreateUsers implements Test, Serializable {

	private static final long serialVersionUID = 1L;
	
		private Date runAt;
		private String description = "Create a user in the user directory";
		private boolean success;
		private String message;
		
		public CanCreateUsers() {
			this.runAt = null;
		}
		
		public void execute() {
			
			Document doc = null;
			
			try {
				Session s = ExtLibUtil.getCurrentSession();
				
				//open the directory database, create a user (person document) and remove it again
				Configuration config = Configuration.get();
				
				Database db = s.getDatabase(s.getServerName(), config.getDirectoryDbPath());
				
				if (db != null && db.isOpen()) {
					
					//directory database opened
					
					doc = db.createDocument();
					doc.replaceItemValue("Form", "Person");
					doc.replaceItemValue("Type", "Person");
					doc.replaceItemValue("LastName", "Testscript " + (new Date()).getTime() );
					doc.computeWithForm(true, true);
					
					success = doc.save();
					message = "";
					
				} else {
					success = false;
				}
				
				runAt = new Date();
			} catch (NotesException e) {
				e.printStackTrace();
				message = "Error: " + e.getMessage();
				success = false;
			} finally {
				
				try {
					if (doc != null && !doc.isNewNote()) {
						doc.removePermanently(true);
					}
				} catch (NotesException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
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
