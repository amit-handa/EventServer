package com.ahanda.techops.noty;

import java.util.*;	//map
import javax.crypto.*; //Mac
import javax.crypto.spec.*; //SecretKeySpec

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;
import org.vertx.java.core.eventbus.*;	//Message

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// handle session and subscriptions
public class SessionMgr extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( SessionMgr.class.getName() );
	private EventBus eb;
	private Mac mac;

	public static final long validityWindow = 30*60*60;	// 30 hours

	Handler sessMgrH = new Handler<Message<JsonObject>>() {
		@Override
		public void handle( Message<JsonObject> msg ) {
		  JsonObject sessUpdateJ = msg.body();
		  String reply = null;
		  JsonObject authReplyJ = Utils.checkCredential( mac, sessUpdateJ.getObject( "document" ) );
		  if( authReplyJ.getString( "status" ).equals( "ok" ) )
			  eb.publish( container.config().getString( "sessions.channel" ), sessUpdateJ );

		  msg.reply( authReplyJ );
		}
	};

	@Override
    public void start() {
	  JsonObject conf = container.config();
	  eb = vertx.eventBus();

	  try {
		mac = Mac.getInstance(Utils.macAlgoName);
		SecretKeySpec sks = new SecretKeySpec( conf.getString( "sessKey" ).getBytes(), Utils.macAlgoName);
		mac.init(sks);
	  } catch( Exception e ) {
		logger.error( "Err in initing mac : {} {}", e.getMessage(), e.getStackTrace() );
	  }

	  eb.registerHandler( conf.getString( "clientID" ) + ".sessionMgr", sessMgrH );
	};
}
