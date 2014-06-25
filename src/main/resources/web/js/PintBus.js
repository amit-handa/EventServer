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

	this.ebo.send( 'PINT.authMgr', {
		'userId' : uid,
		'password' : pword
	  }, function( reply ) {
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
		'userId' : uid,
		'sessStart' : stime,
		'sessAuth' : sessAuth
	  }, function( reply ) {
		verifyRes.call( vrthis, reply );
	  } );
  },

  openChannel: function( pd, openChanRes, octhis ) {
	this.ebo.send( 'PINT.webservice.sessionMgr'
	  , { "action" : "save", "collection" : "sessions",
		  "document" : { 'userId' : pd.userId(),
			'sessStart' : pd.sessStart + '',
			'sessAuth' : pd.sessAuth() } }
	  , function( reply ) {
		openChanRes.call( octhis, reply );
	  }
	);
  },

  getConf : function( pd, confResp, cthis ) {
	this.ebo.send( pd.sessAuth() + '.DB'
	  ,{ "action" : "find"
		, "collection" : "config"
		, "matcher" : { "_id" : "PINT" } }
	  ,function( reply ) {
		confResp.call( cthis, reply );
	  }
	);
  },

  getDBData : function( pd, action, dataResp, cthis ) {
	this.ebo.send( pd.sessAuth() + '.DB'
	  , action
	  ,function( reply ) {
		dataResp.call( cthis, reply );
	  }
	);
  },

  esourceSub : function( pd, esource ) {
	this.ebo.send( 'PINT.webservice.subsMgr'
	  , { action : 'save', collection : 'subscriptions', 'document' : { sessAuth : pd.sessAuth(), source : esource } }
	  ,function( reply ) {
		console.log( "Subscribed " + reply );
	  }
	);
  },


  regChannel : function( sessAuth, sessUpdates, suthis ) {
	var address = sessAuth + '.in';
	console.log( "Reg Channel: " + address );
	this.ebo.registerHandler( address, function( msg ) {
	  sessUpdates.call( suthis, msg );
	} );
  },

  getEvents : function( esdiv, eventsRes, ethis ) {
	var esrc = ko.dataFor( esdiv );
	console.log( "getevents " + esrc.source );
	this.ebo.send( pdata.sessAuth() + '.DB', {
	  'action' : 'find',
	  'collection' : 'events',
	  'matcher' : { "source" : esrc.source } }
	, function( events ) {
	  eventsRes.call( ethis, events, esdiv );
	} );
  }
}
