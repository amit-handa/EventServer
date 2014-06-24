package com.ahanda.techops.noty;

import com.ahanda.techops.noty.ds.*;

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
	private static final Logger logger = LoggerFactory.getLogger( Server.class.getName() );

	private EventBus eb;

	private JsonObject conf;
	private SessionManager sessions;
	private EventCache eventCache;
	private UserConfigManager userconfs;
	private ToolConfig toolConfig;
	private ObjectMapper jsonOM = Utils.jsonOM;
	private AuthMiddleware auth;

	private Map< String, Handler< Message< JsonObject > > > activeSessions = new HashMap< String, Handler< Message< JsonObject > > >();

	private static class DBRespH implements Handler< Message< JsonObject > > {
	  private JsonArray data = new JsonArray();
	  private Handler< JsonArray > procData;

	  public DBRespH( Handler< JsonArray > procData ) {
		this.procData = procData;
	  }

	  @Override
	  public void handle( Message< JsonObject > msg ) {
		JsonObject reply = msg.body();
		JsonArray results = reply.getArray("results");

		for (Object el : results) {
			data.add(el);
		}

		logger.info( "searchEvent Response; {} !", reply );
		if (reply.getString("status").equals("more-exist")) {
			msg.reply( new JsonObject(), this );
		} else {
			procData.handle( data );
		}
	  }
	};

	private Handler configH = new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
		Observable<Buffer> bodyObserve = RxSupport.toObservable(req);
		final String uid = req.params().get( "user" );

		logger.info( "Received configH Request!" );
		bodyObserve.subscribe(new Action1<Buffer>() {
			@Override
			public void call(Buffer body) {
				logger.info( "Get Config: {}", body.toString() );
				JsonObject confReq = new JsonObject( body.toString() );

				Handler< JsonArray > procData = new Handler< JsonArray >() {
				  @Override
				  public void handle( JsonArray data ) {
					req.response().end( data.encodePrettily() );
				  }
				};

				DBRespH dbresph = new DBRespH( procData );
		
				eb.send( conf.getObject( "db" ).getString( "address" ), confReq, dbresph );
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
				Handler< Message<JsonObject> > respH = new Handler< Message< JsonObject > >() {
				  @Override
				  public void handle( Message< JsonObject > msg ) {
					logger.info( "Events Added {} !", msg.body() );
					req.response().end( msg.body().encode() );
				  }
				};
	
				eb.send( conf.getString( "clientID" ) + ".pub", new JsonObject().putArray( "body", new JsonArray( body.toString() ) ), respH );
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
				JsonObject confReq = new JsonObject( body.toString() );
				
				Handler< JsonArray > procData = new Handler< JsonArray >() {
				  @Override
				  public void handle( JsonArray data ) {
					req.response().end( data.encodePrettily() );
				  }
				};

				DBRespH dbresph = new DBRespH( procData );
				eb.send( conf.getObject( "db" ).getString( "address" ), confReq, dbresph );
			}
		});
      }
    };

	private void readConfig() {
		try {
			conf = new JsonObject( new String( Files.readAllBytes( Paths.get( this.getClass().getResource( "/pintConf.json" ).toURI() ) ) ) );
		} catch( Exception e ) {
			logger.error( "common prop error: {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

	private void init( JsonObject conf ) {
		String datadir = conf.getString( "datadir" );

		sessions = new SessionManager();
		userconfs = new UserConfigManager( datadir );
		toolConfig = new ToolConfig( datadir );

		eventCache = new EventCache();

		auth = new AuthMiddleware( datadir, conf.getString( "sessKey" ) );

		container.deployVerticle( "com.ahanda.techops.noty.FSPintReq", conf );
		container.deployVerticle( "com.ahanda.techops.noty.EventPub", conf );
		container.deployVerticle( "com.ahanda.techops.noty.EventSub", conf );
		container.deployVerticle( "com.ahanda.techops.noty.EventDB", conf );

		logger.info( "All verticles/modules deployed !" );
	}

	@Override
	public void start() {
	  eb = vertx.eventBus();
		try {
		final Handler eventMgr = new Handler<Message<JsonObject>>() {
			public void handle( Message<JsonObject> msg ) {
				logger.info( "Received eventMgr message: {}", msg.body() );
				JsonArray opType = msg.body().getArray( "http" );

				String reply = null;
				if( opType.get( 0 ).equals( "post" ) &&
					opType.get( 1 ).equals( "/pint/events" ) ) {
					final Message< JsonObject > msgf = msg;
					eb.send( conf.getString( "clientID" ) + ".pub",
						msg.body(), new Handler< Message< JsonObject > >() {
						@Override
						public void handle( Message< JsonObject > reply ) {
							msgf.reply( reply.body() );
						}
					} );
				} else if( opType.get( 0 ).equals( "post" ) &&
					opType.get( 1 ).equals( "/pint/config" ) ) {
					final Message msgf = msg;
					Handler< JsonArray > procData = new Handler< JsonArray >() {
					  @Override
					  public void handle( JsonArray data ) {
						logger.info( "Opened Session: {}", data.encode() );
						msgf.reply( data.encodePrettily() );
					  }
					};

					DBRespH dbresph = new DBRespH( procData );
					eb.send( conf.getObject( "db" ).getString( "address" ), msg.body().getObject( "body" ), dbresph );
				} else if( opType.get( 0 ).equals( "post" ) &&
					opType.get( 1 ).equals( "/pint/events/search" ) ) {
					final Message msgf = msg;
					Handler< JsonArray > procData = new Handler< JsonArray >() {
					  @Override
					  public void handle( JsonArray data ) {
						logger.info( "Got Events: {}", data.encode() );
						msgf.reply( data.encodePrettily() );
					  }
					};

					DBRespH dbresph = new DBRespH( procData );
					eb.send( conf.getObject( "db" ).getString( "address" ), msg.body().getObject( "body" ), dbresph );
				} else if( opType.get( 0 ).equals( "post" ) &&
					opType.get( 1 ).equals( "/pint/DBData" ) ) {
					final Message msgf = msg;
					Handler< Message< JsonObject > > procData = new Handler< Message< JsonObject > >() {
					  @Override
					  public void handle( Message< JsonObject > data ) {
						logger.info( "Got Events: {}", data.body().encode() );
						msgf.reply( data.body().encodePrettily() );
					  }
					};

					eb.send( conf.getObject( "db" ).getString( "address" ), msg.body().getObject( "body" ), procData );
				} else {
					logger.warn( "didnt find handler for this msg {} !!!", opType );
				}
			}
		};

	final Handler authMgr = new Handler<Message<JsonObject>>() {
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
				opType.get( 1 ).equals( "/pint/sessions" ) ) {
				JsonObject replyj = auth.checkCredential( msgo.getObject( "body" ) );
				reply = replyj.encode();
				if( replyj.getString( "stat" ).equals( "OK" ) ) {
					eb.registerHandler( replyj.getString( "sessAuth" ), eventMgr );
				}
				logger.info( "login done {}", reply );
			} else if( opType.get( 0 ).equals( "delete" ) &&
				opType.get( 1 ).equals( "/pint/sessions" ) ) {
				JsonObject replyj = auth.checkCredential( msgo.getObject( "body" ) );
				if( replyj.getString( "stat" ).equals( "OK" ) ) {
					String busAddr = replyj.getString( "sessAuth" );
					Handler< Message< JsonObject > > h = activeSessions.remove( busAddr );
					eb.unregisterHandler( busAddr, h );
				}
			}
			msg.reply( reply );
		}
	};

	Handler FSReqMgr = new Handler<Message<JsonObject>>() {
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

	readConfig();
	conf.putString( "datadir", System.getProperty( "PINT.datadir" ) );
	init( conf );

	Router r = new Router();
    r.post("/pint/config/:user", configH );
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
