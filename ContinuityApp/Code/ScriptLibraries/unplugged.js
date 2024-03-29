/**
 * Copyright 2013 Teamstudio Inc 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law unpor agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 * the specific language governing permissions and limitations under the License
 */

var unp = unp || {};

$(window).load( function() {
	
	$.blockUI.defaults.message = '';
	
	$(document).ajaxStart($.blockUI).ajaxStop($.unblockUI);
	unp.allowFormsInIscroll();

	initiscroll();
	
	$("#menupane").addClass("offScreen");
	$('.viewsButton').unbind('click');
	$('.viewsButton').click( function(event) {
		toggleViewsMenu();
		return false;
	});
	try {
		$(".viewlink").each( function() {
			$(this).addEventListener("click", function() {
				$.blockUI();
			});
		});
	} catch (e) {
	}
	
	//delayed image loading
	$("img.lazy").lazy();
	
	try{
		$(".opendialoglink").click( function (event){
			openDialog($(this).attr('href'));
		});
	}catch(e){
		
	}
	
	//doKeyboardScrollFix();
	
	//new NoClickDelay( document.getElementById('header') );
	//disabled for header because of problems with menu sliding in/out directly
	new NoClickDelay( document.getElementById('menu') );
	new NoClickDelay( document.getElementById('footer') );
	
	unp.disableRubberBanding();
	
});

/*
function doKeyboardScrollFix() {
	
	//workaround for fixed header/footer moving around when iPad virtual keyboard is turned on/off
	$('input, textarea')
		.on('focus', function (e) {
		    $('.iHeader, .footer').css('position', 'absolute');
		})
		.on('blur', function (e) {
			$('.iHeader, .footer').css('position', 'fixed');
		    
			//force redraw
		    setTimeout( function() {
		        window.scrollTo( $(window).scrollLeft(), $(window).scrollTop() );
		    }, 50 );
		});
		
}

$(window).scroll( function() {
	if ($(window).scrollTop() + $(window).height() == $(document).height()) {
		$(".loadmorebutton").click();
	}
});*/

window.addEventListener("orientationchange", function() {
	hideViewsMenu();
	initiscroll();
}, false);

unp.allowFormsInIscroll = function() {
	[].slice.call(document.querySelectorAll('input, select, button, textarea'))
			.forEach(
					function(el) {
						el.addEventListener(
								('ontouchstart' in window) ? 'touchstart'
										: 'mousedown', function(e) {
									e.stopPropagation();
								})
					});
}

var firedrequests = new Array();

function stopViewSpinner() {
	$(".loadmorelink").disabled = false;
	$("#loadmorespinner").hide();
}

function loadmore(url) {
	try {
		
		
		$(".loadmorelink").hide();
		$("#loadmorespinner").show();
		setTimeout("stopViewSpinner()", 5000);
		var itemlist = $("#summaryList li");
		var pos = itemlist.length;
		for ( var i = 0; i < firedrequests.length; i++) {
			if (firedrequests[i] == pos) {
				$(".loadmorelink").show();
				$("#loadmorespinner").hide();
				return;
			}
		}
		firedrequests.push(pos);
		
		var thisArea = $(".summaryDataRow");
		
		url = url + "?start=" + pos + "&dataMode=1";
		thisArea.load(url + " #summaryList", function() {
			
			$("#summaryList").append($(".summaryDataRow li"));
			if ($(".summaryDataRow").text().indexOf("NOMORERECORDS") > -1) {
				$("#pullUp").hide();
				$(".loadmorelink").hide();
				$("#loadmorespinner").hide();
			} else {
				$("#pullUp").show();
				$(".loadmorelink").show();
				$("#loadmorespinner").hide();
			}
			$(".summaryDataRow").empty();
			
			$("img.lazy").lazy();
			
			return false;
		});
	} catch (e) {
		// Do nothing
	}
}

function openDocument(url, target, title) {
	// $.blockUI();
	// document.location.href = url;
	
	storeRequest(url);
	
	var thisArea = $("#" + target);
	thisArea.load(url + " #contentwrapper",
			function() {

				if (firedrequests != null) {
					firedrequests = new Array();
				}
				initiscroll();
				if (url.indexOf("editDocument") > -1
						|| url.indexOf("newDocument") > -1) {
					unp.allowFormsInIscroll();
					try {
						if ($('.richtextfield').val().indexOf("<") > -1) {
							var val = $($('.richtextfield').val()).text();
							$('.richtextfield').val(val);
						}
					} catch (e) {
					}
					
				}
				
				if (title != null) {
					
					$(".title").html(title);
					
				}
				return false;
			});
}

