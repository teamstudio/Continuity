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