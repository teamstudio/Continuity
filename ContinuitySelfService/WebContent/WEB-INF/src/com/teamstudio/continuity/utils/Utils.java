package com.teamstudio.continuity.utils;

import java.util.Date;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import com.teamstudio.continuity.utils.Logger;
import lotus.domino.*;

import com.ibm.xsp.extlib.util.ExtLibUtil;

public class Utils extends com.ibm.xsp.extlib.util.ExtLibUtil {
	
	public static final Pattern rfc2822 = Pattern.compile(
	        "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$"
	);
	
	public static String VIEW_ALL_BY_ID = "vAllByIdLU";
	
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
				System.out.println("sessionAsSigner object not found, using session object (for user " + s.getEffectiveUserName() + ")" );
			}
		} catch (NotesException e) { }
	}
	

	public static Object getBean(String beanName) {
		/*FacesContext context = FacesContext.getCurrentInstance();
		Application app = context.getApplication();
		ValueBinding binding = app.createValueBinding("#{" + expr + "}");
		Object value = binding.getValue(context);*/
		 
		return ExtLibUtil.resolveVariable(FacesContext.getCurrentInstance(), beanName);
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
	
	// adds a value to an item (doesn't replace existing items)
	//returns true if the item was added and false if nothing was added (e.g. if the value was already in the item) 
	@SuppressWarnings("unchecked")
	public static boolean addItemValue( Document doc, String itemName, Object itemValue, boolean allowDoubleEntries, boolean overwriteLastEntry ) {
		
		try {
			allowDoubleEntries = (allowDoubleEntries || false);
			overwriteLastEntry = (overwriteLastEntry || false);
			
			Item item = doc.getFirstItem(itemName);
			if ( item == null ) {	//item doesn't exist: add it
				
				doc.replaceItemValue( itemName, itemValue );
				return true;
				
			} else {
				
				//add value if double entries are allowed (always) or 
				//no double entries allowed and value isn't present yet
				
				if ( (!allowDoubleEntries && !item.containsValue(itemValue)) || allowDoubleEntries  ) {

					Vector<Object> values = item.getValues();
					
					if (values == null) {		//item doesn't have a value at all (e.g. empty string)
						
						doc.replaceItemValue( itemName, itemValue);
						return true;
						
					} else if (overwriteLastEntry) {
					
						values.set( values.size()-1, itemValue);
						item.setValues(values);
						return true;
						
					} else {
					
						values.add(itemValue);
						item.setValues(values);
						return true;
						
					}
				}

			}
			
			
		} catch (NotesException e) {

			Logger.error(e);
		}
		
		return false;
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
	
	/*
	 * retrieves a document as signer
	 */
	public static Document getDocumentAsSigner( String id ) {
		
		try {
			
			//open current database as signer
			Database db = ExtLibUtil.getCurrentSessionAsSigner().getCurrentDatabase();

			return getDocumentAsSignerEx(id, db);
			
		} catch (NotesException e) {
			e.printStackTrace();
		}
		
		return null;

	}
	
	//retrieves a document as signer in the specified database
	public static Document getDocumentAsSignerEx( String id, Database db) {
		
		Document doc = null;
		
		try {
			//try to retrieve as unid
			if (id.length()==32) {
				try {
					doc = db.getDocumentByUNID(id);
				} catch (Exception e) { }
			}
			
			//if doc couldn't be found by unid OR id isn't a unid, try to retrieve from UNID view
			if (doc==null) {
				View vwAllById = db.getView(VIEW_ALL_BY_ID);
				doc = vwAllById.getDocumentByKey(id, true);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return doc;

		
	}
	
	public static String getRandomString( int length, boolean allowSpecialChars ) {
		
		//character set to generate random value from
		//note: some characters aren't included to not cause confusion
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
	
	//recycle all objects in the arguments (in that order)
	public static void incinerate(Object... dominoObjects) {
	    for (Object dominoObject : dominoObjects) {
	        if (null != dominoObject) {
	            if (dominoObject instanceof Base) {
	                try {
	                    ((Base)dominoObject).recycle();
	                } catch (NotesException ne) {
	                    
	                }
	            }
	        }
	    }
	}
	
public static Date readDate( Document doc, String itemName) {
		
		Item itDate = null;
		Date result = null;
		
		try {
		
			itDate = doc.getFirstItem(itemName);
			
			if (itDate != null) {
				if (itDate.getDateTimeValue() != null) {
					result = itDate.getDateTimeValue().toJavaDate();
				}
			}
		
		} catch (NotesException e) {
			e.printStackTrace();
		} finally {
			Utils.incinerate(itDate);
		}
		
		return result;
		
	}
	

}
