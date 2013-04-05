package com.teamstudio.continuity.utils;

import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.teamstudio.continuity.Configuration;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class Utils {
	
	public static final Pattern rfc2822 = Pattern.compile(
	        "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$"
	);
	
	/*
	 * Custom function to get a sessionAsSigner object that will do a fallback to the signer 
	 * object if the sessionAsSigner object could not be found.
	 * That situation occurs if not all design elements are signed with the same Notes ID.
	 * The function will (once per session) show a message to the server console about this 'error'
	 */ 
	public static Session getCurrentSessionAsSigner() {
		
		Session s = ExtLibUtil.getCurrentSessionAsSigner();
		
		if (s == null) {
			s = ExtLibUtil.getCurrentSession();
			displayNoSessionAsSignerMessage(s);
		}
		
		return s;		
	}
	
	//print a message to the console (once per session) indicating that the sessionAsSigner
	//object could not be retrieved
	private static void displayNoSessionAsSignerMessage(Session s) {
		try {
			Map<String, Object> sessionScope = ExtLibUtil.getSessionScope();
		
			if (!sessionScope.containsKey("sasMessageShown")) {
				sessionScope.put("sasMessageShown", true);
				System.out.println("sessionAsSigner object not found, using session object (for user " + s.getEffectiveUserName() + " in database " + Configuration.get().getContinuityDbPath() + ")" );
			}
		} catch (NotesException e) { }
	}
	
	//open the current installation's directory database (with signer access)
	public static Database getDirectory() throws NotesException {

		Configuration config = Configuration.get();
		
		Session sessionAsSigner = Utils.getCurrentSessionAsSigner();
				
		Database dbDirectory = sessionAsSigner.getDatabase(config.getServerName(), config.getDirectoryDbPath());
		dbDirectory.setDelayUpdates(false);
		
		return dbDirectory;
	}
	
	//returns a random string with the specified length from the set a..z, A..Z and 0..9 (excluding a few characters)
	public static String getRandomString( int length, boolean allowSpecialChars ) {
		
		//character set to generate random value from
		//note: lowercase l and uppercase I are not available
		String chars = "123456789ABCDEFGHJKLMNPQRSTUVWXTZabcdefghkmnpqrstuvwxyz";
		String specialChars = "!@#$%&-+";
		
		if (allowSpecialChars) {
			chars += specialChars;
		}
		
		StringBuffer randomString = new StringBuffer();
		for (int i=0; i<length; i++) {
			int rnum = new Double( Math.floor(Math.random() * chars.length()) ).intValue();
			randomString.append( chars.substring(rnum,rnum+1) );
		}
			
		return randomString.toString();
		
	}

	// resolves a variable
	public static Object resolveVariable( String name ) {
		FacesContext facesContext = FacesContext.getCurrentInstance();
		return facesContext.getApplication().getVariableResolver().resolveVariable(facesContext, name);
	}
	
	/*
	 * update a field value based on documents found using a match field/- value
	 * 
	 * This function is used to change names in related documents based on an id 
	 */
	public static void fieldValueChange( String matchField, String matchValue, String fieldName, String fieldValue) {
		try {
			
			Session sessionAsSigner = Utils.getCurrentSessionAsSigner();
			Database dbCurrent = sessionAsSigner.getCurrentDatabase();
			
			fieldValueChange(dbCurrent, matchField, matchValue, fieldName, fieldValue );
			
		} catch (NotesException e) {
			Logger.error(e);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static void fieldValueChange( Database dbTarget, String matchField, String matchValue, String fieldName, String fieldValue) {
		
		DocumentCollection dc = null;
		
		try {
			
			//Logger.debug("set new value " + fieldValue + " in field " + fieldName + " by matching " + matchValue + " in " + matchField);
			
			dc = dbTarget.search( "@IsMember(\"" + matchValue + "\"; " + matchField + ")");
			
			//Logger.debug("found " + dc.getCount() + " documents in " + dbTarget.getFilePath() );
			
			Document docTemp;
			Item fld;
			
			if (dc.getCount()>0) {
				
				Document doc = dc.getFirstDocument();
				while (null != doc) {
					
					fld = doc.getFirstItem(fieldName);
					
					if (fld != null) {
						
						Vector<String> matchValues = doc.getItemValue(matchField);
						
						int pos = matchValues.indexOf(matchValue);
						
						if (pos>-1) {
							
							Vector<String> fieldValues = fld.getValues();
							
							if (!fieldValues.get(pos).equals(fieldValue) ) {

								fieldValues.set(pos, fieldValue);
								
								fld.setValues(fieldValues);
								doc.save();
								
								//Logger.debug("- saved " + doc.getItemValueString("name"));

							}
						}
						
						fld.recycle();
						
					} else {
						
						doc.replaceItemValue(fieldName, fieldValue);
						doc.save();
						
					}
					
					docTemp = dc.getNextDocument(doc);
					doc.recycle();
					doc = docTemp;
				}
				
				
			}
			
			//Logger.debug("done");
		} catch (NotesException e) {
			Logger.error(e);
			
		} finally {
			Utils.recycle(dc);
			
		}

	}
	
	//add an message to the current context
	public static void addInfoMessage( String message) {
		FacesContext.getCurrentInstance().addMessage(null, 
			new FacesMessage( FacesMessage.SEVERITY_INFO, message, message) );
	}
	public static void addErrorMessage( String message) {
		FacesContext.getCurrentInstance().addMessage(null, 
			new FacesMessage( FacesMessage.SEVERITY_ERROR, message, message) );
	}
	public static void addWarningMessage( String message) {
		FacesContext.getCurrentInstance().addMessage(null, 
			new FacesMessage( FacesMessage.SEVERITY_WARN, message, message) );
	}

	// recycle all objects in the arguments (in that order)
	public static void recycle(Object... dominoObjects) {
		for (Object dominoObject : dominoObjects) {
			if (null != dominoObject) {
				if (dominoObject instanceof Base) {
					try {
						((Base) dominoObject).recycle();
					} catch (Exception ne) {
						// not interested in errors here
					}
				}
			}
		}
	}
	
	//safe methods (with Notesobject cleanup) to set authors/ readers/ dates
	public static void setAuthors(Document doc, String author) {
		Utils.setAuthors(doc, author, "docAuthors");
	}
	public static void setAuthors(Document doc, Vector<String> authors) {
		Utils.setAuthors(doc, authors, "docAuthors");
	}
	public static void setAuthors( Document doc, String author, String fieldName) {
		Vector<String> tmp = new Vector<String>();
		tmp.add(author);
		Utils.setAuthors(doc, tmp, fieldName);
	}
	public static void setAuthors( Document doc, Vector<String> authors, String fieldName) {
		Item itAuthors = null;
		
		try {
			itAuthors = doc.replaceItemValue(fieldName, authors);
			itAuthors.setAuthors(true);
		} catch (NotesException ne) {
			//do nothing
		} catch (Throwable t) {
			t.printStackTrace();
		} finally{
			Utils.recycle(itAuthors);
		}
	}
	
	/*
	 * adds a value to an item in a document
	 * returns true if the item was added and false if nothing was added (e.g. if the value was already in the item)
	 */
	@SuppressWarnings("unchecked")
	public static boolean addItemValue( Document doc, String itemName, Object itemValue, boolean allowDoubleEntries, boolean overwriteLastEntry ) {
		
		Item item = null;
		boolean result = false;
		
		try {
		
			item = doc.getFirstItem(itemName);
			
			if ( item == null ) {	//item doesn't exist: create it and add the value
				
				doc.replaceItemValue( itemName, itemValue );
				result = true;
				
			} else {
				
				//add value if double entries are allowed (always) or 
				//no double entries allowed and value isn't present yet
				
				if ( (!allowDoubleEntries && !item.containsValue(itemValue)) || allowDoubleEntries  ) {

					Vector<Object> values = item.getValues();
					
					if (values == null) {		//item doesn't have a value at all (e.g. empty string)
						
						doc.replaceItemValue( itemName, itemValue);
						result = true;
						
					} else if (overwriteLastEntry) {
					
						values.set( values.size()-1, itemValue);
						item.setValues(values);
						result = true;
						
					} else {
					
						values.add(itemValue);
						item.setValues(values);
						result = true;
						
					}
				}

			}
			
			
		} catch (NotesException e) {

			Logger.error(e);
		} finally {
			
			Utils.recycle(item);
		}
		
		return result;
	}
	
	
	//removes a values from a field (for a text list: leaves existing values intact)
	//returns true if the item was removed and false if nothing was removed
	@SuppressWarnings("unchecked")
	public static boolean removeItemValue(Document doc, String itemName, Object itemValue) {
		
		Item item = null;
		boolean result = false;
		
		try {
			item = doc.getFirstItem(itemName);
			
			if (item != null) {
				if (item.containsValue(itemValue) ) {
					Vector values = item.getValues();
					values.remove(itemValue);
					item.setValues(values); 
					result = true;
				}
			}
		} catch (NotesException e) {
			Logger.error(e);
		} finally {
			Utils.recycle(item);
		}
		
		return result;
	}

}
