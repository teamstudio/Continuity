package eu.linqed.debugtoolbar;

/*
 * <<
 * XPage Debug Toolbar
 * Copyright 2012 Mark Leusink
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this 
 * file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License
 * >> 
 */

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Map.Entry;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import lotus.domino.Database;
import lotus.domino.Name;
import lotus.domino.Session;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.directory.DirectoryUser;
import com.ibm.xsp.designer.context.XSPContext;

public class DebugToolbar implements Serializable {

	private static final long serialVersionUID = 6348127836747732428L;

	public static final String BEAN_NAME = "dBar";
	
	private Vector<Message> messages;
	
	private final int MAX_MESSAGES = 2000;			//maximum number of message held in this class
	
	private boolean enabled;		//enable/ disable the toolbar
	
	private boolean collapsed;
	private HashMap<String, Timer> timers;
	private Vector<String> filterLogTypes;
	private String activeTab;
	private String requestContextPath;
	private String consolePath;
	private boolean showLists;
	private String toolbarColor;
	
	private Object dumpObject;
	
	private boolean configLoaded;
	
	private SortedSet<String> sortedScopeContents;
		
	public DebugToolbar() {
		
		activeTab = "";		//default tab: none
		
		enabled = true;
		collapsed = false;
		messages = new Vector<Message>(); 
		timers = new HashMap<String, Timer>();
		filterLogTypes = new Vector<String>();
		showLists = true;
		toolbarColor = "#444444";
		
		configLoaded = false;
		
		requestContextPath = FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
		consolePath = requestContextPath + "/debugToolbarConsole.xsp";
	}
	
	//retrieve an instance of this toolbar class
	public static DebugToolbar get() {
		return (DebugToolbar) resolveVariable(BEAN_NAME);
	}
	
	//dump the contents of any object to the toolbar
	public void dump(Object dumpObject) {
		
		this.dumpObject = dumpObject;
		ValueBinding vb = FacesContext.getCurrentInstance().getApplication().createValueBinding("#{javascript:dBarHelper.dumpObject()}");
		this.info("(dumped) " + vb.getValue(FacesContext.getCurrentInstance()).toString());
		this.dumpObject = null;
		
	}
	
	public Object getDumpObject() {
		return dumpObject;
	}
	
	public void init(boolean defaultCollapsed, String color) {

		if (!this.configLoaded){
			this.collapsed = defaultCollapsed;
			
			if (color != null ) {
				this.toolbarColor = color;
			}

			this.configLoaded = true;
		}
	}
	
	public static boolean hideDetails( String entryName) {
		
		if (entryName.equals(DebugToolbar.BEAN_NAME)) {
			return true;
		} else if ( entryName.equals("expressionInfo") ||
				entryName.equals("expression") ||
				entryName.equals("componentId") ||
				entryName.equals("previousExpressions") ||
				entryName.equals("hiddenComponents") ||
				entryName.equals("xpageNames") ) {
			return true;
		}
		
		return false;
		
	}
	
	
	public String getActiveTab() {
		return this.activeTab;
	}
	
	//change the active tab
	public void setActiveTab( String tabName ) {
		
		if (tabName.equals(this.activeTab) ) {		//open tab clicked: close it
			this.activeTab = "";
			
		} else {
			
			this.activeTab = tabName;
			
			if (tabName.indexOf("Scope")>-1) {
				readScopeContents();
			}
			
		}
	}
	
	public void openTab( String tabName) {
		this.activeTab = tabName;
	}
	
	public void close() {
		this.setActiveTab("");
	}
	
	
	public boolean scopeHasValues() {
		return this.getScopeContents().size()>0;
	}
	
	@SuppressWarnings("unchecked")
	public Set getScopeContents() {
		return sortedScopeContents;
	}
	
	@SuppressWarnings("unchecked")
	public void readScopeContents() {
		Map map = (Map) resolveVariable(activeTab);
		sortedScopeContents = new TreeSet<String>(map.keySet());
	}
	
