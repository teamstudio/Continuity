/**
 * Copyright 2013 Teamstudio Inc 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 * the specific language governing permissions and limitations under the License
 */
$(window).load( function() {

	$(document).ajaxStart($.blockUI).ajaxStop($.unblockUI);
	allowFormsInIscroll();

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
	
	new NoClickDelay( document.getElementById('header') );
	new NoClickDelay( document.getElementById('menu') );
	new NoClickDelay( document.getElementById('footer') );
	
	try{
		$(".opendialoglink").click( function (event){
			openDialog($(this).attr('href'));
		});
	}catch(e){
		
	}
});

$(window).scroll( function() {
	if ($(window).scrollTop() + $(window).height() == $(document).height()) {
		$(".loadmorebutton").click();
	}
});

window.addEventListener("orientationchange", function() {
	hideViewsMenu();
	initiscroll();
}, false);

function allowFormsInIscroll() {
	[].slice.call(document.querySelectorAll('input, select, button, textarea')).forEach(function(el) {
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
			
			console.log("lets inser...")
			
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
			try {
				scrollContent.refresh();
			} catch (e) {
			}
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
					allowFormsInIscroll();
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
	scrollContent.scrollTo(0, -60, 0);
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
						$.blockUI();
						window.location.href = viewxpagename + "?action=openDocument&documentId=" + response;
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
		if ($(this).val() == "") {
			var label = $("label[for='" + $(this).attr('id') + "']");
			alert("Please complete " + label.text());
			$(this).focus();
			valid = false;
		}
	})
	return valid;
}

