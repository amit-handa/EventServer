package com.ahanda.techops.noty;

import com.ahanda.techops.noty.ds.*;

import java.util.*;	//objservable
import java.nio.file.*; //Path,paths,files;
import java.io.*;	// input/output stream

import org.vertx.java.core.*; //AsyncResult;Handler;
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
	private String dbdeployid;

	private JsonObject conf;
	private AuthMiddleware auth;

	private static class DBRespH implements Handler< Message< JsonObject > > {
	  private JsonArray data = new JsonArray();
	  private Handler< JsonObject > procData;

	  public DBRespH( Handler< JsonObject > procData ) {
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
		  reply.putArray( "results", data );
		  procData.handle( reply );
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
				JsonObject confReq = new JsonObject()
				  .putString( "action", "find" )
				  .putString( "collection", "config" )
				  .putObject( "matcher", new JsonObject()
					.putString( "_id", uid ) );

				Handler< JsonObject > procData = new Handler< JsonObject >() {
				  @Override
				  public void handle( JsonObject data ) {
					req.response().end( data.getArray( "results" ).encode() );
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
	
				JsonObject nevents = new JsonObject()
				  .putString( "action", "save" )
				  .putString( "collection", "events" )
				  .putArray( "documents", new JsonArray( body.toString() ) );

				eb.send( conf.getString( "clientID" ) + ".events.new", nevents, respH );
			}
		});
      }
    };

	private Handler sessMgrH = new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
		Observable<Buffer> bodyObserve = RxSupport.toObservable(req);

		logger.info( "Received sessMgr Request!" );
		bodyObserve.subscribe(new Action1<Buffer>() {
			@Override
			public void call( Buffer body ) {
				Handler< Message<JsonObject> > respH = new Handler< Message< JsonObject > >() {
				  @Override
				  public void handle( Message< JsonObject > msg ) {
					logger.info( "Session updated {} !", msg.body() );
					req.response().end( msg.body().encode() );
				  }
				};
	
				JsonObject newSessJ = new JsonObject()
				  .putString( "action", req.method().equals("post") ? "save" : "delete" )
				  .putString( "collection", "sessions" )
				  .putObject( "document", new JsonObject( body.toString() ) );

				eb.send( conf.getString( "clientID" ) + ".sessionMgr", newSessJ, respH );
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
				
				Handler< JsonObject > procData = new Handler< JsonObject >() {
				  @Override
				  public void handle( JsonObject data ) {
					req.response().end( data.getArray( "results" ).encode() );
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

	private Handler modDeployH = new Handler<AsyncResult< String > >() {
	  public void handle(AsyncResult<String> asyncResult) {
		  if (asyncResult.succeeded()) {
			  logger.info( "The mongo-module has been deployed, deployment ID is {}", asyncResult.result() );
			  dbdeployid = asyncResult.result();
		  } else {
			  asyncResult.cause().printStackTrace();
		  }
	  } };

	private void init( JsonObject conf ) {
		String datadir = conf.getString( "datadir" );

		container.deployModule( "io.vertx~mod-mongo-persistor~2.1.1", conf.getObject( "db" ), modDeployH );

		auth = new AuthMiddleware( datadir, conf.getString( "sessKey" ) );

		container.deployVerticle( "com.ahanda.techops.noty.FSPintReq", conf );
		container.deployVerticle( "com.ahanda.techops.noty.SessionMgr", conf );
		container.deployVerticle( "com.ahanda.techops.noty.EventMgr", conf );

		logger.info( "All verticles/modules deployed !" );
	}

	@Override
	public void start() {
	  eb = vertx.eventBus();
	  try {
		readConfig();
		conf.putString( "datadir", System.getProperty( "PINT.datadir" ) );
		init( conf );

		Router r = new Router();
		r.get("/pint/config/:user", configH );

		r.post("/pint/events", addEvents );
		r.post("/pint/sessions", sessMgrH );
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

		logger.info( "Creating Server on 8090! {}" );
	  } catch( Exception e ) {
		  System.out.printf( "Exception :( %s", e );
	  }
	}

  public void stop() {
	  logger.info( "Exiting Server !" );
	  if( dbdeployid != null )
		  container.undeployModule( dbdeployid );
  }
}