function saveDocument(formid, unid, viewxpagename, formname, parentunid, dbname) {
	var data = $(".customform :input").serialize();
	//var url = 'UnpSaveDocument.xsp?unid=' + unid + "&formname=" + formname
		//	+ "&rnd=" + Math.floor(Math.random() * 1001);
	//ML: use custom save doc form
	var url = 'UnpSaveDocumentEx.xsp?unid=' + unid + "&formname=" + formname
		+ "&rnd=" + Math.floor(Math.random() * 1001);
	if (parentunid){
		url += "&parentunid=" + parentunid;
	}
	if (dbname){
		url += "&dbname=" + dbname;
	}
	var valid = validate();
	if (valid) {
		$.ajax( {
			type : 'POST',
			url : url,
			data : data,
			cache : false,
			beforeSend : function() {
				console.log("About to open URL");
			}
		}).done(
				function(response) {
					//console.log(response.length);
					if (response.length == 32) {
						//openDocument(
						//		viewxpagename
						//				+ "?action=openDocument&documentId="
						//				+ response, "content");
						//initiscroll();
						
						//ML: go back to page from where this new document was opened
						if (viewxpagename == 'back') {
							goBack();
						} else if (viewxpagename=="UnpIncident.xsp") {
							//ML: incident activation - use ajax load
							loadPageEx(
									viewxpagename + "?action=openDocument&documentId=" + response, 
									"contentwrapper", 
									null,
									true,
									true,
									function() { alert('Your BCM team has been activated')}
								);
							
							if (arguments.length<4) {
								loadFooter = true;
								loadHeader = true;
							}
							
						} else if (viewxpagename.indexOf("UnpIncident.xsp") > -1 ) {
							//save of a notification message
							loadPageEx(viewxpagename, "contentwrapper", null, true, true);
						} else {
							$.blockUI();
							window.location.href = viewxpagename + "?action=openDocument&documentId=" + response;
						}
					} else {
						alert(response);
					}
				});
	} else {
		return false;
	}
}

function validate() {
	var valid = true;
	$(".required").each( function() {
		var $this = $(this);
		var v = $this.val();
		//ML: can't add 'empty' values to computed combos in unplugged
		if (v == "" || v =="~") {
			var label = $("label[for='" + $this.attr('id') + "']");
			var type = $this.attr('type');
			
			alert(( type == 'file' ? "Please select a " + label.text().toLowerCase() : "Please complete " + label.text()) );
			$this.focus();
			valid = false;
			
			//ML 3/6/14: abort after first invalid field
			return valid;
			
		}
	})
	return valid;
}

function toggleViewsMenu() {
	if ($("#menuPane").hasClass("offScreen")) {
		$("#menuPane").removeClass("offScreen").addClass("onScreen");
		$("#menuPane").animate( {
			"left" : "+=700px"
		}, 400);
		//$("#content").fadeOut();
	} else {
		$("#menuPane").removeClass("onScreen").addClass("offScreen");
		$("#menuPane").animate( {
			"left" : "-=700px"
		}, 400);
		//$("#content").fadeIn();
	}
}

function hideViewsMenu() {
	if (!$("#menuPane").hasClass("offScreen")) {
		$("#menuPane").removeClass("onScreen").addClass("offScreen");
		$("#menuPane").animate( {
			"left" : "-=700px"
		}, "slow");
	}
	//$("#content").fadeIn();
}

var firedrequests;
function loadPage(url, target, menuitem) {
	var thisArea = $("#" + target);
	thisArea.load(url, function() {

		if (firedrequests != null) {
			firedrequests = new Array();
		}
		initiscroll();
		return false;
	});
	var menuitems = $("#menuitems li");
	menuitems.removeClass("viewMenuItemSelected");
	menuitems.addClass("viewMenuItem");
	$(".menuitem" + menuitem).removeClass("viewMenuItem");
	$(".menuitem" + menuitem).addClass("viewMenuItemSelected");
	hideViewsMenu();
}

function load(url) {
	loadPageEx(url, "contentwrapper", null, false, false, null);
}

