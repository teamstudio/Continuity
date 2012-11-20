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
import java.util.Date;
import javax.faces.context.FacesContext;
import com.ibm.xsp.designer.context.XSPContext;

public class Message implements Serializable {

	private static final long serialVersionUID = -4017804868101698470L;
	
	public static final String TYPE_INFO = "info";
	public static final String TYPE_WARNING = "warning";
	public static final String TYPE_DEBUG = "debug";
	public static final String TYPE_ERROR = "error";
	public static final String TYPE_DIVIDER = "divider";
	
	private String text;
	private String type;
	private Date date;
	private String context;

	private String stackTrace;
	
	private boolean hasStackTrace = false;
	private boolean showStackTrace = false;

	public Message(Object obj, String context, String type) {
		this.text = getMessageText(obj);
		this.type = (type==null ? TYPE_INFO : type);
		this.context = context;
		this.date = new Date();
	}
	
	public Message(Throwable error, String context) {
		
		StringBuilder e = new StringBuilder();
		
		e.append( error.toString() );
		
		if (error.getClass().getName().equals( "javax.faces.FacesException") ) {
			
			e.append("<table cellpadding=\"0\" cellspacing=\"0\" class=\"errorDetails\"><tbody><tr><td colspan=\"2\"><b><u>Error details</u></b></td></tr>");
		
			javax.faces.FacesException fE = (javax.faces.FacesException) error;
			
			if ( fE.getCause().getClass().getName().equals( "com.ibm.xsp.exception.EvaluationExceptionEx") ) {
				
				com.ibm.xsp.exception.EvaluationExceptionEx evEx = (com.ibm.xsp.exception.EvaluationExceptionEx) fE.getCause();
				
				if ( evEx.getCause().getClass().getName().equals("com.ibm.jscript.InterpretException") ) {
					
					com.ibm.jscript.InterpretException iEx = (com.ibm.jscript.InterpretException) evEx.getCause();
					
					e.append("<tr><td><b>Message:</b></td><td>" + iEx.getMessage() + "</td></tr>");
					e.append("<tr><td><b>JavaScript code:</b></td><td>" + iEx.getExpressionText().replace("\n", "<br />") + "</td></tr>");
					
				}
				
				e.append("<tr><td><b>Caused by component:</b></td><td>" + evEx.getErrorComponentId() + "</td></tr>");
				e.append("<tr><td><b>Property:</b></td><td>" + evEx.getErrorPropertyId() + "</td></tr>" );
			}
			
			XSPContext xspContext = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
			String [] historyUrls = xspContext.getHistoryUrls();
			String lastUrl = historyUrls[historyUrls.length-1];
			
			e.append("<tr><td><b>In page:</b></td><td><a href=\"" + FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + lastUrl + "\">" + lastUrl + "</a></td></tr>");
			e.append("</tbody></table>");
		}
		
		this.text = e.toString();
		
		this.type = TYPE_ERROR;
		this.context = context;
		this.date = new Date();
		
		hasStackTrace = true;
		this.stackTrace = getStacktraceAsString( error );
		
	}
	
	private static String getMessageText(Object obj) {
		
		String text = "";
		
		try {
			text = (String) obj;		//strings
		} catch (ClassCastException cce) {
			
			try {
				text = obj.toString();
			} catch ( Exception e) {
				text = "error: cannot retrieve text for object of class " + obj.getClass().getName();
			}
			
		} catch (Exception e) {
			text = "error: cannot retrieve text for object of class " + obj.getClass().getName();
		}
		
		return text;
	}
	
	public boolean hasStackTrace() {
		return this.hasStackTrace;
	}
	
	public boolean isShowStackTrace() {
		return showStackTrace;
	}
	public void setShowStackTrace(boolean show) {
		this.showStackTrace = show;
	}
	
	public String getStackTrace() {
		return stackTrace;
	}
	

	public String getText() {
		return text;
	}

	public String getType() {
		return type;
	}

	public String getContext() {
		return context;
	}

	public Date getDate() {
		return date;
	}
	
	public String getStyleclass() {
		if (type.equals(TYPE_ERROR) || type.equals(TYPE_WARNING) || type.equals(TYPE_DEBUG)) {
			return type;
		} else {
			return "";
		}
	}
	
	// convert a stacktrace to a string
	private String getStacktraceAsString( Throwable e) {
		StringBuilder sb = new StringBuilder();
		for (StackTraceElement element : e.getStackTrace()) {
			sb.append(element.toString() + "\n");
		}
		return sb.toString();
	}

}

