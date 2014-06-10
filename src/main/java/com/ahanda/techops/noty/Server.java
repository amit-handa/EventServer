package com.ahanda.techops.noty;

import com.ahanda.techops.noty.ds.*;
import com.ahanda.techops.noty.ds.event.*;

import java.util.*;	//objservable
import java.nio.file.*; //Path,paths,files;
import java.io.*;	// input/output stream

import org.vertx.java.core.Handler;
import org.vertx.java.platform.*;	//Container;Verticle;
import org.vertx.java.core.http.*;	//RouteMatcher;HttpServerRequest
import org.vertx.java.core.sockjs.*;	//RouteMatcher;SockJSServerRequest
import org.vertx.java.core.eventbus.*;	//Message
import org.vertx.java.core.buffer.*;	//Buffer
import org.vertx.java.core.json.*;	//JsonObject

import io.vertx.rxcore.*;	//rxsupport
import io.vertx.rxcore.java.*;
import rx.Observable;
import rx.util.functions.*;	//Func1, Action1
import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.jetdrone.vertx.yoke.*; //Yoke
import com.jetdrone.vertx.yoke.middleware.*; //Router

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources; //Resources, bytesource;
import com.google.common.io.ByteSource; //Resources, bytesource;

public class Server extends Verticle {
	private SessionManager sessions;
	private EventCache eventCache;
	private UserConfigManager userconfs;
	private ToolConfig toolConfig;
	private ObjectMapper jsonOM = Utils.jsonOM;
	private AuthMiddleware auth;

	private Map< String, Handler< Message< JsonObject > > > activeSessions = new HashMap< String, Handler< Message< JsonObject > > >();

	private static final Logger logger = LoggerFactory.getLogger( Server.class.getName() );

	private String _openSession( String uid ) {
		Map< String, Object > initialConf = new HashMap< String, Object >();
		UserConfig uc = userconfs.getUserConf( uid );
		initialConf.put( "userConfig", uc );
		initialConf.put( "toolConfig", toolConfig.getConfig() );

		String str = null;
		try {
		  str = jsonOM.writeValueAsString( initialConf );
		} catch( Exception e ) {
			logger.error( "writing as json! {}, {}", e.getMessage(), e.getStackTrace() );
		}

		return str;
	}

	private Handler openSession = new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
		Observable<Buffer> bodyObserve = RxSupport.toObservable(req);
		final String uid = req.params().get( "user" );

