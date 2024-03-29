package com.teamstudio.continuity.utils;

import eu.linqed.debugtoolbar.DebugToolbar;

//generic class for system logging, logs to the debug toolbar for now

public class Logger {
	
	public static void debug( String msg) {
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.debug(msg);
		}
	}
	
	public static void warn( String msg) {
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.warn(msg);
		}
	}
	
	public static void info( String msg) {
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.debug(msg);
		}
	}
	
	public static void error( Throwable e, String msgContext) {
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.error(e, msgContext);
		}
	}
	
	public static void error( Throwable e) {
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.error(e);
		}
	}
	
	public static void error(String msg) {
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.error(msg);
		}
	}

	
}