//ML: extended loadPage to also update the header title and footer content
function loadPageEx(url, target, menuitem, loadFooter, loadHeader, callback) {

	storeRequest(url);
	
	if (url.indexOf(" ")==-1) {
		url += " .iscrollcontent";		//only load content part
	}
	
	var thisArea = $("#" + target);
	
	if (arguments.length<4) {
		loadFooter = true;
		loadHeader = true;
	}
	
	thisArea.load(url, function(data) {
		
		if (firedrequests != null) {
			firedrequests = new Array();
		}
		
		//extract footer content from ajax request and update footer
		if (loadFooter) {
			
			var footerNode = $(data).find(".footer");
			if (footerNode) {
				$(".footer").html( footerNode );
			}
		}
		
		//update header
		if (loadHeader) {
			
			var h = $(data).find('.iHeader').html();
			if (h) {
				$(".iHeader").html(h);
				
				//re-add onclick event to views button
				$('.viewsButton').unbind('click');
				$('.viewsButton').click( function(event) {
					toggleViewsMenu();
					return false;
				});
			}
		}
		
		initiscroll();
		
		unp.allowFormsInIscroll();
		
		if (url.indexOf('UnpActiveTasks')>-1 || url.indexOf('UnpIncidents') > -1 ) {
			
			//load live (first) cat
			var _li = $('#summaryList li:first');
			if( _li.text() == 'Live incidents') { 
				_li.click();
			}
			
		}

		//doKeyboardScrollFix();

		$("img.lazy").lazy();

		//optional callback function after loading a page
		if (callback != null) {
			callback.call(this);
		}
		
		return false;
	});
	
	if (menuitem != null && menuitem != 0) {
		
		var menuitems = $("#menuitems li");
		
		if (menuitems.length) {		//menuitems exist
			menuitems.removeClass("viewMenuItemSelected");
			menuitems.addClass("viewMenuItem");
			$(".menuitem" + menuitem).removeClass("viewMenuItem");
			$(".menuitem" + menuitem).addClass("viewMenuItemSelected");
		}
	}
	
	hideViewsMenu();
}

var _ajaxRequests = [];

//ML: store a string in the ajaxRequests array
//the array cannot grow larger than X items
function storeRequest(url) {
	
	//console.log(" store " + url);
	
	_ajaxRequests.push(url);
	
	if (_ajaxRequests.length > 10 ) {
		_ajaxRequests.splice(0,1);
	}
	
}

function getFromId() {
	
	var fromId = "";
	
	if (_ajaxRequests.length>0 ) {
		
		var t = _ajaxRequests[_ajaxRequests.length - 1]; 
		
		if (t.indexOf('&documentId=')>-1) {
			fromId = '&fromId=' + t.substring( t.indexOf('&documentId=')+'&documentId='.length );
		}
	}
	
	return fromId;
}

//ML: open the page that was shown before the current
function goBack() {
	
	var pos = _ajaxRequests.length - 2;
	var url;
	
	if (pos < 0 ) {
		url = window.location.pathname + window.location.search;
	} else {
		
		url = _ajaxRequests[pos];
		_ajaxRequests.splice(pos, 2);
		
	}
	
	loadPageEx( url , 'contentwrapper', null, true, true);
}


function openPage(url, target) {
	$.blockUI();
	document.location.href = url;
}

function initiscroll() {

	try {
		pullUpEl = document.getElementById('pullUp');
		pullUpOffset = pullUpEl.offsetHeight;
	} catch (e) {
	}
	
	$('.iscrollcontent').bind('scroll', function() {
			
		if ($(this).scrollTop() + $(this).innerHeight() >= $(this)[0].scrollHeight) {
			if (pullUpEl) {
				pullUpEl.className = 'flip';
				pullUpEl.querySelector('.pullUpLabel').innerHTML = 'Release to refresh...';
				if (pullUpEl.className.match('flip')) {
					pullUpEl.className = 'loading';
					pullUpEl.querySelector('.pullUpLabel').innerHTML = 'Loading...';
					$(".loadmorebutton").click();
				}
			}
		}
	});
	
}

function openDialog(id){
	$(id).css('display', 'block');
	$("#adminCover").css('display', 'block');
	initiscroll();
}

function closeDialog(id){
	$(id).css('display', 'none');
	$("#adminCover").css('display', 'none');
	initiscroll();
}


