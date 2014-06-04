/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function DemoViewModel() {
  var that = this;
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

  var AllEventsProto = {
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
  that.allEvents = Object.create( AllEventsProto, {} );

  function EventSource( id, es ) {
	this.id = id;
	for( var prop in es ) {
	  this[prop] = es[prop];
	}
  }

  that.loadFeed = function( esid, data ) {
	var esname = that.esName( data );
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

	var events = that.allEvents.get( esid, data );
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

  that.esName = function( esm ) {
	var esn = '';
	var i = 0;
	for( var k in esm ) {
	  if( i++ )
		esn += '_';
	  esn += esm[k];
	}
	return esn;
  };

  var eb = new vertx.EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/spint');

  eb.onopen = function() {
	eb.send('PINT.events', { "http" : [ "post", "/pint/sessions" ],
	  "body" : "ahanda" }, function(reply) {
	  console.info( 'received ' + reply );
	  reply = JSON.parse( reply );
	  console.info( 'AHanda ' + reply.userConfig );

	  var eventSources = {};
	  var es = reply.toolConfig.eventSources;
	  for( var estype in es ) {
		  var tmp = new EventSource( estype, es[estype] );
		  eventSources[estype] = tmp;
	  }

	  that.eventSources = ko.observable( eventSources );

	  ko.applyBindings( that );
	});
  };

  eb.onclose = function() {
    eb = null;
  };
})();
