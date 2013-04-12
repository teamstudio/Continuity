package com.teamstudio.apps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.xsp.component.UIFileuploadEx.UploadedFile;
import com.ibm.xsp.http.IUploadedFile;
import com.teamstudio.continuity.utils.Authorizations;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

//class used when importing contacts from a batch file in CSV or LDIF format
public class ContactsImport {

	private UploadedFile batchFile;
	
	private long numProcessed;
	private long numSuccess;
	private long numFailed;
	private String type;
	
	private static final String TYPE_AD = "ad";
	private static final String TYPE_OTHER = "other";
	
	//public boolean save( com.ibm.xsp.model.domino.wrapped.DominoDocument xspDocImport ) {
	public boolean save() {
		
		boolean success = false;
		BufferedReader uploadedFile = null;

		try {
	
			if (batchFile != null) {
				
				//batch file uploaded
				IUploadedFile iuploadedFile = batchFile.getUploadedFile();
				
				String fileName = iuploadedFile.getClientFileName();
				String extension = fileName.substring( fileName.lastIndexOf(".")+1);
				
				String validExtensions = "csv,ldif,xml";
				List<String> validExtensionsList = Arrays.asList(validExtensions.split(","));
				
				//check for a valid extension
				if ( !validExtensionsList.contains(extension) ) {
					
					Utils.addErrorMessage("Invalid file extension: only CSV and LDIF files are supported");
					return false;
			
				}
				
				Logger.debug("start processing uploade file " + iuploadedFile.getClientFileName() );
				
				//open the uploaded file and process it
				if (extension.equals("csv")) {
					
					type = TYPE_AD;
					uploadedFile = new BufferedReader(new FileReader(iuploadedFile.getServerFile()));
					processCSVFile( uploadedFile );
					
				} else if (extension.equals("ldif")) {
					
					type = TYPE_AD;
					uploadedFile = new BufferedReader(new FileReader(iuploadedFile.getServerFile()));
					processLdifFile( uploadedFile );

				} else if (extension.equals("xml")) {
				
					type = TYPE_OTHER;
					processXMLFile( iuploadedFile.getServerFile());
					
				}
				
				com.teamstudio.continuity.User.get().updateMenuOptionCounts();
				
				Utils.addInfoMessage("Succesfully processed " + numProcessed + " contact(s) from batch file");
				Logger.debug("done");
			}
			
			success = true;

		} catch (Exception e) {
			
			Logger.error(e);
			
		} finally {
			
			try {
				//close the file
				if (uploadedFile != null) { uploadedFile.close(); }
			} catch (Exception e) { }
			
		}
		
		return success;
	}
	
	/*
	 * Process an uploaded CSV file containing Active Directory users. The file should follow the format
	 * that's used when exporting users to a CSV file using CSVDE.
	 * 
	 * See http://support.microsoft.com/kb/327620 for more info on CSVDE.
	 */
	private void processCSVFile( BufferedReader csvFile ) throws IOException {
		
		boolean headerRowRead = false;
		String[] headerArray = null;
		
		//loop through all rows and create contacts
		String row = csvFile.readLine();
		
		while (row != null) {
			
			if (row.length()==0 || row.startsWith("#") || row.startsWith(";") ) {		
				
				//empty or comment row: skip
				
			} else if (!headerRowRead) {
				
				//header row is the first non-blank, not-comment row in the CSV file
				//the required header row contains the attribute names
				
				headerRowRead = true;
				
				//read header
				headerArray = parseCSVLine( row, true); 		
				Logger.debug("header: " + row);
				
			} else {
			
				HashMap<String,String> contactObject = ContactsImport.getRowValues(headerArray, row);
				createContact(contactObject);
			}
			
			row = csvFile.readLine(); //read next line form csv file
		}
		
		Logger.debug("finished");

	}
	
	private void processLdifFile( BufferedReader ldifFile ) throws IOException {
		
		HashMap<String,String> contactObject = new HashMap<String,String>();
		
		//loop through all rows and create entries
		String row = ldifFile.readLine();
		
		while (row != null) {
			
			if (row.length() == 0) {
				
				//empty row: process current object and start a new object
				createContact(contactObject);
						
				contactObject.clear();
				
			} else if (row.startsWith("#")) {
				
				//comment row: skip
				
			} else if (row.indexOf(":")>-1) {
				
				String[] keyValue = row.split("\\s*:\\s*");
				contactObject.put( keyValue[0].toLowerCase(), keyValue[1]);
				
			}
			
			
			row = ldifFile.readLine(); // Read next line of data.
		}
		
		//process last object
		createContact(contactObject);
		
		Logger.debug("finished");

	}
	
	/*
	 * not implemented yet - XML syntax needs to be worked out
	 */
	private void processXMLFile( File xmlFile ) throws SAXException, IOException, ParserConfigurationException {
		
		//parse the uploaded xml file
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		
		domFactory.setValidating(false);
		domFactory.setIgnoringComments(true);
		
		DocumentBuilder docBuilder = domFactory.newDocumentBuilder();
			
		Document doc = docBuilder.parse(xmlFile);
				
		Element employeesNode = doc.getDocumentElement();
		
		NodeList employees = employeesNode.getElementsByTagName("Employee");
		
		Logger.debug("found " + employees.getLength() + " employees in xml document");
		
		for (int i=0; i<employees.getLength(); i++ ) {
			
			HashMap<String,String> contactObject = new HashMap<String,String>();
			
			Element employee = (Element) employees.item(i);
			
			contactObject.put( "id", employee.getAttribute("id") );
			contactObject.put( "givenname", ContactsImport.getXmlChildNodeValue( employee, "GivenName") );
			contactObject.put( "sn", ContactsImport.getXmlChildNodeValue( employee, "SurName") );
			contactObject.put( "title", ContactsImport.getXmlChildNodeValue( employee, "JobTitle") );
			contactObject.put( "mail", ContactsImport.getXmlChildNodeValue( employee, "EmailAddress") );
			contactObject.put( "telephonenumber", ContactsImport.getXmlChildNodeValue( employee, "PhoneNumber") );
			
			createContact(contactObject);
			
		}
		
	}
	