function toggleViewsMenu() {
	if ($("#menuPane").hasClass("offScreen")) {
		$("#menuPane").removeClass("offScreen").addClass("onScreen");
		$("#menuPane").animate( {
			"left" : "+=700px"
		}, "slow");
		//$("#content").fadeOut();
	} else {
		$("#menuPane").removeClass("onScreen").addClass("offScreen");
		$("#menuPane").animate( {
			"left" : "-=700px"
		}, "slow");
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

//ML: extended loadPage to also update the header title and footer content
function loadPageEx(url, target, menuitem, loadFooter, loadHeader) {

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
			var footerNode = $(data).find(".footer")
			$(".footer").html( footerNode );
		}
		
		//update header
		if (loadHeader) {
			var h = $(data).find('.iHeader').html();
			$(".iHeader").html(h);
			
			//re-add onclick event to views button
			$('.viewsButton').unbind('click');
			$('.viewsButton').click( function(event) {
				toggleViewsMenu();
				return false;
			});
			
		}
		
		initiscroll();
		
		allowFormsInIscroll();
		
		//lazy load images
		//$("img.lazy").lazyload({ threshold : 5 });
		
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
	
	_ajaxRequests.push(url);
	
	if (_ajaxRequests.length > 10 ) {
		_ajaxRequests.splice(0,1);
	}
	
}

//ML: open the page that was shown before the current
function goBack() {
	
	var url = _ajaxRequests[_ajaxRequests.length - 2];
	
	loadPageEx( url , 'contentwrapper', null, false, false );
}


function openPage(url, target) {
	$.blockUI();
	document.location.href = url;
}

var scrollContent = null;
var scrollMenu;
function initiscroll() {
	
	if (unpluggedserver){
		
		document.addEventListener('touchmove', function(e) {
			e.preventDefault()
		});
		// Initialise any iScroll that needs it
		try {
			pullUpEl = document.getElementById('pullUp');
			pullUpOffset = pullUpEl.offsetHeight;
		} catch (e) {
		}
		try {
			scrollContent.destroy();
			delete scrollContent;
		} catch (e) {
		}

		try {
			scrollMenu.destroy();
			delete scrollMenu;
		}catch(e){
		}
		try{
			scrollMenu = new iScroll('#menuWrapper', {bounce: true, momentum: false});
		}catch(e){}
		
		//if (!scrollContent) {
		
		$(".iscrollcontent")
				.each(
						function() {
							scrollContent = new iScroll(
									$(this).attr("id"),
									{
										useTransition : true,
										onRefresh : function() {
											if (pullUpEl) {
												if (pullUpEl.className
														.match('loading')) {
													pullUpEl.className = '';
													pullUpEl
															.querySelector('.pullUpLabel').innerHTML = 'Pull up to load more...';
												}
											}
										},
										onScrollMove : function() {
											if (pullUpEl) {
												if (this.y < (this.maxScrollY - 5)
														&& !pullUpEl.className
																.match('flip')) {
													pullUpEl.className = 'flip';
													pullUpEl
															.querySelector('.pullUpLabel').innerHTML = 'Release to refresh...';
													this.maxScrollY = this.maxScrollY;
												} else if (this.y > (this.maxScrollY + 5)
														&& pullUpEl.className
																.match('flip')) {
													pullUpEl.className = '';
													pullUpEl
															.querySelector('.pullUpLabel').innerHTML = 'Pull up to load more...';
													this.maxScrollY = pullUpOffset;
												}
											}
										},
										onScrollEnd : function() {
											if (pullUpEl) {
												if (pullUpEl.className
														.match('flip')) {
													pullUpEl.className = 'loading';
													pullUpEl
															.querySelector('.pullUpLabel').innerHTML = 'Loading...';
													$(".loadmorebutton").click();
												}
											}
										}
									});
						});
		
	}else{
		//alert("Not on unplugged so no iScroll");
	}
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
	
	console.log('Category: ' + catName);
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

//expand/ collapse link
function showListDetails(id) {

	var $div = x$(id);
	if ($div.text().length==0) { return; }		//no content to show
	
	var $image = $div.slideToggle(300, function() {
		//refresh iscroll
		scrollContent.refresh();
	}).siblings("img");

	if ($image.attr("src") == "unp/arrow-up.png") {
		   $image.attr("src", "unp/arrow-down.png");
	} else {
		 $image.attr("src", "unp/arrow-up.png");
	}

}


//mark a task as done (from the tasks page), remove the button on completion
function markDone(doneId, undoneId, id) {
	
	$.ajax( {
		type : 'GET',
		url : "unpProcess.xsp?type=task&to=done&id=" + id,
		cache : false
	}).done(
	function(response) {
		
		try {
			
			if (response.indexOf("error")>-1) {
				alert(response);
			} else 	{	
				x$(doneId).hide();
				x$(undoneId).show();
			}
		} catch (e) {
			console.log(e);
		}

	});
	
}

//mark a task as undone (from the tasks page), remove the button on completion
function markUndone(doneId, undoneId, id) {
	
	$.ajax( {
		type : 'GET',
		url : "unpProcess.xsp?type=task&to=undone&id=" + id,
		cache : false
	}).done(
	function(response) {
		
		try {
			if (response.indexOf("error")>-1) {
				alert(response);
			} else 	{	
				x$(doneId).hide();
				x$(undoneId).show();
			}
		} catch (e) {
			console.log(e);
		}

	});
	
}

//deactivate an incident, reopen the incident when done
function deactivateIncident(id, numOpenTasks) {
	
	//confirmation for open tasks
	/*if (numOpenTasks>0) {
		if (!confirm("This incident has " + numOpenTasks + " open tasks. These will be closed automatically.\n\nAre you sure you want to continue?") ) {
			return;
		}
	}*/
	
	$.ajax( {
		type : 'GET',
		url : "unpProcess.xsp?type=incident&id=" + id,
		cache : false
	}).done(
	function(response) {
		window.location.href = "mIncident.xsp?action=openDocument&documentId=" + id;
	});
}

var syncFunc = function(data) {
	
	$.blockUI();

	if ("true" === data) {
		
		setTimeout(function() { 
			$.get("/_$$unp/replStatus", syncFunc).error(
					//ios doesn't have this page
					function() { 
						
						if ( _ajaxRequests.length > 0 ) {
							loadPageEx( _ajaxRequests[_ajaxRequests.length-1] , 'contentwrapper', null, true, false );
						} else {
							location.reload();
						}
					});
		}, 500);
			
	}
	else {
		
		if ( _ajaxRequests.length > 0 ) {
			loadPageEx( _ajaxRequests[_ajaxRequests.length-1] , 'contentwrapper', null, true, false );
		} else {
			location.reload();
		}
	}
	
}

//function to initiate synchronisation of Continuity
function syncAllDbs(){
	$.blockUI({
		centerY: 0,
		css: { top: '10px', left: '10px', right: '' }
	});
	$.get("UnpSyncAll.xsp", syncFunc);
}

//remove click delay
function NoClickDelay(el) {
	this.element = el;
	if( window.Touch ) this.element.addEventListener('touchstart', this, false);
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
