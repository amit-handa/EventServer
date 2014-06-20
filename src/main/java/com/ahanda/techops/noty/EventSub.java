package com.ahanda.techops.noty;

import java.util.*;	//map
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.Destination;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.vertx.java.core.*;	// handler, asyncresulthandler
import org.vertx.java.core.eventbus.*;	//eventbus
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSub extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventSub.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
	private String bgdeployid;

	@Override
	public void start() {
		JsonObject conf = container.config();
		JsonObject jmsConf = conf.getObject( "jms" );

		final EventSub self = this;
		container.deployWorkerVerticle( "com.ahanda.techops.noty.EventSubBG", conf, 1, true, new Handler<AsyncResult< String > >() {
			public void handle(AsyncResult<String> asyncResult) {
				if (asyncResult.succeeded()) {
					logger.info( "The verticle has been deployed, deployment ID is {}", asyncResult.result() );
					self.bgdeployid = asyncResult.result();
				} else {
					asyncResult.cause().printStackTrace();
				}
			} } );

		try {
			Handler< Message< JsonObject > > topicSubH = new Handler< Message< JsonObject > >() {
				@Override
				public void handle( Message< JsonObject > msg ) {
					JsonObject e = msg.body();
					self.newEvent( e );
				}
			};
			vertx.eventBus().registerHandler( jmsConf.getString( "subscription" ), topicSubH );
		} catch( Exception e ) {
			logger.error( "error starting eventsub verticle: {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

	public void newEvent( JsonObject msg ) {
		logger.info( "Received new Event : {}", msg );
	}

	public void stop() {
		logger.info( "Exiting EventSub !" );
		if( bgdeployid != null )
			container.undeployVerticle( bgdeployid );
	}
}
