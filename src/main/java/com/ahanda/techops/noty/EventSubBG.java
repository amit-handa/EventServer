package com.ahanda.techops.noty;

import java.util.*;	//map
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.jms.Destination;
import javax.jms.TextMessage;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSubBG extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventSubBG.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
    private String brokerURL;
    private String topic;
    private String subscription;
	private ConnectionFactory connF;
	private Connection conn;
	private Session sess;

	private Topic topicJ;
	private TopicSubscriber topicSub;

	@Override
	public void start() {
		JsonObject conf = container.config();
		JsonObject jmsConf = conf.getObject( "jms" );

		brokerURL = jmsConf.getString( "broker" );
		topic = jmsConf.getString( "topic" );
		subscription = jmsConf.getString( "subscription" );

		try {
			// Getting JMS connection from the server and starting it
			connF = new ActiveMQConnectionFactory( brokerURL );
			conn = connF.createConnection();
			conn.setClientID( conf.getString( "clientID" ) );
			conn.start();
			sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

			topicJ = sess.createTopic( topic );
			topicSub = sess.createDurableSubscriber( topicJ, subscription );
		} catch( Exception e ) {
			logger.error( "error starting EventSubBG verticle: {} {}", e.getMessage(), e.getStackTrace() );
		}

		final EventSubBG self = this;
		vertx.setTimer( 500, new Handler< Long >() {
			@Override
			public void handle( Long id ) {
				self.recvMsgs();
			}
		} );
	}

	public void recvMsgs() {
		while( true ) {
			try {
				logger.info( "Going into wait to receive message!" );
				javax.jms.Message msg = topicSub.receive();

				if (msg instanceof TextMessage) {
					TextMessage textMsg = (TextMessage) msg;
					logger.info( "Received message {}", textMsg.getText() );
					vertx.eventBus().publish( subscription, new JsonObject( textMsg.getText() ) );
				}
			} catch( JMSException e ) {
				logger.error( "Receiving Message JMS: {} {} {}", new Object[] { e.getErrorCode(), e.getMessage(), e.getStackTrace() } );
			} catch( Exception e ) {
				logger.error( "Receiving Message: {} {}", e.getMessage(), e.getStackTrace() );
			}
		}
	}

	public void stop() {
		try {
			if( conn != null ) conn.close();
		} catch( Exception e ) {
			logger.error( "Error in closing connection!" );
		}
	}
}
