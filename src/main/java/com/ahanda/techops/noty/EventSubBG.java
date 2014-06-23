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
import javax.jms.ExceptionListener;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.core.eventbus.*;	//EventBus
import org.vertx.java.platform.*;	//Container;Verticle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSubBG extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( EventSubBG.class.getName() );

    // URL of the JMS server. DEFAULT_BROKER_URL will just mean
    // that JMS server is on localhost
    private String brokerURL;
    private String clientID;
    private String topic;
    private String subscription;

	private EventBus eb;

	private ConnectionFactory connF;
	private Connection conn;
	private Session sess;

	private Topic topicJ;
	private TopicSubscriber topicSub;
	private boolean connected = false;
	private Long retryTimer;

	private void createConnection() {
		connF = new ActiveMQConnectionFactory( brokerURL );

		try {
			conn = connF.createConnection();
			conn.setClientID( clientID );
			conn.start();

			conn.setExceptionListener(new ExceptionListener() {
				@Override
				public void onException(JMSException e) {
					logger.error("JMS Error : {} {}", e.getMessage(), e.getStackTrace() );
					connected = false;
					eb.send("jms.reconnect", "");
				}
			});

			sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

			topicJ = sess.createTopic( topic );
			topicSub = sess.createDurableSubscriber( topicJ, subscription );
			connected = true;
			logger.info("jms connected");

			if( retryTimer != null ) {
			  vertx.cancelTimer( retryTimer );
			  retryTimer = null;
			}

			recvMsgs();
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

		clientID = conf.getString( "clientID" );
		brokerURL = jmsConf.getString( "broker" );
		topic = jmsConf.getString( "topic" );
		subscription = jmsConf.getString( "subscription" );

		eb = vertx.eventBus();

		eb.registerLocalHandler( "jms.reconnect", new Handler<Message<String>>() {
			@Override
			public void handle(Message<String> event) {
				if(!connected)
					createConnection();
			}
		});

		connF = new ActiveMQConnectionFactory( brokerURL );
		createConnection();
	}

	public void recvMsgs() {
		boolean err;
		while( true ) {
			err = false;
			try {
				logger.info( "Going into wait to receive message!" );
				javax.jms.Message msg = topicSub.receive();

				if (msg instanceof TextMessage) {
					TextMessage textMsg = (TextMessage) msg;
					logger.info( "Received message {}", textMsg.getText() );
					eb.publish( subscription, new JsonObject( textMsg.getText() ) );
				}
			} catch( Exception e ) {
				logger.error( "Receiving Message: {} {}", e.getMessage(), e.getStackTrace() );
				err = true;
			} finally {
				if( err )
					break;
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
