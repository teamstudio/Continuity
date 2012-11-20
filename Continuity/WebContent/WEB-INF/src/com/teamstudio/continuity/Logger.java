package com.teamstudio.continuity;

import eu.linqed.debugtoolbar.DebugToolbar;

//generic class for system logging, logs to the debug toolbar for new

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