	public void clearScopeContents(String type) {
		if (type.equals("both")) {
			this.clearApplicationScope();
			this.clearSessionScope();
		} else if (type.equals("sessionScope")) {
			this.clearSessionScope();
		} else {
			this.clearApplicationScope();
		}
	}
	
	// clear the contents of the sessionScope
	@SuppressWarnings("unchecked")
	public void clearSessionScope() {
		
		Map sessionScope = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
		
		Iterator it = sessionScope.keySet().iterator();
		while(it.hasNext() ) {
			
			String key = (String) it.next();
			if (!key.equals( BEAN_NAME )) {		//don't remove debug toolbar variable
				sessionScope.remove(key);
			}
			
		}

		// hide all tabs after clearing
		setActiveTab("");
	}

	// clear the contents of the applicationScope
	@SuppressWarnings("unchecked")
	public void clearApplicationScope() {
		try {

			Map applicationScope = FacesContext.getCurrentInstance().getExternalContext().getApplicationMap();
			
			Iterator it = applicationScope.keySet().iterator();
			while(it.hasNext() ) {
				applicationScope.remove(it.next());
			}
				
			setActiveTab("");
		} catch (Exception e) {
			this.error(e);
		}
	}
	
	//remove a specific entry from a scope
	@SuppressWarnings("unchecked")
	public void clearScopeEntry(String name) {
		
		if (StringUtil.isNotEmpty(name)) {

			Map map = (Map) resolveVariable(activeTab);
			map.remove(name);
		
			readScopeContents();
		}
	}
	
	public boolean isCollapsed() {
		return this.collapsed;
	}
	public void setCollapsed(boolean to) {
		this.collapsed = to;
	}
	
	public boolean isEnabled() {
		return this.enabled;
	}
	public void setEnabled(boolean to) {
		this.enabled = to;
	}
	
	public void setShowLists(boolean to) {
		this.showLists = to;
	}

	public boolean isShowLists() {
		return showLists;
	}
	
	/*
	 * log a message with a specific type to the toolbar
	 */
	private void log( Object msg, String msgContext, String type) {
		addMessage(new Message( msg, msgContext, type));
	}
	private void log( Throwable msgThrown, String msgContext) {
		addMessage( new Message(msgThrown, msgContext) );
	}
	
	private void addMessage(Message message) {
		try {
			if (!enabled) {
				return;
			}
			
			this.messages.add(0, message);
	
			if (messages.size() > this.MAX_MESSAGES) {
				messages.remove(messages.size() - 1); // remove oldest element
			}
			
		} catch (Exception e) {
			
			//error while logging to the toolbar log to console
			System.err.println("(DebugToolbar) error while logging: " + e.getMessage());
			System.out.println("(DebugToolbar) " + message.getText() );
		}
	}
	
	
	public void info(Object msg) {
		this.log( msg, null, Message.TYPE_INFO);
	}
	public void info(Object msg, String msgContext) {
		this.log(msg, msgContext, Message.TYPE_INFO);
	}
	public void debug(Object msg) {
		this.log(msg, null, Message.TYPE_DEBUG);
	}
	public void debug(Object msg, String msgContext) {
		this.log(msg, msgContext, Message.TYPE_DEBUG);
	}
	public void warn(Object msg) {
		this.log(msg, null, Message.TYPE_WARNING);
	}
	public void warn(Object msg, String msgContext) {
		this.log(msg, msgContext, Message.TYPE_WARNING);
	}
	public void error(Throwable error) {
		this.log(error, null);
	}
	public void error(Throwable error, String msgContext) {
		this.log(error, msgContext);
	}
	public void error( Object error ) {
		this.error(error, null);
	}
	public void error( Object error, String msgContext) {
		this.log(error, msgContext, Message.TYPE_ERROR);
	}
	
