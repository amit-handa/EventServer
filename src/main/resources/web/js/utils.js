console.log("strt utils");

function getCookie( c_name ) {
  var cookies = document.cookie;
  var c_start = cookies.indexOf( " " + c_name + "=" );

  if ( c_start == -1 ) {
	c_start = cookies.indexOf( c_name + "=" );
  }

  if ( c_start == -1 ) {
	cookies = null;
  } else {
	c_start = cookies.indexOf( "=", c_start ) + 1;
	var c_end = cookies.indexOf( ";", c_start );
	if ( c_end == -1 ) {
		c_end = cookies.length;
	}
	cookies = unescape( cookies.substring( c_start, c_end ) );
  }

  console.log( "found cookie-->  " + c_name + " : " + cookies )

  return cookies;
}

function setCookie(c_name, value, exdays) {
  var exdate = new Date();
  exdate.setDate(exdate.getDate() + exdays);
  var cookies = escape(value) + ((exdays == null) ? "" : "; expires=" + exdate.toUTCString());
  console.log( 'set-cookie ' + c_name + ' * ' + value );
  document.cookie = c_name + "=" + cookies;
}

function initAuth() {
  var userId = getCookie( "userId" );
  var sessAuth = getCookie( "sessAuth" );

  if( !userId || !sessAuth ) {
	console.log("No cookie");
	loadLogin();
  } else {
	var stime = getCookie( "sessStart" );
	pbus.verify( userId, stime, sessAuth, pdata.verifyRes, pdata );
  }
}

function deleteCookie(c_name) {
  document.cookie = encodeURIComponent( c_name ) + "=deleted; expires=" + new Date(0).toUTCString();
}

function loadLogin() {
  document.getElementById('login').style.display = '';
  document.getElementById('content').style.display = 'none';
  console.log("login loaded");
}

function login() {
  console.info("login !!!!!!!!!!!)");

  var userId = $('#userId').val().trim();
  var password = $('#password').val().trim();
  console.info("sending : "+ userId + password);

  pbus.authenticate( userId, password, pdata.loginRes, pdata );
  $('#userId').val('');
  $('#password').val('');
}

function signOut() {
  deleteCookie("sessStart");
  deleteCookie("sessAuth");
  location.reload();
  return false;
}

function loadUser() {
  //document.getElementById('login').style.display = 'none';
  document.getElementById('content').style.display = '';
  console.log("account loaded");
}

console.log("end utils");
