function PintData() {
  prevESource = null;
  eventSources = null;
  allEvents = null;
}

var EventsProto = {
  add : function( events ) {
	for( var i = 0, len = events.length; i < len; i++ ) {
	  this.body.push( events[i] );
	}
  },

  html : function( events ) {
	var eview = '<table class="bordered-table"> <thead> <th>eId</th> <th>eTime</th> <th>status</th> <th>message</th> </thead>';
	var ebody = '<tbody data-bind="foreach: body"><td data-bind="text: eId.ids[0]"></td> <td data-bind="text: eTime"></td><td data-bind="text: status"></td><td data-bind="text: message"></td>';
	return eview + ebody + '</table>';
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

loadFeed = function( esid, data ) {
	var esname = esName( data );
	esname = esid + ':' + esname;
	var escontent = document.getElementById( esname );

	var pillc = document.getElementById( 'pill-content' );
	for( var esdivi = 0, allesdivs = pillc.childNodes.length; esdivi < allesdivs; esdivi++ ) {
	  var esdiv = pillc.childNodes[esdivi];
	  esdiv.style="display:none";
	}

	if( escontent == null ) {
	  escontent = document.createElement( "div" );
	  escontent.id = esname;
	  escontent.style = "display:block";

	  escontent.innerHTML = '<table class="bordered-table"> <thead> <th>' + esname + '</th> </thead></thead>';

	  pillc.appendChild( escontent );
	}
	escontent.style = "display:block";

	var events = allEvents.get( esid, data );
	console.info( "events " + esname + events );
	eb.send( 'PINT.events', { "http" : [ "post", "/pint/events/search" ],
		"body" : [ "EventCache", "findEvents", {
				"eventSource" : { "type" : esid, "body" : data },
				"events" : { "body" : events.filter }
		} ]
	  }, function(reply) {
	  reply = JSON.parse( reply );
	  if( events.body == undefined ) {
		events.body = ko.observableArray( reply );
		escontent.innerHTML = events.html();
		ko.applyBindings( events, document.getElementById( esname ) );

	  } else events.add( reply );
	  console.info( "events received ! " + reply );
	});
  };

function esName( esm ) {
  var esn = '';
  var i = 0;
  for( var k in esm ) {
	if( i++ )
	  esn += '_';
	esn += esm[k];
  }
  console.info( "esname: " + esn );
  return esn;
};

