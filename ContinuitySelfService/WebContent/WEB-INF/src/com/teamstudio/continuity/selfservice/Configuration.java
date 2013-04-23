package com.teamstudio.continuity.selfservice;

import java.io.Serializable;
import java.util.HashMap;

import javax.faces.context.FacesContext;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.designer.context.XSPContext;
import com.teamstudio.continuity.utils.Logger;
import com.teamstudio.continuity.utils.Utils;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;

import java.util.List;

public class Configuration implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String server;
	private String directoryDbPath;
	private String hostUrl;
	private String loginsDbPath;
	private String unpluggedDbPath;
	private String relativeDbUrl;
	
	private String senderEmail;
	private String senderName;
	
	private String adminGroup;
	
	private String continuityDbTemplatePath;
	private String installBasePath; 
	
	public static final String ROLE_ADMIN = "[admin]";
	public static final String ROLE_EDITOR = "[bcEditor]";
	public static final String ROLE_USER = "[bcUser]";
	public static final String ROLE_DEBUG = "[debug]";

	private HashMap<String, String> orgContinuityPaths;

	//read the configuration for the logins/ self-service database
	public Configuration() {
		
		this.reload();
		
	}
	
	public void reload() {
		
		Document docSettings = null;
		Database dbCurrent = null;
		
		try {
			
			System.out.println("(Continuity - self service) reading configuration");
			
			dbCurrent = Utils.getCurrentDatabase();
			
			orgContinuityPaths = new HashMap<String, String>();
			
			server = dbCurrent.getServer();
			loginsDbPath = dbCurrent.getFilePath();
			relativeDbUrl = "/" + loginsDbPath.replaceAll("\\\\", "/");
	
			docSettings = Utils.getCurrentDatabase().getView("vwSettings").getFirstDocument();
			
			if (null != docSettings) {
				
				hostUrl = docSettings.getItemValueString("hostUrl");
				if (hostUrl.indexOf("http") != 0) {
					hostUrl = "http://" + hostUrl; 
				}
				
				directoryDbPath = docSettings.getItemValueString("directoryDbPath");
				unpluggedDbPath = docSettings.getItemValueString("unpluggedDbPath");
				
				continuityDbTemplatePath = docSettings.getItemValueString("continuityDbTemplatePath");
				installBasePath = docSettings.getItemValueString("installBasePath");
				
				adminGroup = docSettings.getItemValueString("adminGroup");
				
				senderEmail = docSettings.getItemValueString("senderEmail");
				senderName = docSettings.getItemValueString("senderName");
				
				//defaults
				if (senderEmail.length()==0) {
					senderEmail = "no-reply@continuity.com";
				}
				if (senderName.length()==0) {
					senderName = "Continuity";
				}
				
			}
			
		} catch (NotesException e) {
			
			System.out.println("error while reading configuration:");
			e.printStackTrace();
			
		} finally {
			Utils.incinerate(docSettings, dbCurrent);
		}
		
	}
	
	public static Configuration get() {
		return (Configuration) Utils.getBean("configBean");
	}

	public String getServerName() {
		return server;
	}

	public String getDirectoryDbPath() {
		return directoryDbPath;
	}
	
	public String getUnpluggedDbPath() {
		return unpluggedDbPath;
	}
	
	public String getLoginsDbPath() {
		return loginsDbPath;
	}

	public String getHostUrl() {
		return hostUrl;
	}
	
	public String getDbUrl() {		//returns a url to the current db, including host
		return hostUrl + relativeDbUrl;
	}
	
	//redirect a logged in user to his Continuity application
	@SuppressWarnings("unchecked")
	public void redirectToContinuity() {
		
		Document docPublicProfile = null;
		
		try {
			
			Configuration config = Configuration.get();
			
			FacesContext facesContext = FacesContext.getCurrentInstance();
			
			String redirectTo = null;
			
			//create Name object for current user
			Session session = Utils.getCurrentSession();
			String currentUser = session.getEffectiveUserName();
			Name nmUser = session.createName(currentUser);
			String organization = nmUser.getOrganization();
			nmUser.recycle();
			session.recycle();
			
			redirectTo = getContinuityDbPath( organization );
			
			if ( StringUtil.isEmpty(redirectTo)) {
				
				//continuity database for this user not found - check if the user is an admin
				XSPContext context = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
				List<String> roles = context.getUser().getRoles();
				
				if (roles.contains( Configuration.ROLE_ADMIN )) {
					redirectTo = config.getLoginsDbPath() + "/organisations.xsp";			//redirect to organisations overview
				} else {
					
					//show error page
					Utils.addErrorMessage("Continuity application could not be found");
					redirectTo = config.getLoginsDbPath() + "/error.xsp";
					
				}
				
			}
			
			//create url to this database relative to the server
			redirectTo = "/" + redirectTo.replaceAll("\\\\", "/");
			
			//redirect to target location					 
			facesContext.getExternalContext().redirect(redirectTo);
				
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			Utils.incinerate(docPublicProfile);
			
		}
		
	}
	
	//retrieve the path to the Continuity database of the specified organisation (using the alias)
	//the path is stored in the application scope for faster retrievals
	public String getContinuityDbPath( String orgAlias) {
		return this.getOrganisationSetting(orgAlias, 2, orgContinuityPaths);
	}
	
	private String getOrganisationSetting(String orgAlias, int colIndex, HashMap<String,String> cacheMap) {
		View vwOrganisations = null;
		ViewEntry entryOrg = null;
		Session sessionAsSigner = null;
		Database dbCurrent = null;
		String path = null;
		
		try {
			
			if ( StringUtil.isEmpty(orgAlias)) {
				return null;
			}
			
			if (!cacheMap.containsKey(orgAlias)) {
				
				//Logger.debug("not in cache for " + orgAlias);

				//retrieve path to a user's organisation database to redirect user to
				//cache the path it in a local map
				sessionAsSigner = Utils.getCurrentSessionAsSigner();
				dbCurrent = sessionAsSigner.getCurrentDatabase();
				vwOrganisations = dbCurrent.getView("vwOrganisations");
				entryOrg = vwOrganisations.getEntryByKey( orgAlias, true );
				
				if (entryOrg != null) {
					
					//organisation found: redirect to organisation db (defaults to admin db)
					path = (String) entryOrg.getColumnValues().elementAt(colIndex);
					cacheMap.put(orgAlias, path);
					
					//Logger.debug("(redirect) store path in cache: " + orgAlias + "/" + path);
					
				} else {
					
					//Logger.warn("(redirect) could not find path to organisation database with alias " + orgAlias);
					
				}
				
			} else {
				
				path =  cacheMap.get(orgAlias);
				
				//Logger.debug("(redirect) from cache: " + path);
				
			}
			
		} catch (Exception e) {
			Logger.error(e);
		} finally {
			
			Utils.incinerate(entryOrg, vwOrganisations);
			
		}
		
		return path;
		
	}
	
	//default start/ end for notifications send from Continuity
	private String HEADER_COLOR = "#116F9B";
		
	public String getMailHeader() {
		
		return "<table style=\"margin-left: 15px; margin-right: 5px;\"><tbody>" +
		"<table width=\"100%\" style=\"border:1px solid #cccccc; font:normal 12px Arial; background: #ffffff; width: 600px;\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tbody>" +
		"<tr><td style=\"padding:4px 10px; background:" + HEADER_COLOR + "; text-align:right\"><a href=\"" + hostUrl + "\" style=\"color:#ffffff; font-weight: bold; text-decoration:none;\">Continuity</a></td></tr>" +
		"<tr><td style=\"padding: 15px 20px\">";
	}
	
	public String getMailFooter() {
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("</td></tr>");
		
		sb.append("<tr><td style=\"padding:4px 10px; background:" + HEADER_COLOR + "; text-align:right\">&nbsp;</td></tr>");
		sb.append("</tbody></table>");
				
		sb.append("<table width=\"100%\" style=\"border:0; font:normal 10px Arial; padding: 5px 0;\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tbody>");
		sb.append(" <tr><td style=\"text-align: center;\">(this email was generated automatically; you cannot respond to it)</td></tr>");
		sb.append("</tbody></table>");
		
		return sb.toString();
		
	}

	public String getContinuityDbTemplatePath() {
		return continuityDbTemplatePath;
	}

	public String getInstallBasePath() {
		return installBasePath;
	}
	
	public String getAdminGroup() {
		return adminGroup;
	}

	public String getSenderEmail() {
		return senderEmail;
	}

	public String getSenderName() {
		return senderName;
	}
		
	
}