function PintBus() {
  this.ebo = null;
};

PintBus.prototype = {
  init : function ( busOpen, bothis ) {
	var self = this;
	this.ebo = new vertx.EventBus( window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/spint');

	this.ebo.onopen = function() {
	  busOpen.call( bothis );
	};

	self.ebo.onclose = function() {
	  console.log( 'Bus closed ... re-initing' );
	  timer = setTimeout( function(){ self.init( busOpen, bothis ) }, 5 * 1000 );
	  self.ebo = null;
	}
  },

  authenticate : function ( uid, pword, loginRes, lrthis ) {
	console.log("authenticating.... : " + uid+ pword);

	if(uid ==  '') {
	  alert('invalid number');
	  return;
	}

	this.ebo.send('PINT.authMgr', {
	  "http" : [ "post", "/pint/login" ],
	  "body" : {
		'userId' : uid,
		'password' : pword
	  } }, function( reply ) {
	  loginRes.call( lrthis, reply );
	} );
  },

  verify : function (uid, stime, sessAuth, verifyRes, vrthis ) {
	console.log("verifying.... : " + uid + sessAuth);

	if( !uid ) {
	  console.log( "uid is null/undef. Cannot verify" );
	  return false;
	}

	this.ebo.send('PINT.authMgr', {
	  "http" : [ "post", "/pint/login" ],
	  "body" : {
		'userId' : uid,
		'sessStart' : stime,
		'sessAuth' : sessAuth
	  } }, function( reply ) {
		verifyRes.call( vrthis, reply );
	  } );
  },

  openChannel: function( pd, openChanRes, octhis ) {
	this.ebo.send( 'PINT.authMgr',
	  { "http" : [ "post", "/pint/sessions" ],
		"body" : { 'userId' : pd.userId(),
		  'sessStart' : pd.sessStart + '',
		  'sessAuth' : pd.sessAuth() } },
	  function( reply ) {
		openChanRes.call( octhis, reply );
	  }
	);
  },

  getConf : function( pd, confResp, cthis ) {
	this.ebo.send( pd.sessAuth()
	  ,{ "http" : [ "post", "/pint/config" ],
		"body" : { "action" : "find"
		  , "collection" : "config"
		  , "matcher" : { "_id" : "PINT" } } }
	  ,function( reply ) {
		confResp.call( cthis, reply );
	  }
	);
  },

  getDBData : function( pd, action, dataResp, cthis ) {
	this.ebo.send( pd.sessAuth()
	  ,{ "http" : [ "post", "/pint/DBData" ],
		"body" : action }
	  ,function( reply ) {
		dataResp.call( cthis, reply );
	  }
	);
  },

  regChannel : function( sessAuth, sessUpdates, suthis ) {
	var address = "in." + sessAuth;
	console.log( "Reg Channel: " + address );
	this.ebo.registerHandler( address, function( msg ) {
	  sessUpdates.call( suthis, msg );
	} );
  },

  getEvents : function( esdiv, eventsRes, ethis ) {
	var esrc = ko.dataFor( esdiv );
	console.log( "getevents " + esName( esrc.esid ) );
	this.ebo.send( pdata.sessAuth(), { "http" : [ "post", "/pint/events/search" ],
	  "body" : [ "EventCache", "findEvents",
		{ "eventSource" : esrc.esid, "events" : {
		  "cycleDate" : "Feb 10 2014 00:00:00",
		  "body" : 'true' } }
	  ]
	}, function( events ) {
	  eventsRes.call( ethis, events, esdiv );
	} );
  }
}
