package com.ahanda.techops.noty;

import java.util.*;	//map

import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.Destination;
import javax.jms.TextMessage;
import javax.jms.MessageProducer;
import javax.jms.JMSException;
import javax.jms.ExceptionListener;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.*;	// message
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMgr extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventMgr.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
	private EventBus eb;
	private JsonObject conf;
	private Map< String, Set< String > > esrcSubs = new HashMap< String, Set< String > >();

	private Handler unregH = new Handler< Message< JsonObject > >() {
	  @Override
	  public void handle( Message< JsonObject > msg ) {
		logger.info( "unregistered !" );
	  }
	};

	private Handler pubReqH = new Handler< Message< JsonObject > >() {
	  @Override
	  public void handle( Message< JsonObject > msg ) {
		logger.info( "Received events !!! {}", msg.body() );
		JsonObject eventsi = msg.body();

		JsonArray eventsj = eventsi.getArray( "documents" );
		for( int i = 0; i < eventsj.size(); i++ ) {
		  JsonObject e = eventsj.get( i );
		  newEvent( e );
		}
		dbreqH.handle( msg );
	  }
	};

	public void newEvent( JsonObject e ) {
	  Set< String > activeSubs = esrcSubs.get( e.getString( "source" ) );
	  logger.info( "Active subs {}", activeSubs );
	  if( activeSubs == null )
		return;

	  for( String sessAuth : activeSubs ) {
		eb.send( sessAuth + ".in", e );
	  }
	}

	private static class DBRespH implements Handler< Message< JsonObject > > {
	  private JsonArray data = new JsonArray();
	  private Handler< JsonObject > procData;

	  public DBRespH( Handler< JsonObject > procData ) {
		this.procData = procData;
	  }

	  @Override
	  public void handle( Message< JsonObject > msg ) {
		JsonObject reply = msg.body();
		logger.info( "searchEvent Response; {} !", reply );
		JsonArray results = reply.getArray("results");

		for (Object el : results) {
			data.add(el);
		}

		if (reply.getString("status").equals("more-exist")) {
			msg.reply( new JsonObject(), this );
		} else {
		  reply.putArray( "results", data );
		  procData.handle( reply );
		}
	  }
	};

	private Handler< Message< JsonObject > > dbreqH = new Handler< Message< JsonObject > >() {
		@Override
		public void handle( Message< JsonObject > msg ) {
		  logger.info( "Received DB Request !!! {}", msg.body() );
			JsonObject dbreq = msg.body();
			String action = dbreq.getString( "action" );
			if( action.equals( "save" ) ) {
			  final JsonArray docs = dbreq.getArray( "documents" );
			  if( docs != null )
				dbreq.removeField( "documents" );

			  final Message< JsonObject > msgf = msg;
			  Handler< Message< JsonObject > > saveReplyH = new Handler< Message< JsonObject > >() {
				int i = 0;
				StringBuilder errMsg = new StringBuilder();

				@Override
				public void handle( Message< JsonObject > msg ) {
				  i++;
				  JsonObject reply = msg.body();
				  if( !reply.getString( "status" ).equals( "ok" ) )
					errMsg.append( reply.getString( "message" ) );

				  if( i == docs.size() ) {
					String errstr = errMsg.toString();
					msgf.reply( new JsonObject()
					  .putString( "status", errstr.isEmpty() ? "ok" : "error" )
					  .putString( "message", errstr ) );
				  }
				}
			  };

			  for( int i = 0; i < docs.size(); i++ ) {
				JsonObject doc = docs.get( i );
				JsonObject cdbreq = dbreq.copy();
				cdbreq.putObject( "document", doc );
				eb.send( conf.getObject( "db" ).getString( "address" ), cdbreq, saveReplyH );
			  }
			} else if( action.equals( "find" ) ) {  // find
			  final Message msgf = msg;
			  Handler< JsonObject > procData = new Handler< JsonObject >() {
				@Override
				public void handle( JsonObject data ) {
				  msgf.reply( data );
				}
			  };

			  DBRespH dbresph = new DBRespH( procData );

			  eb.send( conf.getObject( "db" ).getString( "address" ), dbreq, dbresph );
			} else {  // other
			  final Message msgf = msg;
			  Handler< Message< JsonObject > > procData = new Handler< Message< JsonObject > >() {
				@Override
				public void handle( Message< JsonObject > reply ) {
				  msgf.reply( reply.body() );
				}
			  };

			  eb.send( conf.getObject( "db" ).getString( "address" ), dbreq, procData );
			}
		}
	};

	private Handler subsMgrH = new Handler< Message< JsonObject > >() {
		@Override
		public void handle( Message< JsonObject > msg ) {
		  JsonObject cmsg = msg.body();

		  logger.info( "Received sub/sess update: {}", cmsg );
		  JsonObject reply = null;
		  String collection = cmsg.getString( "collection" );
		  String action = cmsg.getString( "action" );
		  String sessAuth = cmsg.getObject( "document" ).getString( "sessAuth" );
		  if( collection.equals( "sessions" ) ) {
			if( action.equals( "save" ) ) {
			  eb.registerHandler( sessAuth + ".subsMgr", this );
			  eb.registerHandler( sessAuth + ".DB", dbreqH );
			} else {  // "delete"
			  eb.unregisterHandler( sessAuth + ".subsMgr", this );
			  eb.unregisterHandler( sessAuth + ".DB", dbreqH );
			}
		  } else {	// "subscriptions"
			String esrc = cmsg.getObject( "document" ).getString( "source" );

			Set< String > sessAuths = esrcSubs.get( esrc );
			if( action.equals( "save" ) ) {
			  if( sessAuths == null ) {
				sessAuths = new HashSet< String >();
				esrcSubs.put( esrc, sessAuths );
			  }
			  sessAuths.add( sessAuth );
			} else {	// delete
			  if( sessAuths != null )
				sessAuths.remove( sessAuth );
			}
			reply = new JsonObject().putString( "status", "ok" );
			msg.reply( reply );
		  }
		}
	};

	@Override
	public void start() {
		conf = container.config();
		eb = vertx.eventBus();

		eb.registerHandler( conf.getString( "clientID" ) + ".events.new", pubReqH );
		eb.registerHandler( conf.getString( "clientID" ) + ".subsMgr", subsMgrH );
		eb.registerHandler( conf.getString( "clientID" ) + ".DB", dbreqH );
		eb.registerHandler( conf.getString( "sessions.channel" ), subsMgrH );
	}

	public void stop() {
	  logger.info( "Exiting EventMgr !" );
	}
}
