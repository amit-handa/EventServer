function PintData() {
  this.uconf = undefined;
  this.userId = ko.observable( '' );
  console.log( "init pintdata " + this.userId() );
  this.sessStart = undefined;
  this.sessAuth = ko.observable( '' );

  this.prevESource = null;
  this.eventSources = null;
  this.allEvents = {};
  this.pesdiv = null;
}

PintData.prototype = {
  showEvents : function( esdiv ) {
	  pbus.getEvents( esdiv, this.eventsRes, this );
  },

  eventsRes : function( events, esdiv ) {
	var esdivid = esdiv.innerHTML;
	console.log( "received events !!! " + events + ' # ' + esdivid );
	if( this.pesdiv ) {
	  if( this.pesdiv == esdiv )
		console.log( "Equalizer!" );
	  else console.log( "Inequality abounds!" );
	}
	this.pesdiv = esdiv;

	var allEventsTable = document.getElementById( 'esource-events' );
	/*console.log( 'child nodes: ' + allEventsTable.toString() );
	for( var esdivi = 1, allesdivs = allEventsTable.childNodes.length; esdivi < allesdivs; esdivi++ ) {
	  var tmp = allEventsTable.childNodes[esdivi];
	  console.log( 'cnode: ' + eskey( tmp ) );
	  if( tmp.hasAttribute( "class" ) )
		tmp.removeAttribute( "class" );
	}*/

	events = JSON.parse( events );
	var data = ko.dataFor( esdiv );
	var esrcInfo = this.allEvents[esdivid];
	console.log( "checking div " + esrcInfo + ' # ' + eskey( this.allEvents ) );
	if( !esrcInfo ) {
	  console.log( "creating esrc tab !!! " + esdiv );
	  var esname = esName( data.esid );
	  var esrcTab = document.getElementById( "esource-tabs" );
	  var tabentry = document.createElement( "li" );
	  tabentry.innerHTML = "<a href='#" + esname + "-events'>" +  esname + "</a>";
	  esrcTab.appendChild( tabentry );

	  console.log( "creating esrc events !!! " + esdiv );
	  eventsTable = document.createElement( "div" );

	  esrcInfo = this.allEvents[esdivid] = {};

	  esrcInfo.esrcTab = tabentry;
	  esrcInfo.events = eventsTable;
	  esrcInfo.esrcItem = esdiv;

	  eventsTable.id = esname + "-events";
	  //eventsTable.setAttribute( "class", "active" );
	  eventsTable.innerHTML = EventsProto.html();
	  allEventsTable.appendChild( eventsTable );

	  ko.applyBindings( { body : ko.observableArray( events ) }, eventsTable );
	}

	//esrcInfo.events.setAttribute( "class", "active" );
	esrcTabClick.call( esrcInfo.esrcTab.childNodes[0] );
	//$('.tabs').tabs();
  },

  busOpen : function() {
	initAuth();
	ko.applyBindings( this, document.getElementById( 'login' ) );
	ko.applyBindings( this, document.getElementById( 'loggedIn' ) );
  },

  loginRes : function( reply ) {
	reply = JSON.parse( reply );
	console.log( "Login res: " + reply.stat + reply.sessStart + reply.sessAuth );

	if( reply.stat != 'OK' ) {
	  console.log('invalid login');
	  alert('invalid login');
	  return;
	}
	this.signInSuccess( reply );
  },

  verifyRes : function( reply ) {
	console.log( reply.stat );
	if(reply.stat != 'ok') {
	  console.log("invalid sessAuth");
	  loadLogin();
	  return false;
	}

	this.signInSuccess( reply );
  },

  signInSuccess : function( reply ) {
	this.sessAuth( reply.sessAuth );
	this.userId( reply.userId );
	this.sessStart = reply.sessStart;

	console.log( 'signin ' + reply.sessAuth + ' # ' + this.sessAuth() );
	setCookie( "userId", reply.userId, 1 );
	setCookie( "sessStart", reply.sessStart, 1);
	setCookie( "sessAuth", this.sessAuth(), 1);

	console.log("setCookie");

	loadUser();

	pbus.openChannel( this, this.initChannel, this );
  },

  initChannel : function( reply ) {
	console.info( 'initChannel begin: ' + reply );
	reply = JSON.parse( reply );
	if( reply.stat != 'OK' ) {
	  console.log( 'Session Creation Failed' );
	  return;
	}

	pbus.regChannel( this.sessAuth(), this.sessUpdates, this );
	pbus.getConf( this, this.confResp, this );
  },

  confResp : function( reply ) {
	console.log( "ConfResp: " + reply );
	reply = JSON.parse( reply );
	this.eventSources = new EventSources( reply.toolConfig );
	this.eventSources = ko.observable( this.eventSources );
	ko.applyBindings( this, document.getElementById( 'content' ) );
  },

  loadUConfRes : function( reply ) {
	// list of user subscriptions
	console.log( 'user conf loaded' );
	uconf = reply;

	// load feeds for each subscription
  },

  sessUpdates : function( msg, replyTo ) {
	console.log( "received message!!" );
	console.log( msg );
	this.getMessage( msg ); // /Need to be created
  },

  getMessage : function( msg ) {
	console.log( "message is here !" );
  }
};

var EventsProto = {
  add : function( events ) {
	for( var i = 0, len = events.length; i < len; i++ ) {
	  this.body.push( events[i] );
	}
  },

  html : function() {
	var eview = '<div class="span12"><table class="bordered-table"> <thead> <th>eId</th> <th>eTime</th> <th>status</th> <th>message</th> </thead>';
	var ebody = '<tbody data-bind="foreach: body"><td data-bind="text: eId.ids.join( \':\' )"></td> <td data-bind="text: eTime"></td><td data-bind="text: status"></td><td data-bind="text: message"></td>';
	return eview + ebody + '</table></div>';
  }
};

function EventSources( esources ) {
  var self = this;
  self.openState = ko.observable( { focussed : false, shouldOpen : false } );
  for( var estype in esources ) {
	if( estype != 'children' )
	  self[estype] = esources[estype];
  }

  if( esources.children ) {
	var tmp = [];
	for( var i = 0, len = esources.children.length; i < len; i++ ) {
	  tmp.push( new EventSources( esources.children[i] ) );
	}
	self.children = ko.observableArray( tmp );
  }
};

EventSources.prototype = {
  toggle : function( esrc, e ) {
	console.info( "togggggge! " + esName( esrc.esid ) + esName( this.esid ) );
	var shouldOpen = esrc.openState().shouldOpen;
	this.openState( { focussed : true, shouldOpen : !shouldOpen } );
  },
  get : function( esid, data ) {
	console.info( "get events : " + esid + data );

	var esidC = this[esid];
	if( esidC == null )
	  esidC = this[esid] = {};

	var events = esidC[data];
	if( events == null ) {
	  events = esidC[data] = Object.create( EventsProto, {} );
	  events.filter = '#cmpDate( #e.eTime, "Feb 10 2014 02:11:16") > 0';
	  events.body = undefined;
	}

	console.info( "return " + events );
	return events;
  }
};