		logger.info( "Received OpenSession Request!" );
		bodyObserve.subscribe(new Action1<Buffer>() {
			@Override
			public void call(Buffer body) {
				String cval = String.format( "%s; path=/pint; HttpOnly", req.headers().get( "Set-Cookie" ) );
				req.response().headers().set( "Set-Cookie", cval );
				String str = _openSession( uid );
				logger.info( "opensession resp prepared {} !", str  );
				req.response().end( str );
			}
		});
		}
	};

	private Handler addEvents = new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
		Observable<Buffer> bodyObserve = RxSupport.toObservable(req);

		logger.info( "Received AddEvents Request!" );
		bodyObserve.subscribe(new Action1<Buffer>() {
			@Override
			public void call(Buffer body) {
				eventCache.addEvents( body.toString() );
				req.response().end( "Events Added!" );
			}
		});
      }
    };

	private Handler searchEvents = new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
		Observable<Buffer> bodyObserve = RxSupport.toObservable(req);

		logger.info( "Received searchEvents Request!" );
		bodyObserve.subscribe(new Action1<Buffer>() {
			@Override
			public void call(Buffer body) {
				JsonArray bodyj = new JsonArray( body.toString() );
				List< Event > events = eventCache.findEvents( ((JsonObject)bodyj.get( 2 )).encode() );
				String respstr = null;
				try {
					respstr =Utils.jsonOM.writeValueAsString( events );
				} catch( Exception e ) {
					logger.error( "searchEvent Response; {} !", e.getMessage()  );
				}
				logger.info( "searchEvent Response; {} !", respstr  );
				req.response().end( respstr );
			}
		});
      }
    };

	private void init( String datadir ) {
		sessions = new SessionManager();
		eventCache = new EventCache();
		userconfs = new UserConfigManager( datadir );
		toolConfig = new ToolConfig( datadir );
		eventCache.setToolConfig( toolConfig );

		logger.info( "Launching verticle for FSPintReq" );
		container.deployVerticle( "com.ahanda.techops.noty.FSPintReq" );

		InputStream is = null;
		try {
			is = this.getClass().getResourceAsStream( "/common.properties" );
			Properties propf = new Properties();
			propf.load( is );
			auth = new AuthMiddleware( System.getProperty( "PINT.datadir" ), (String)propf.get( "PINT.sessKey" ) );
			is.close();
			is = null;
		} catch( Exception e ) {
			logger.error( "common prop error: {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

  public void start() {
	try {
	final Handler<Message<JsonObject>> eventMgr = new Handler<Message<JsonObject>>() {
		public void handle( Message<JsonObject> msg ) {
			logger.info( "Received eventMgr message: {}", msg.body() );
			JsonObject msgo = msg.body();
			String reply = null;
			JsonArray opType = msgo.getArray( "http" );
			logger.info( "didnt find handler for this msg {} !!!", opType );
			if( opType.get( 0 ).equals( "post" ) &&
				opType.get( 1 ).equals( "/pint/events" ) ) {
				logger.info( "pppsssssssssssssssst" );
				eventCache.addEvents( msgo.getObject( "body" ).encode() );
			} else if( opType.get( 0 ).equals( "post" ) &&
				opType.get( 1 ).equals( "/pint/sessions" ) ) {
				String uid = msgo.getString( "body" );
				reply = _openSession( uid );
				logger.info( "Opened Session for {} : {}", uid, reply );
			} else if( opType.get( 0 ).equals( "post" ) &&
				opType.get( 1 ).equals( "/pint/events/search" ) ) {
				List< Event > events = eventCache.findEvents( ((JsonObject)msgo.getArray( "body" ).get( 2 )).encode() );
				try {
				reply = jsonOM.writeValueAsString( events );
				} catch( Exception e ) {
					logger.error( "searchEvent Response; {} !", e.getMessage()  );
				}
				logger.info( "Found events: {}", events );
			} else {
				logger.warn( "didnt find handler for this msg {} !!!", opType );
			}
			if( reply != null ) msg.reply( reply );
		}
	};

	final Handler<Message<JsonObject>> authMgr = new Handler<Message<JsonObject>>() {
		public void handle( Message<JsonObject> msg ) {
			logger.info( "Received authMgr message: {}", msg.body() );
			JsonObject msgo = msg.body();
			String reply = null;
			JsonArray opType = msgo.getArray( "http" );
			if( opType.get( 0 ).equals( "post" ) &&
				opType.get( 1 ).equals( "/pint/login" ) ) {
				JsonObject replyj = auth.checkCredential( msgo.getObject( "body" ) );
				reply = replyj.encode();
				logger.info( "login done {}", reply );
			} else if( opType.get( 0 ).equals( "post" ) &&
				opType.get( 1 ).equals( "/pint/sessions2" ) ) {
				JsonObject replyj = auth.checkCredential( msgo.getObject( "body" ) );
				reply = replyj.encode();
				if( replyj.getString( "stat" ).equals( "OK" ) ) {
					vertx.eventBus().registerHandler( replyj.getString( "sessAuth" ), eventMgr );
				}
				logger.info( "login done {}", reply );
			} else if( opType.get( 0 ).equals( "delete" ) &&
				opType.get( 1 ).equals( "/pint/sessions2" ) ) {
				JsonObject replyj = auth.checkCredential( msgo.getObject( "body" ) );
				if( replyj.getString( "stat" ).equals( "OK" ) ) {
					String busAddr = replyj.getString( "sessAuth" );
					Handler< Message< JsonObject > > h = activeSessions.remove( busAddr );
					vertx.eventBus().unregisterHandler( busAddr, h );
				}
			}
			msg.reply( reply );
		}
	};

	Handler<Message<JsonObject>> FSReqMgr = new Handler<Message<JsonObject>>() {
		public void handle( Message<JsonObject> msg ) {
			logger.info( "Received FSReq message: {}", msg.body() );
			JsonObject msgo = msg.body();
			String toAddr = msgo.getString( "to" );
			if( toAddr == null )
				eventMgr.handle( msg );
			else authMgr.handle( msg );
		}
	};

	vertx.eventBus().registerHandler( "PINT.authMgr", authMgr );
	vertx.eventBus().registerHandler( "PINT.FSReq", FSReqMgr );

	init( System.getProperty( "PINT.datadir" ) );

	/*RouteMatcher rm = new RouteMatcher();
    rm.post("/pint/sessions/:user", openSession );
    rm.post("/pint/events", addEvents );
    rm.post("/pint/events/search", searchEvents );

    vertx.createHttpServer().requestHandler(rm).listen(8090);*/
	Router r = new Router();
    r.post("/pint/sessions/:user", openSession );
    r.post("/pint/events", addEvents );
    r.post("/pint/events/search", searchEvents );

    HttpServer httpServer = vertx.createHttpServer();
	new Yoke( this )
		//.use( new BodyParser() )
		.use( new Static( "web" ) )
		.use( auth )
		.use( r ).listen( httpServer );

	SockJSServer sockServer = vertx.createSockJSServer( httpServer );
	JsonObject config = new JsonObject().putString( "prefix", "/spint" );
	JsonArray noPermitted = new JsonArray();
	noPermitted.add( new JsonObject() );
	sockServer.bridge( config, noPermitted, noPermitted );

	httpServer.listen( 8090 );

	logger.info( "Creating Server on 8090! {}", eventCache );
	} catch( Exception e ) {
		System.out.printf( "Exception :( %s", e );
	}
  }
}
