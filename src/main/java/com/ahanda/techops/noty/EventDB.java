package com.ahanda.techops.noty;

import java.util.*;	//map

import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.Destination;
import javax.jms.TextMessage;
import javax.jms.MessageProducer;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.vertx.java.core.*;	// handler, asyncresult
import org.vertx.java.core.eventbus.*;	// message
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDB extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventDB.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
	private String dbdeployid;
	private JsonObject dbconf;
	private Handler< Message<JsonObject> > saveReplyH = new Handler< Message< JsonObject > >() {
		@Override
		public void handle( Message< JsonObject > msg ) {
			logger.info( "save result ! {} {}", msg.body().getString( "status" ), msg.body().getString( "message" ) );
		}
	};

	private Handler modDeployH = new Handler<AsyncResult< String > >() {
	  public void handle(AsyncResult<String> asyncResult) {
		  if (asyncResult.succeeded()) {
			  logger.info( "The mongo-module has been deployed, deployment ID is {}", asyncResult.result() );
			  dbdeployid = asyncResult.result();
		  } else {
			  asyncResult.cause().printStackTrace();
		  }
	  } };

	private Handler newEventsH = new Handler< Message< JsonObject > >() {
		@Override
		public void handle( Message< JsonObject > msg ) {
			JsonObject e = msg.body();
			newEvent( e );
		}
	};

	@Override
	public void start() {
		JsonObject conf = container.config();
		dbconf = conf.getObject( "db" );

		container.deployModule( "io.vertx~mod-mongo-persistor~2.1.1", dbconf, modDeployH );

		try {
			vertx.eventBus().registerHandler( conf.getObject( "jms" ).getString( "subscription" ), newEventsH );
		} catch( Exception e ) {
			logger.error( "error starting eventsub verticle: {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

	public void newEvent( JsonObject msg ) {
		logger.info( "Received new Event : {}", msg );
		JsonObject saveMsg = new JsonObject()
			.putString( "action", "save" )
			.putString( "collection", "events" )
			.putObject( "document", msg );
		vertx.eventBus().send( dbconf.getString( "address" ), saveMsg, saveReplyH );
	}

	public void stop() {
		logger.info( "Exiting EventDB !" );
		if( dbdeployid != null )
			container.undeployModule( dbdeployid );
	}
}