	/*
	 * retrieve the text value of a specific child node of a parent
	 */
	private static String getXmlChildNodeValue( Element parentNode, String childNodeName ) {
		
		NodeList children = parentNode.getElementsByTagName(childNodeName);
		
		if (children != null && children.getLength()>0) {
			
			Element child = (Element) children.item(0);
			return child.getTextContent();

		}
		
		return null;
	}
		
	
	/*
	 * Create a new contact in the application
	 * 
	 * This application requires the following attributes to be present at a minimum
	 * in the contactObject key/value HashMap:
	 * 
	 * - objectClass		required value: User or person		(case insensitive)
	 * - givenName			first name
	 * - SN					last name
	 * - mail				e-mail address
	 * - objectGUID or userPrincipalName	uniquely identify a user
	 * 
	 * The following optional attributes can be used:
	 * - Title				job title
	 * - telephoneNumber		office phone
	 *  
	 */
	private void createContact( HashMap<String,String> contactObject ) {
				
		//validate input object
		try {
			
			if (contactObject == null || contactObject.isEmpty()) {
				return;
			}
			
			//DEBUG: output all values
			/*
			Logger.debug("***");
			 
			for (String key : contactObject.keySet() ) {
				Logger.debug("key:" + key + ", value:" + contactObject.get(key));
				
			}
			Logger.debug("***");
			*/
			
			numProcessed++;
			
			//validate required Active Directory fields
			if (type.equals(TYPE_AD)) {		
				
				String objectClass = contactObject.get("objectclass");
				
				if (objectClass == null || ( !objectClass.equalsIgnoreCase("user") && !objectClass.equalsIgnoreCase("person") )) {
					
					//no or no valid object class: skip
					throw( new Exception("missing required \"objectClass\" attribute"));
					
				} else if ( !contactObject.containsKey("userprincipalname") && !contactObject.containsKey("objectguid") ) {
					
					//no identifier: skip
					throw( new Exception("missing required attribute to identify user"));
				}
					
			}

			//check required attributes:
			if (
				!contactObject.containsKey("givenname") || 
				!contactObject.containsKey("sn") ||
				!contactObject.containsKey("mail")
			) {
				throw( new Exception("missing required attribute(s)"));
			}
			
			//create contact object using values
			Contact c = new Contact();
			
			c.setUserType(Authorizations.USER_TYPE_CONTACT);		//default user type = "contact"
			
			c.setLastName( contactObject.get("sn") );
			c.setFirstName(contactObject.get("givenname") );
			c.setEmail( contactObject.get("mail"));
			
			if ( contactObject.containsKey("objectguid")) {
				c.setImportIdentifier(contactObject.get("objectguid"));
			} else if (contactObject.containsKey("userprincipalname") ) {
				c.setImportIdentifier(contactObject.get("userprincipalname"));
			} else {
				c.setImportIdentifier(contactObject.get("id"));
			}
			
			if (contactObject.containsKey("title")) {
				c.setJobTitle(contactObject.get("title"));
			}
			if (contactObject.containsKey("telephonenumber")) {
				c.setPhoneWork( contactObject.get("telephonenumber"));
			}
			
			//optional: set org name/ alias from import file based on the (first?) 'OU' parameter in the user's DN
			
			c.create();
			
			numSuccess++;
			
		} catch (Exception e) {
			numFailed++;
			Logger.error("invalid contact object (" + e.getMessage() + ")" );
		}
		
	}
	
	//parse a line from the csv file and store its values in a hashmap containing keys based
	//on the variable definitino in the header row 
	private static HashMap<String,String> getRowValues( String[] headerArray, String row ) {
		
		String[] dataArray = ContactsImport.parseCSVLine(row, false);
		HashMap<String,String> values = null;
		
		try {
			//validate: number of elements in the row should be equals to the number of elements in the header
			if (dataArray.length != headerArray.length) {
				
				throw( new Exception("number of elements doesn't match header row"));
				
			} else {
				
				//store all values in the hashmap
				values = new HashMap<String,String>();
				
				for (int i=0; i<dataArray.length; i++) {
					values.put( headerArray[i], dataArray[i]);
				}
			}
		} catch (Exception e) {
			Logger.error("invalid row (" + e.getMessage() + "): " + row);
		}
		
		return values;
		
	}
	
	//parse a single line from a CSV file (comma separated)
	private static String[] parseCSVLine(String line, boolean convertToLowerCase ) {
		
		//split the line om commas, but only if that comma is not between quotes (0 or even nr of quotes ahead)
		String[] tokens = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
		
		String[] result = new String[tokens.length];
		
		//get rid of spaces and enclosing double quotes
		for (int i=0; i<tokens.length; i++) {
			
			String t = tokens[i].trim().replaceAll("^\"|\"$", "");
			
			if (convertToLowerCase) {
				t = t.toLowerCase();
			}
				
			result[i] = t;
			
		}
		
		return result;
	}

	//getter/setter for batch files
  	public UploadedFile getBatchFile() {
		return this.batchFile;
  	}
	public void setBatchFile(UploadedFile to) {
		this.batchFile = to;
	}
	
}
