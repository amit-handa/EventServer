function PintBus() {
  this.ebo = null;
};

PintBus.prototype = {
  init : function ( pdata ) {
	var self = this;
	self.ebo = new vertx.EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/spint');

	self.ebo.onopen = function() {
	  self.ebo.send('PINT.events', { "http" : [ "post", "/pint/sessions" ],
		"body" : "ahanda" }, function(reply) {
		console.info( 'received ' + reply );
		reply = JSON.parse( reply );
		console.info( 'AHanda ' + reply.userConfig );

		pdata.eventSources = new EventSources( reply.toolConfig );
		pdata.eventSources = ko.observable( pdata.eventSources );
		ko.applyBindings( pdata );
	  });
	};

	self.ebo.onclose = function() {
	  console.log( 'Bus closed ... re-initing' );
	  timer = setTimeout( function(){ initPintBus( pdata ) }, 5 * 1000 );
	  self.ebo = null;
	}
  },

  authenticate : function (uid, pword) {
	  console.log("authenticating.... : " + uid+ pword);

	  if(uid ==  '') {
		alert('invalid number');
		return;
	  }
	  this.ebo.send('pint.auth', {
		'user' : uid,
		'pass' : pword
	  }, function(reply) {
		console.log(reply.stat);
		console.log(reply.token);
		if(reply.stat === 'ok') {
		  console.log('invalid login');
		  alert('invalid login');
		  return;
		}

		TOKEN = reply.token;
		UID = reply.uid;
		SELF_NUM = uid;
		console.log("You logged in as " + uid + " and a password of " + pword);
		setCookie("uid", SELF_NUM, 1);
		setCookie("token", TOKEN, 1);
		console.log("setCookie");
		
		register(TOKEN);
		
		//loadAccount();
	  });
	}
}