function accordionLoadMore(obj, viewName, catName, xpage, dbname){
	
	var thisArea = $(obj).nextAll(".summaryDataRow:first").children(".accordionRowSet");		
	var pos = $(thisArea).find('li').length;
	thisArea.css('display','block');
	var thisUrl = "UnpAccordionViewList.xsp?chosenView=" + encodeURIComponent(viewName) + "&catFilter=" + encodeURIComponent(catName) + "&xpageDoc=" + xpage + "&start=" + pos + "&dbname=" + dbname; 
	
	var tempHolder = $(obj).nextAll(".summaryDataRow:first").children(".summaryDataRowHolder");
	$(tempHolder).load(thisUrl + " #results", function(){
		$(thisArea).append($(".summaryDataRow li"));

		//check if there's only 1 expanded category and update the bottom border
		if ( $("#summaryList .categoryrow").length == 1 ) {
			$("#summaryList div.summaryDataRow ul.accordionRowSet li:last-child").addClass("roundedBottom");
		}
		
		if ($(tempHolder).text().indexOf("NOMORERECORDS") > -1){
			$(obj).nextAll(".summaryDataRow:first").children(".accLoadMoreLink").hide();
		}else{
			$(obj).nextAll(".summaryDataRow:first").children(".accLoadMoreLink").removeClass('hidden').show();	
		}
		$(tempHolder).empty();
		try{
			initiscroll();
		}catch(e){}
	});
	
	
	$(obj).addClass("accordianExpanded");
	$(obj).nextAll(".summaryDataRow:first").children(".accLoadMoreLink").show();

}

function fetchDetails(obj, viewName, catName, xpage, dbname)
{	
	$('.accordionRowSet').empty();
	$('.accLoadMoreLink').hide();
	
	//console.log('Category: ' + catName);
	if($(obj).hasClass("accordianExpanded")){
		$(obj).nextAll('.summaryDataRow:first').children('.accordionRowSet').slideUp('fast', function(){ $(this).children().remove()});
		$(obj).removeClass("accordianExpanded");
		$(obj).nextAll('.summaryDataRow:first').children('.accLoadMoreLink').hide();
	}
	else{
		$('.categoryRow').removeClass("accordianExpanded");
		accordionLoadMore(obj, viewName, catName, xpage, dbname);
	}
}

function fetchMoreDetails(obj, viewName, catName, xpage, dbname){
	
	var objRow = $(obj).parent().parent().prev();
	accordionLoadMore(objRow, viewName, catName, xpage, dbname);	
}

//jquery selector for xpages
function x$(idTag, param){ //Updated 18 Feb 2012
   idTag=idTag.replace(/:/gi, "\\:")+(param ? param : "");
   return($("#"+idTag));
}

//workaround for android v4.1 issue that onclick is called twice
var clickCalled = 0;

//expand/ collapse link
function showListDetails(srcNode) {
	
	var now = new Date().getTime();
	
	if (clickCalled > 0 && (now-clickCalled) < 750) {
		//abort if this function is called twice within a 750ms
		return;
	}
	
	clickCalled = now;
	
	//find the details node
	var parentLi = srcNode.closest('li')
	
	parentLi.children('.taskDetails').each( function() {
		
			if ( !$(this).is(":empty") ) {
	
				var $image = $(this).slideToggle(300, function() {
					
				}).siblings("img");
			
				if ($image.attr("src") == "unp/arrow-up.png") {
					   $image.attr("src", "unp/arrow-down.png");
				} else {
					 $image.attr("src", "unp/arrow-up.png");
				}
			}
		
		}
	);
	

}

//mark a task as done (from the tasks page), remove the button on completion
function markDone(doneId, undoneId, id) {
	
	x$(doneId).hide();
	x$(undoneId).show();
	
	$.ajax( {
		type : 'GET',
		url : "UnpProcess.xsp?type=task&to=done&id=" + id,
		cache : false
	}).done(
	function(response) {
		
		try {
			if (response.indexOf("error")>-1) {
				x$(doneId).show();
				x$(undoneId).hide();
				alert(response);
				
			}
		} catch (e) {
			console.log(e);
		}

	});
	
}

//mark a task as undone (from the tasks page), remove the button on completion
function markUndone(doneId, undoneId, id) {
	
	x$(doneId).show();
	x$(undoneId).hide();
	
	$.ajax( {
		type : 'GET',
		url : "UnpProcess.xsp?type=task&to=undone&id=" + id,
		cache : false
	}).done(
	function(response) {
		
		try {
			if (response.indexOf("error")>-1) {
				x$(doneId).hide();
				x$(undoneId).show();
				alert(response);
			}
		} catch (e) {
			console.log(e);
		}

	});
	
}

