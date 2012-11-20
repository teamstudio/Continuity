package com.teamstudio.continuity;

import javax.faces.context.FacesContext;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class Utils {
	
	//open the current installation's directory database (with signer access)
	public static Database getDirectory() {

		try {
			
			Database dbCurrent = (Database) Utils.resolveVariable("database");
			Session session = (Session) Utils.resolveVariable("sessionAsSigner");
			
			String server = dbCurrent.getServer();
			String dbDir = "names.nsf";
					
			Database dbDirectory = session.getDatabase(server, dbDir);
			
			dbDirectory.setDelayUpdates(false);
			
			return dbDirectory;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;

	}

	// resolves a variable
	public static Object resolveVariable( String name ) {
		FacesContext facesContext = FacesContext.getCurrentInstance();
		return facesContext.getApplication().getVariableResolver().resolveVariable(facesContext, name);
	}

	// recycle all objects in the arguments (in that order)
	public static void recycle(Object... dominoObjects) {
		for (Object dominoObject : dominoObjects) {
			if (null != dominoObject) {
				if (dominoObject instanceof Base) {
					try {
						((Base) dominoObject).recycle();
					} catch (NotesException ne) {
						// not interested in errors here
					}
				}
			}
		}
	}

}