	public void addDivider() {
		this.log("", null, Message.TYPE_DIVIDER);
	}

	// clear all debug log messages
	public void clearMessages() {
		messages.clear();
	}
	

	private static Object resolveVariable (String variableName) {
		FacesContext context = FacesContext.getCurrentInstance();
		return context.getApplication().getVariableResolver().resolveVariable(context, variableName);
    }
	
	
	// check if the selected type is filtered from the list of messages
	public boolean isFiltered(String type) {
		return filterLogTypes.contains(type);
	}

	// add or remove the selected type to the list of filtered message types
	public void toggleFiltered(String type, boolean included) {
		
		if (included) {

			if (filterLogTypes.contains(type)) {
				filterLogTypes.remove(type);

			}

		} else {

			if (!filterLogTypes.contains(type)) {
				filterLogTypes.add(type);
			}

		}
	}

	// filter the list of messages and return it
	public Vector<Message> getFilteredMessages() {
		
		if (filterLogTypes.size() == 0) {

			return messages;

		} else {

			Vector<Message> filtered = new Vector<Message>();

			Iterator<Message> messagesIt = messages.iterator();
			while (messagesIt.hasNext()) {

				Message m = messagesIt.next();

				if (!filterLogTypes.contains(m.getType())) {

					filtered.add(m);

				}

			}

			return filtered;
		}

	}

	public Vector<Message> getMessages() {
		return messages;
	}

	public boolean hasMessages() {
		return messages.size() > 0;
	}
	
	/* functions for timers */
	public String getTimersDump() {
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("<table class=\"grid\"><thead><tr><th>Id</th><th>Total time<br />(ms)</th></tr></thead></tbody>");
		
		for(Timer timer : timers.values()) {
			
			sb.append("<tr><td>" + timer.getId() + "</td>");
			sb.append("<td>" + (timer.isRunning() ? "(running)" : timer.getTotalTime()) + "</td></tr>");
			
		}
		
		sb.append("</tbody></table>");
		
		return sb.toString();
	}
	
	public boolean hasTimers() {
		return timers.size() > 0;
	}
	public int getNumTimers() {
		return timers.size();
	}

	public void clearTimers() {
		timers.clear();
	}

	// start a new timer
	public void startTimer(String id) {

		if (!enabled) {
			return;
		}

		if (timers.containsKey(id) && timers.get(id).isRunning()) {
			// can't start a running timer twice
		} else {

			timers.put(id, new Timer(id));
		}

	}

	// stop a timer
	public void stopTimer(String id) {

		if (!enabled || id == null) {
			return;
		}

		if (timers.containsKey(id)) {
			timers.get(id).stop();
		}

	}
	
	public static String getReadableSize( double bytes ) {
		
		double kbSize = bytes / 1024;
		
		if (kbSize < 1024) {
			//0 decimals
			return  Math.round(kbSize*1)/1 + " KB";
		} else {
			//1 decimal
			double mbSize = kbSize / 1024;
			return Math.round(mbSize*100)/100 + " MB";
		}
		
	}
	