//deactivate an incident, reopen the incident when done
function deactivateIncident(id, numOpenTasks, returnTo) {
	
	//confirmation for open tasks
	if (numOpenTasks>0) {
		if (!confirm("This " + labelIncident.toLowerCase() + " has " + numOpenTasks + " open tasks. These will be closed automatically.\n\nAre you sure you want to continue?") ) {
			return;
		}
	}
	
	$.ajax( {
		type : 'GET',
		url : "UnpProcess.xsp?type=incident&id=" + id,
		cache : false
	}).done(
		function(response) {
			loadPageEx( returnTo, 'contentwrapper', null);
		}
	);
}

var syncFunc = function(data) {

	if ("true" === data) {
		
		setTimeout(function() { 
			$.get("/_$$unp/replStatus", syncFunc).error(			
				//ios doesn't have this page
				function() {
					reloadAfterSync();
				});
		}, 500);
			
	} else {
		reloadAfterSync();
	}
	
}

function reloadAfterSync() {

	//register handlers again
	$(document).ajaxStart($.blockUI).ajaxStop($.unblockUI);
	
	if ( _ajaxRequests.length > 0 ) {
		loadPageEx( _ajaxRequests[_ajaxRequests.length-1] , 'contentwrapper', null, true, false );
	} else { 
		location.reload();
	}
}

//function to initiate synchronisation of Continuity
function syncAllDbs(){
	
	//first unregister the ajaxstart/ stop handlers
	$(document).unbind('ajaxStart').unbind('ajaxStop');
	
	//now block the ui for the sync action
	$.blockUI({
		centerY: 0,
		css: { top: '10px', left: '10px', right: '' }
	});
	
	$.get("UnpSyncAll.xsp", syncFunc);
}

function getOpenTasksCount() {
	
	//call ajax page that returns all open tasks counts in JSON format
	
	//find (if any) span to add that count to
	
	//insert count
	
	
}

//remove click delay
function NoClickDelay(el) {
	if (el) {
		this.element = el;
		if( window.Touch ) this.element.addEventListener('touchstart', this, false);
	}
}

NoClickDelay.prototype = {
	handleEvent: function(e) {
		switch(e.type) {
			case 'touchstart': this.onTouchStart(e); break;
			case 'touchmove': this.onTouchMove(e); break;
			case 'touchend': this.onTouchEnd(e); break;
		}
	},

	onTouchStart: function(e) {
		e.preventDefault();
		this.moved = false;

		this.element.addEventListener('touchmove', this, false);
		this.element.addEventListener('touchend', this, false);
	},

	onTouchMove: function(e) {
		this.moved = true;
	},

	onTouchEnd: function(e) {
		this.element.removeEventListener('touchmove', this, false);
		this.element.removeEventListener('touchend', this, false);

		if( !this.moved ) {
			// Place your code here or use the click simulation below
			var theTarget = document.elementFromPoint(e.changedTouches[0].clientX, e.changedTouches[0].clientY);
			if(theTarget.nodeType == 3) theTarget = theTarget.parentNode;

			var theEvent = document.createEvent('MouseEvents');
			theEvent.initEvent('click', true, true);
			theTarget.dispatchEvent(theEvent);
		}
	}
};

(function($, window, document)
{
	$.fn.lazy = function(settings)
	{

		var items = this;
		
		lazyImgLoad();

		function lazyImgLoad() {
			
			items.each(function() {
				
				var element = $(this);

				if( element.attr("data-src") && 
					element.attr("data-src") != element.attr("src") && 
					!element.data("loaded") && 
					(element.is(":visible") || !true) ) {

					//load image and mark as loaded
					element
						.attr("src", element.attr("data-src") )
						.data("loaded", true)
						.removeAttr("data-src");
				}
			});

			//remove items that are already loaded
			items = $(items).filter(function()
			{
				return !$(this).data("loaded");
			});
		}

		return this;
	}

}
)(jQuery, window, document);


/*
 * Disable rubberbanding effect in iOS.
 * Based on the 'Baking Soda Paste' technique written by Armagan Amcalar at
 * http://blog.armaganamcalar.com/post/70847348271/baking-soda-paste
 */
unp.disableRubberBanding = function() {
   document.body.addEventListener('touchstart', function() {
        document.body.addEventListener('touchmove', function moveListener(e) {
            document.body.removeEventListener('touchmove', moveListener);

            var el = e.target;

            do {

                var h = parseInt(window.getComputedStyle(el, null).height, 10);
                var sH = el.scrollHeight;

                if (h < sH) {
                    return;
                }
            } while (el != document.body && el.parentElement != document.body && (el = el.parentElement));

            e.preventDefault();
        });
    });

}