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