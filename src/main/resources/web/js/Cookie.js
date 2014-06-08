function getCookie(c_name) {
	var c_value = document.cookie;
	var c_start = c_value.indexOf(" " + c_name + "=");
	if (c_start == -1) {
		c_start = c_value.indexOf(c_name + "=");
	}
	if (c_start == -1) {
		c_value = null;
	} else {
		c_start = c_value.indexOf("=", c_start) + 1;
		var c_end = c_value.indexOf(";", c_start);
		if (c_end == -1) {
			c_end = c_value.length;
		}
		c_value = unescape(c_value.substring(c_start, c_end));
	}
	console.log("found cookie-->  " + c_name + " : " + c_value)
	return c_value;
}

function setCookie(c_name, value, exdays) {
	var exdate = new Date();
	exdate.setDate(exdate.getDate() + exdays);
	var c_value = escape(value) + ((exdays == null) ? "" : "; expires=" + exdate.toUTCString());
	document.cookie = c_name + "=" + c_value;
}

function checkCookie() {
	var uid = getCookie("uid");
	if (uid != null && uid != "") {
		var token = getCookie("token");
		if(verify(uid, token) == true) {
			//chatDisplay(uid);
			TOKEN = token;
			SELF_NUM = uid;
			/*
			register(TOKEN);
			alert("You logged in as " + user + " and a password of " + pword);
			loggedIn = true;
			*/
			alert("Welcome again " + uid);
			return true;
		} 
	} else {
		console.log("no cookie");
		loadLogin();
		return false;		
	}
}

function deleteCookie(c_name) {
	document.cookie = encodeURIComponent(c_name) + "=deleted; expires=" + new Date(0).toUTCString();
}
