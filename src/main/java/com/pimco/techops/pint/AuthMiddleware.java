package com.pimco.techops.pint;

import java.util.*;	// arraylist
import java.io.*;	// file/object input/output stream, bufferredreader/writer
import java.nio.file.*; //Path,paths,files;
import java.nio.charset.Charset;
import java.net.URL;

import com.google.common.io.Resources; //Resources, bytesource;
import com.google.common.io.ByteSource; //Resources, bytesource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;

import com.pimco.techops.pint.ds.*;	// Session

import io.netty.handler.codec.http.HttpResponseStatus;

import org.jetbrains.annotations.NotNull;
import org.vertx.java.core.*;	//multimap
import com.jetdrone.vertx.yoke.*;	//Middleware,Yoke
import com.jetdrone.vertx.yoke.middleware.YokeRequest;	//yokerequest
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

import javax.crypto.*;	//Mac
import javax.crypto.spec.*;	//SecretKeySpec
import org.apache.commons.codec.binary.Base64;

//import org.springframework.beans.factory.annotation.Required;
public class AuthMiddleware extends Middleware {
	// process all the uconfs and populate user-data
	private static final Logger logger = LoggerFactory.getLogger( AuthMiddleware.class );
	private static final String macAlgoName = "HmacSHA256";

	private String datadir; //$TRDATADIR
	private String secretKey; //$TRDATADIR
	private Mac mac;

	private WatchService watcher;
	private Map< WatchKey, Path > watchKeys = new HashMap< WatchKey, Path >();

	private Map< String, UserInfo > userInfos = new HashMap< String, UserInfo >();
	private Set< String > invalidSessions = new HashSet< String >();

	private static volatile int refdur = 500000;	// .5 secs

    public AuthMiddleware() {}
    public AuthMiddleware( String datadir, String secretKey ) {
		this.datadir = datadir;
		this.secretKey = secretKey;
	}

	private static class UserInfo {
		public String role;
		public String password;
		public String fname;	// properties file

		public void set( String field, String fvalue ) {
			switch( field ) {
				case "role" :
					role = fvalue;
					break;
				case "password" :
					password = fvalue;
					break;
				default:
					break;
			}
		}
	}

	public void updateConf( Map< String, UserInfo > userInfos, Properties p, String fname ) {
		for( Map.Entry< Object, Object > pe : p.entrySet() ) {
			String upropStr = (String)pe.getKey();
			if( upropStr.isEmpty() ||
				upropStr.charAt( 0 ) == '#' )
				continue;

			String[] uprops = upropStr.split( ".", 2 );
			if( uprops.length != 2 )
				continue;

			UserInfo uinfo = userInfos.get( uprops[0] );

			if( uinfo == null )
				uinfo = new UserInfo();

			uinfo.set( uprops[1], (String)pe.getValue() );
			uinfo.fname = fname;
		}
	}

	public void removeUInfo( Map< String, UserInfo > userInfos, String fnames ) {
		Iterator< Map.Entry< String, UserInfo > > uinfoi = userInfos.entrySet().iterator();
		while( uinfoi.hasNext() ) {
			UserInfo qmi = uinfoi.next().getValue();
			if( !fnames.contains( qmi.fname ) )
				continue;

			uinfoi.remove();
		}
	}

	@Override
    public Middleware init(@NotNull final Vertx vertx, @NotNull final org.vertx.java.core.logging.Logger l) {
        try {
			super.init( vertx, l );

			// Set up the watcher
            watcher = FileSystems.getDefault().newWatchService();
            logger.trace("register all watchdirs");
			Path dirPath = Paths.get( System.getProperty("PINT.datadir") );
			WatchKey dirKey = dirPath.register( watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE );
			logger.debug("Watcher registered for: {}", dirPath.toString());
			watchKeys.put( dirKey, dirPath );

			InputStream is = null;
			try( DirectoryStream< Path > dir = Files.newDirectoryStream( dirPath ) ) {
				for( Path file : dir ) {
					Path absPath = file.toAbsolutePath();
					if( !absPath.toString().endsWith( "uconf" ) )
						continue;

					ByteSource inputSupplier = Resources.asByteSource( absPath.toUri().toURL() );
					is = inputSupplier.openStream();
					Properties propf = new Properties();
					propf.load( is );
					updateConf( userInfos, propf, absPath.toString() );
					is.close();
					is = null;
				}
			} catch( Exception e ) {
				logger.error( "ReadingConfigDir, {} {}", e.getMessage(), e.getStackTrace() );
			} finally {
				if( is != null ) is.close();
			}

			// periodically check for new events:
            vertx.setPeriodic( refdur, new Handler<Long>() {
                @Override
                public void handle(Long timerId) {
                    try {
                        processEvents();
                    } catch (IOException exc) {
                        logger.error("I/O error occured while processing events: {} (cause: {})", exc.getMessage(), exc.getCause() );
                    } catch (Exception exc) {
                        logger.error("Unexpected error occured while processing events: {}", exc.getMessage());
                    }
				}
			} );

			mac = Mac.getInstance( macAlgoName );
			SecretKeySpec sks = new SecretKeySpec( secretKey.getBytes(), macAlgoName );
			mac.init( sks );
        } catch (IOException e) {
            logger.error("{} {}", e.getMessage(), e.getStackTrace() );
        } catch (ClosedWatchServiceException exc) {
            logger.warn("The service was closed, exiting...");
        } catch (Exception exc) {
            logger.warn("Exception found: {} {}", exc.getMessage(), exc.getStackTrace() );
        }

		logger.info( "Inited ! {} {}", new Object[] { secretKey, mac } );
		return this;
    }

