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

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.*;	// message
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventPub extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventPub.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
    private String brokerURL;
    private String topic;
	private ConnectionFactory connF;
	private Connection conn;
	private Session sess;
	private Destination dest;
	private MessageProducer prod;

	@Override
	public void start() {
		JsonObject conf = container.config();
		JsonObject jmsConf = conf.getObject( "jms" );

		brokerURL = jmsConf.getString( "broker" );
		topic = jmsConf.getString( "topic" );

		try {
			// Getting JMS connection from the server and starting it
			connF = new ActiveMQConnectionFactory( brokerURL );
			conn = connF.createConnection();
			conn.start();

			sess = conn.createSession( false, Session.AUTO_ACKNOWLEDGE );
			dest = sess.createTopic( topic );
			prod = sess.createProducer( dest );

			final EventPub self = this;
			Handler< Message< JsonObject > > pubTopicH = new Handler< Message< JsonObject > >() {
				@Override
				public void handle( Message< JsonObject > msg ) {
					JsonObject e = msg.body();
					self.publish( e );
				}
			};
			vertx.eventBus().registerHandler( conf.getString( "clientID" ) + ".pub", pubTopicH );
		} catch( Exception e ) {
			logger.error( "error starting eventpub verticle: {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

	public void publish( JsonObject msg ) {
		try {
			TextMessage msgstr = sess.createTextMessage( msg.encode() );

			// Here we are sending the message!
			prod.send( msgstr );
			logger.info("Sent message '" + msgstr.getText() + "'");
		} catch( Exception e ) {
			logger.error( "error in publishing message!" );
		}
	}

	public void stop() {
		logger.info( "Exiting EventPub !" );
		try {
			if( conn != null ) conn.close();
		} catch( Exception e ) {
			logger.error( "Error in closing jms connection!" );
		}
	}
}
