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

public class EventPub extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventPub.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
	private String pubID;
    private String brokerURL;
    private String topic;
	private Connection conn;

	private EventBus eb;

	private boolean connected = false;
	private Handler unregH = new Handler< Message< JsonObject > >() {
	  @Override
	  public void handle( Message< JsonObject > msg ) {
		logger.info( "unregistered !" );
	  }
	};
	private Long retryTimer;

	private void createConnection() {
		ConnectionFactory connF = new ActiveMQConnectionFactory( brokerURL );

		try {
			eb.unregisterHandler( pubID, unregH );
			final Connection cconn = connF.createConnection();
			conn = cconn;

			cconn.setExceptionListener(new ExceptionListener() {
				@Override
				public void onException(JMSException e) {
					logger.error("JMS Error : {} {}", e.getMessage(), e.getStackTrace() );
					connected = false;
					eb.send("jms.pub.reconnect", "");
				}
			});
			cconn.start();

			final Session csess = cconn.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination dest = csess.createTopic( topic );
			final MessageProducer cprod = csess.createProducer( dest );

			Handler pubReqH = new Handler< Message< JsonObject > >() {
			  final Session sess = csess;
			  final MessageProducer prod = cprod;

			  @Override
			  public void handle( Message< JsonObject > msg ) {
				JsonObject eventsi = msg.body();
				JsonObject reply = publish( sess, prod, eventsi );
				msg.reply( reply );
			  }
			};

			vertx.eventBus().registerHandler( pubID, pubReqH );

			if( retryTimer != null ) {
			  vertx.cancelTimer( retryTimer );
			  retryTimer = null;
			}

			connected = true;
			logger.info("jms pub connected");
		} catch(Exception e) {
			logger.error("JMS Error : {} {}", e.getMessage(), e.getStackTrace() );
			connected = false;

			if( retryTimer == null ) {
			  retryTimer = vertx.setPeriodic( 10000, new Handler< Long >() {
				@Override
				public void handle( Long id ) {
				  createConnection();
				} } );
			}
		}
	}

	@Override
	public void start() {
		JsonObject conf = container.config();
		JsonObject jmsConf = conf.getObject( "jms" );

		brokerURL = jmsConf.getString( "broker" );
		topic = jmsConf.getString( "topic" );
		pubID = conf.getString( "clientID" ) + ".pub";

		eb = vertx.eventBus();

		eb.registerLocalHandler( "jms.pub.reconnect", new Handler<Message<String>>() {
			@Override
			public void handle(Message<String> event) {
				if(!connected)
					createConnection();
			}
		});

		createConnection();
	}

	public static JsonObject publish( Session session, MessageProducer prod, JsonObject eventsi ) {
	  logger.info( "PUBLISHING Messages {}", eventsi.size() );
	  JsonArray events = eventsi.getArray( "body" );
	  String stat = "ok";
	  StringBuilder details = new StringBuilder();
	  for( int i = 0; i < events.size(); i++ ) {
		JsonObject msg = events.get( i );
		try {
			TextMessage msgstr = session.createTextMessage( msg.encode() );

			// Here we are sending the message!
			prod.send( msgstr );
			logger.info("Sent message '" + msgstr.getText() + "'");
		} catch( Exception e ) {
			logger.error( "error in publishing message!" );
			details.append( ',' );
			details.append( i );
			stat = "error";
		}
	  }

	  JsonObject reply = new JsonObject().putString( "status", stat );
	  if( !details.toString().isEmpty() ) reply.putString( "details", details.toString() );

	  return reply;
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