	public String getConsolePath() {
		return consolePath;
	}
 	
	
	@SuppressWarnings("unchecked")
	public ArrayList<Entry<String, String>> getCustomVars() {

		ArrayList customVars = new ArrayList<Entry<String,String>>();

		try {

			Session session = (Session) resolveVariable("session");
			Database database = (Database) resolveVariable("database");

			XSPContext context = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
			DirectoryUser currentUser = context.getUser();

			String userName = session.getEffectiveUserName();
			Name n = session.createName(userName);
			Vector<String> groups = new Vector(currentUser.getGroups());
			groups.remove(userName);
			groups.remove(n.getCommon());

			ExternalContext extCon = FacesContext.getCurrentInstance().getExternalContext();
			
			HttpServletRequest httpServletRequest = (HttpServletRequest) extCon.getRequest();
			
			customVars.add( new AbstractMap.SimpleEntry("title", "User"));
			customVars.add( new AbstractMap.SimpleEntry("username", userName));
			customVars.add( new AbstractMap.SimpleEntry("access level", getReadableAccessLevel(database.getCurrentAccessLevel())));
			
			List<String> roles = context.getUser().getRoles();
			customVars.add( new AbstractMap.SimpleEntry("roles", (roles.size()>0 ? roles.toString() : "(no roles enabled)") ));
			customVars.add( new AbstractMap.SimpleEntry("groups", (groups.size()>0 ? groups.toString() : "(user not listed in any group)")));

			customVars.add( new AbstractMap.SimpleEntry("title", "Browser"));
			customVars.add( new AbstractMap.SimpleEntry("user agent", context.getUserAgent().getUserAgent()));
			customVars.add( new AbstractMap.SimpleEntry("name", context.getUserAgent().getBrowser() + " " + context.getUserAgent().getBrowserVersion()));
			customVars.add( new AbstractMap.SimpleEntry("language", context.getLocale().getDisplayName()	+ " (" + context.getLocaleString() + ")"));
			customVars.add( new AbstractMap.SimpleEntry("timezone", context.getTimeZoneString()));

			customVars.add( new AbstractMap.SimpleEntry("title", "Database"));
			customVars.add( new AbstractMap.SimpleEntry("name", database.getTitle()));
			customVars.add( new AbstractMap.SimpleEntry("path", database.getFilePath()));
			customVars.add( new AbstractMap.SimpleEntry("size", DebugToolbar.getReadableSize(database.getSize())));
			customVars.add( new AbstractMap.SimpleEntry("last modified", database.getLastModified().toString()));
			customVars.add( new AbstractMap.SimpleEntry("full text indexed?", (database.isFTIndexed() ? "yes (last update: " + database.getLastFTIndexed().toString() + ")" : "no")));

			customVars.add( new AbstractMap.SimpleEntry("title", "Request"));
			customVars.add( new AbstractMap.SimpleEntry("query string", context.getUrl().getQueryString()));
			customVars.add( new AbstractMap.SimpleEntry("remote address", httpServletRequest.getRemoteAddr()));
			
			customVars.add( new AbstractMap.SimpleEntry("cookies", getCookieDump(extCon.getRequestCookieMap()) ));

		} catch (Exception e) {
			error(e, "getCustomVars");

		}

		return customVars;

	}
	
	private static String getCookieDump( Map<String,Cookie> cookieMap) {
		
		StringBuilder sb = new StringBuilder();

		sb.append("<table class=\"dumped\"><tbody>");

		for (Cookie cookie : cookieMap.values()) {
			sb.append("<tr><td>" + cookie.getName() + "</td><td>" + cookie.getValue() + "</td></tr>");
		}
		
		sb.append("</tbody></table>");
		
		return sb.toString();
	
	}
	
	private static String getReadableAccessLevel(int level) {

		switch (level) {
			case 1:		return "Depositor";
			case 2:		return "Reader";
			case 3:		return "Author";
			case 4:		return "Editor";
			case 5:		return "Designer";
			case 6:		return "Manager";
		}
		return "";
	}
	
	public String getColor() {
		return toolbarColor;
	}
	public void setColor(String color) {
		this.toolbarColor = color;
	}
	
	private class Timer implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private String id;
		private long started = 0;
		private long totalTime = 0;
		
		public Timer(String id) {
			this.id = id;
			this.started = System.currentTimeMillis();
			this.totalTime = 0;
		}
		
		public boolean isRunning() {
			return (totalTime == 0);
		}
		
		public void stop() {
			
			if (isRunning() ) {
				totalTime = (System.currentTimeMillis() - started);
			}
				
		}
		
		public String getId() {
			return id;
		}
		
		public long getTotalTime() {
			return totalTime;
		}
		
	}
	
}