    private void processEvents() throws IOException, Exception {
        for (;;) {
            WatchKey key;
            key = watcher.poll();
             // watch key is null if no queued key is available (within the specified timeframe if a timeout was specified on the poll() request)
             if (key == null) break;

             logger.info("Events received, start processing... (key: {})", key );

             Path dir = watchKeys.get(key);
             if (dir == null) {
                 logger.warn("watchKey not recognized! ({})", key );
                 continue;
             }

             for (WatchEvent<?> event: key.pollEvents()) {
                 WatchEvent<Path> watchEvent = (WatchEvent<Path>) event;
                 WatchEvent.Kind<Path> kind = watchEvent.kind();

                 if (kind.name().equals(StandardWatchEventKinds.OVERFLOW.name())) {
                     continue;
                 }

				InputStream is = null;
				try {
					 //The filename is the context of the event.
					 Path filename = watchEvent.context();
					if( !filename.toString().endsWith( "uconf" ) )
						continue;

					filename = dir.resolve( filename );
					ByteSource inputSupplier = Resources.asByteSource( filename.toUri().toURL() );
					Properties propf = new Properties();
					is = inputSupplier.openStream();
					propf.load( is );
					updateConf( userInfos, propf, filename.toString() );
					logger.info("processed changes for: {} {}", filename.toString());
				} catch( Exception e ) {
					logger.error( "Reading content: {} {}", e.getMessage(), e.getStackTrace() );
				} finally { if( is != null ) is.close(); }
             }

             //Reset the key -- this step is critical if you want to receive
             //further watch events. If the key is no longer valid, the directory
             //is inaccessible so exit the loop.
             boolean valid = key.reset();
             if (!valid) {
                 logger.warn("watchKey invalidated, removing from the list ({})", key );
                 watchKeys.remove(key);

                 // Exit if no keys remain
                 if ( watchKeys.isEmpty() )
                     break;
             }
        }
    }

    @Override
    public void handle(@NotNull final YokeRequest request, @NotNull final Handler<Object> next) {
		MultiMap headers = request.headers();
		String path = request.path();
		String accessMethod = request.method();
		if( path.matches( ".+/sessions/.*" )
			&& accessMethod.equals( "POST" ) ) {	// opensession 
			String userId = path.substring( path.lastIndexOf( '/' )+1 );
			UserInfo ui = userInfos.get( userId );
			if( ui == null ) {	// authenticate userId
				logger.debug( "Cannot validate User {}, Fix it, continuing as usual !", userId );
				//return null;
			}

			String cval = String.format( "name=%s&startTime=%d", userId, System.currentTimeMillis()/1000L );
			String sessid = new String( Base64.encodeBase64( mac.doFinal( cval.getBytes() ) ) );
			headers.set( "Set-Cookie", String.format( "%s&sessId=%s", cval, sessid ) );
			logger.info( "Opensession request: {}", path );
			next.handle( null );
			return;
		}

		YokeResponse resp = request.response();
		String cvals = headers.get( "cookie" );
		logger.info( "Intercepted msg : headers {} {} {}!!!", new Object[] { path, cvals, headers } );

		if( cvals == null ) { // invalid request, opensession first
			resp.setStatusCode( HttpResponseStatus.UNAUTHORIZED.code() );
			logger.error( "Invalid request, session doesnt exist!" );
			next.handle( "Authorization absent, kindly sign-in first" );
			return;
		}

		final String[] cfields = cvals.split( "&", 3 );
		assert( cfields.length == 3 &&
			cfields[0].startsWith( "userId=" ) &&
			cfields[1].startsWith( "startTime=" ) &&
			cfields[2].startsWith( "sessId=" ) );

		String sessid = cfields[2].substring( "sessId=".length() );
		String csessid = new String( Base64.encodeBase64( mac.doFinal( String.format( "%s&%s", cfields[0], cfields[1] ).getBytes() ) ) );

		if( !csessid.equals( sessid ) ) {
			logger.error( "Invalid credentials {} {}!!", sessid, csessid );
			resp.setStatusCode( HttpResponseStatus.UNAUTHORIZED.code() );
			next.handle( "Unauthorized access: kindly sign-in again" );
			return;
		}

		long startTime = Long.valueOf( cfields[1].substring( "startTime=".length() ) ).longValue();

		long elapseSecs = System.currentTimeMillis()/1000L-startTime;
		if( invalidSessions.contains( sessid ) ||  elapseSecs > Session.validityWindow ) {
			resp.setStatusCode( 419 );
			next.handle( String.format( "Session Expired : %d", elapseSecs ) );
			return;
		}

		headers.set( "userId", cfields[0].substring( "userId=".length() ) );
		headers.set( "startTime", cfields[1].substring( "startTime=".length() ) );
		if( path.matches( ".+/sessions/.*" ) ) {
			if( accessMethod.equals( "DELETE" ) ) {
				invalidSessions.add( sessid );
				resp.setStatusCode( HttpResponseStatus.OK.code() );
				next.handle( "Session Deleted Successfully" );
				return;
			}
		}

		next.handle( null );
    }
}
