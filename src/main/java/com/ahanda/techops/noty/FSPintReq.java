package com.ahanda.techops.noty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.*;	//JsonObject
import org.vertx.java.platform.*;	//Container;Verticle;

import java.util.*;	//map
import java.io.*;	//ioexception, file
import java.nio.file.*;	//filesystems

public class FSPintReq extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger( FSPintReq.class.getName() );
	private WatchService watcher;
	private Map< WatchKey, Path > watchKeys = new HashMap< WatchKey, Path >();

	@Override
    public void start() {
        try {
			// Set up the watcher
            watcher = FileSystems.getDefault().newWatchService();
            logger.trace("register all watchdirs");
			Path dirPath = Paths.get(System.getProperty("PINT.datadir") + File.separator + "pintReq" );
			WatchKey dirKey = dirPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE );
			logger.debug("Watcher registered for: {}", dirPath.toString());
			watchKeys.put( dirKey, dirPath );

			// periodically check for new events:
           vertx.setPeriodic( 500, new Handler<Long>() {
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
            });
        } catch (IOException e) {
            logger.error("{} {}", e.getMessage(), e.getStackTrace() );
        } catch (ClosedWatchServiceException exc) {
            logger.warn("The service was closed, exiting...");
        }
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

				 JsonObject watchMsg = null;
				try {
					 //The filename is the context of the event.
					 Path filename = watchEvent.context();
					 logger.info("processing changes for: {} {}", filename.toString());
					filename = dir.resolve( filename );
					 watchMsg = new JsonObject( new String( Files.readAllBytes( filename ) ) );
					 watchMsg.putString( "url", filename.toString() );
					filename.toFile().delete();
				} catch( Exception e ) {
					logger.error( "Reading content: {} {}", e.getMessage(), e.getStackTrace() );
				}

                 // publish on eventbus
                 vertx.eventBus().publish("PINT.FSReq", watchMsg);
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
}
