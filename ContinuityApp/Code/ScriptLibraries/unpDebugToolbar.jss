/**
 * Copyright 2013 Teamstudio Inc 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 * the specific language governing permissions and limitations under the License
 */

var dBar = {
		
	TYPE_DEBUG : "debug",
	TYPE_INFO : "info",
	TYPE_ERROR : "error",
	TYPE_WARNING : "warning",
	
	_get : function() {
	
		return sessionScope.get("dBar") ||
			{
				isCollapsed : false,
				isEnabled : true,
				messages : [],
				isInit : false,
				timerStarted : 0
			};
	
	}, 
	
	init : function( collapsed:boolean ) {
		
		var dBar = this._get();
		if (!dBar.isInit) {
			dBar.isInit = true;
			dBar.isCollapsed = collapsed;
		}
		sessionScope.put("dBar", dBar);
		
	},
	
	setCollapsed : function( to:boolean ) {
		
		var dBar = this._get();
		dBar.isCollapsed = to;
		sessionScope.put("dBar", dBar);
		
	},
	
	setEnabled : function( to:boolean ) {
		
		var dBar = this._get();
		dBar.isEnabled = to;
		sessionScope.put("dBar", dBar);
		
	},
	
	//check if the toolbar is enabled
	isEnabled : function() {
		return this._get().isEnabled;
	},
	
	//returns if the toolbar is in a collapsed or expanded state
	isCollapsed : function() {
		return this._get().isCollapsed;
	},
	
	//retrieve a list of messages
	getMessages : function() {
		return this._get().messages;
	},
	
	//clears the list of messages & timer start
	clear : function() {
		var dBar = this._get();
		dBar.messages = [];
		dBar.timerStarted = 0;
		sessionScope.put("dBar", dBar);
	},
		
	//add a message to the toolbar
	//note: this function doesn't do anything if the toolbar is disabled
	addMessage : function(msg, type:String, showTimeSincePrevious:boolean ) {
		
		try {
		
			var dBar = this._get();
			
			if ( !dBar.isEnabled ) { return; }
			
			var messages = dBar.messages;
			
			if (typeof msg != "string") {
				msg = msg.toString();
			}
			
			var now = new Date();
			
			if (showTimeSincePrevious && messages.length > 0) {
				
				var nowMs = now.getTime();
				var timerStarted = dBar.timerStarted;
				
				if (timerStarted == 0) {
					dBar.timerStarted = nowMs;
					timerStarted = nowMs;
				}
				
				var prevMessage = messages[0];
				var sincePrev = " (+" + ( nowMs - prevMessage.date.getTime() )  + "ms / " + (nowMs - timerStarted) + " ms)";
				msg += sincePrev;
			}
			
			var m = {
				"text": msg, 
				"date" : now, 
				"type" : type
			};
			messages.unshift( m );
			
			dBar.messages = messages;
			
			sessionScope.put("dBar", dBar);
			
		} catch (e) {		//error while logging
			print(e.toString() );
		}

	},
	
	//function to log different types of messages
	debug : function(msg) {
		print("add message");
		this.addMessage(msg, this.TYPE_DEBUG, false);	
	},
	info : function(msg) {
		this.addMessage(msg, this.TYPE_INFO, false);	
	},
	error : function(msg) {
		this.addMessage(msg, this.TYPE_ERROR, false);	
	},
	warn : function(msg) {
		this.addMessage(msg, this.TYPE_WARNING, false);	
	},
	time : function(msg) {
		this.addMessage(msg, this.TYPE_DEBUG, true);	
	},
	
	//store all messages in a document in the current app
	save : function() {
	
		try {
			
			var doc = database.createDocument();
			
			doc.replaceItemValue("Form", "Log");
			doc.replaceItemValue("CreatedBy", @UserName() );
			doc.replaceItemValue("AppTitle", database.getTitle() );
			doc.replaceItemValue("LatestPage", context.getUrl().toString() );
			
			//TODO: would be great to add device info here too
			
			var dBar = this._get();
			
			var messages = dBar.messages;
			var messagesArr = [];
			
			for (var i=0; i<messages.length; i++) {
				
				messagesArr.push( @Text( messages[i].date ) + " " + messages[i].text );
			}
			
			doc.replaceItemValue("LogMessages", messagesArr);
			doc.save();
		
		} catch (e) {
			print("error while saving:");
			print(e);
		}
	}
		
}