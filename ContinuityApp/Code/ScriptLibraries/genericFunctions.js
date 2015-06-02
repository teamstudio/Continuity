//attach a function (to every page) that automatically sets the focus on the first
//input (text) field
dojo.addOnLoad(function(){
	
	setFocusOnFirstField();
	
});

function setFocusOnFirstField() {
	
	try {
		
		var i = dojo.query("input[type=text]");
		
		if (i.length>0) {
			i[0].focus();
		}
		
	} catch (e) { }
	
}

function toggleDetails( idImg, idDetails ) {
	
	var img = dojo.byId(idImg);
	
	if ( dojo.hasClass(img, "lotusIconShow") ) {
	
		//show
		dojo.replaceClass( img , "lotusIconHide", "lotusIconShow");
		dojo.attr(img, "title", "Hide details");
		
		dojo.fx.wipeIn( {
				node: idDetails,
				duration: 300
			} ).play();

	} else {
	
		//hide
		dojo.replaceClass( img , "lotusIconShow", "lotusIconHide");
		dojo.attr(img, "title", "Show details");
		dojo.fx.wipeOut( {
				node: idDetails,
				duration: 300
			} ).play();
	}
	
}

//Modified version of 'Standby Dialog V.3.2'
//Code Compile by Fredrik Norling www.xpagedeveloper.com
//used with permission from the authors of the original code
//http://dontpanic82.blogspot.com/2010/01/xpages-hijackingpublishing-partial.html
//http://lotusnotus.com/lotusnotus_en.nsf/dx/xpages-tip-a-modal-waiting-dialog-for-background-processes..htm
var init_hijackAndPublishPartialRefresh=false;

if(typeof hijackAndPublishPartialRefresh != 'function')
{ 
	//load only once
	init_hijackAndPublishPartialRefresh=true;
	var obj_hijackAndPublishPartialRefresh=function (){
		
		// hijack the partial refresh 
		XSP._inheritedPartialRefresh = XSP._partialRefresh;
		
		XSP._partialRefresh = function( method, form, refreshId, options ){  
			// Publish init
			dojo.publish( 'partialrefresh-init', [ method, form, refreshId, options ]);
			this._inheritedPartialRefresh( method, form, refreshId, options );
		}

		// Publish start, complete and error states 
		dojo.subscribe( 'partialrefresh-init', function( method, form, refreshId, options ){

			if( options ){ // Store original event handlers
				var eventOnStart = options.onStart; 
				var eventOnComplete = options.onComplete;
				var eventOnError = options.onError;
			}
	
			options = options || {};
			
			options.onStart = function(){
				dojo.publish( 'partialrefresh-start', [ method, form, refreshId, options ]);
				if( eventOnStart ){
					if( typeof eventOnStart === 'string' ){
						eval(eventOnStart)
					} else {
						eventOnStart();
					}
				}
			};
	
			options.onComplete = function(){
				dojo.publish( 'partialrefresh-complete', [ method, form, refreshId, options ]);
				if( eventOnComplete ){
					if( typeof eventOnComplete === 'string' ){
						eval( eventOnComplete );
					} else {
						eventOnComplete();
					}
				}
			};
	
			options.onError = function(){
				dojo.publish( 'partialrefresh-error', [ method, form, refreshId, options ]);
				if( eventOnError ){
					if( typeof eventOnError === 'string' ){
						eval( eventOnError );
					} else {
						eventOnError();
					}
				}
			};
		});
	}
	
	hijackAndPublishPartialRefresh = obj_hijackAndPublishPartialRefresh;
	
}

var doShowLoading = false;

function showLoading() {
	if (doShowLoading) {
		dojo.style( 'loading', 'display', 'block');
	}
}
function hideLoading() {
	doShowLoading = false;
	dojo.style( 'loading', 'display', 'none');	   
}

function enableLoadingIndicator() {
	try{
		//dojo-subscribe('onfocus',null,function(method,form,refreshId){FieldOnfocus()})
		dojo.subscribe( 'partialrefresh-start', null, function( method, form, refreshId ){
			doShowLoading = true;
			setTimeout( showLoading, 200);
		
		} );
		dojo.subscribe( 'partialrefresh-complete', null, function( method, form, refreshId ){
			hideLoading();
		} );
		
		dojo.subscribe( 'partialrefresh-error', null, function( method, form, refreshId ){
			alert("Sorry, the request could not be processed.");
		  hideLoading();
		} );
	}catch(e){
		console.log(e);
	}
}

if(init_hijackAndPublishPartialRefresh==true){
	hijackAndPublishPartialRefresh();
}

dojo.addOnLoad( enableLoadingIndicator );