package com.teamstudio.continuity.utils;

import eu.linqed.debugtoolbar.DebugToolbar;

//generic class for system logging, logs to the debug toolbar for new

public class Logger {
	
	private static boolean LOG_TO_CONSOLE = true;
	
	public static void debug( String msg) {
		logToConsole(msg);
				
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.debug(msg);
		}
	}
	
	public static void warn( String msg) {
		
		logToConsole(msg);
		
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.warn(msg);
		}
	}
	
	public static void info( String msg) {
		logToConsole(msg);
		
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.debug(msg);
		}
	}
	
	public static void error( Throwable e) {
		logToConsole(e.getMessage());
		
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.error(e);
		}
	}
	
	public static void error(String msg) {
		logToConsole(msg);
		
		DebugToolbar dBar = DebugToolbar.get();
		if (dBar != null ) {
			dBar.error(msg);
		}
	}
	
	private static void logToConsole( String msg) {
		if (LOG_TO_CONSOLE) {
			System.out.println(msg);
		}
	}

	
}
