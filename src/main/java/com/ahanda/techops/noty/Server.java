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

		 /*// first access the buffer as an observable. We do this this way, since
		// we want to keep using the matchhandler and we can't do that with rxHttpServer
		Observable<Buffer> reqDataObservable = RxSupport.toObservable(req);

		// after we have the body, we update the element in the database
		Observable<RxMessage<JsonObject>> updateObservable = reqDataObservable.flatMap(new Func1<Buffer, Observable<RxMessage<JsonObject>>>() {
			@Override
			public Observable<RxMessage<JsonObject>> call(Buffer buffer) {
				System.out.println("buffer = " + buffer);
				// create the message
				JsonObject newObject = new JsonObject(buffer.getString(0, buffer.length()));
				JsonObject matcher = new JsonObject().putString("_id", req.params().get("id"));
				JsonObject json = new JsonObject().putString("collection", "zips")
					.putString("action", "update")
					.putObject("criteria", matcher)
					.putBoolean("upsert", false)
					.putBoolean("multi", false)
					.putObject("objNew", newObject);

				// and return an observable
				return rxEventBus.send("mongodb-persistor", json);
			}
		});
 
		// use the previous input again, so we could see whether the update was successful.
		Observable<RxMessage<JsonObject>> getLatestObservable = updateObservable.flatMap(new Func1<RxMessage<JsonObject>, Observable<RxMessage<JsonObject>>>() {
			@Override
			public Observable<RxMessage<JsonObject>> call(RxMessage<JsonObject> jsonObjectRxMessage) {
				System.out.println("jsonObjectRxMessage = " + jsonObjectRxMessage);
				// next we get the latest version from the database, after the update has succeeded
				// this isn't dependent on the previous one. It just has to wait till the previous
				// one has updated the database, but we could check whether the previous one was successfully
				JsonObject matcher = new JsonObject().putString("_id", req.params().get("id"));
				JsonObject json2 = new JsonObject().putString("collection", "zips")
					.putString("action", "find")
					.putObject("matcher", matcher);
				return rxEventBus.send("mongodb-persistor", json2);
			}
		});
 
		// after we've got the latest version we return this in the response.
		getLatestObservable.subscribe(new Action1<RxMessage<JsonObject>>() {
			@Override
			public void call(RxMessage<JsonObject> jsonObjectRxMessage) {
				req.response().end(jsonObjectRxMessage.body().encodePrettily());
			}
		});*/

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
	Handler<Message<JsonObject>> pintTracker = new Handler<Message<JsonObject>>() {
		public void handle( Message<JsonObject> msg ) {
			logger.info( "Received pintTracker message: {}", msg.body() );
			JsonObject msgo = msg.body();
			String reply = null;
			JsonArray opType = msgo.getArray( "http" );
			if( opType.get( 0 ).equals( "post" ) &&
				opType.get( 1 ).equals( "/pint/events" ) )
				eventCache.addEvents( msgo.getObject( "body" ).encode() );
			else if( opType.get( 0 ).equals( "post" ) &&
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
			}
			if( reply != null ) msg.reply( reply );
		}
	};

	vertx.eventBus().registerHandler( "PINT.FSReq", pintTracker );

	vertx.eventBus().registerHandler( "PINT.events", pintTracker );

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

	httpServer.listen( 8090, "192.168.1.18" );

	logger.info( "Creating Server on 8090! {}", eventCache );
	} catch( Exception e ) {
		System.out.printf( "Exception :( %s", e );
	}
  }
}
