package com.ahanda.techops.noty.ds;

import java.util.*;

import org.vertx.java.core.json.*;	//JsonObject,JsonArray

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

// TODO: migrate eventindex to tree class, create indexNode as tree node.
// keep map for tracking children , parent node for traversing upwards
// or arraylist for inserting event indices.
//
// Keep a separate, pattern to index node structure. This is for helping translate eventgroup to index nodes
//
public class EventCache {
	private static Logger logger = LoggerFactory.getLogger( EventCache.class.getName() );

	private List< JsonObject > events = new ArrayList< JsonObject >();
	private Map< EIKey, Object > eindices = new TreeMap< EIKey, Object >();
	private final List< String > eifields = new ArrayList< String >();

	private Integer eend = 0;

	public EventCache() {
		eifields.add( "eventSource" );
		eifields.add( "eventType" );
	}

	public void addEvents( JsonArray ievents ) {
		for( Object e : ievents ) {
			logger.info( "Adding event !! {}", e );
			events.add( (JsonObject)e );
		}
	}

	public JsonArray findEvents( JsonObject sevents ) {
		logger.debug( "findevents args: {}", sevents );
		updtEIndex( eindices, events, eend );
		eend = events.size();

		logger.debug( "findevents !!!" );
		List< Integer > eis = (List< Integer >)getEIndex( true, eindices, eifields, sevents );
		JsonArray es = new JsonArray();
		for( int ei : eis )
			es.addObject( events.get( ei ) );

		logger.debug( "findevents return {} {}", es.size(), es );
		return es;
	}

	private Object getEIndex( boolean readOnly, Map< EIKey, Object > eindices, List< String > eifields, JsonObject e ) {
		Object cobj = eindices;
		for( int i = 0; i < eifields.size(); i++ ) {
			EIKey keyo = new EIKey( eifields.get( i ), e.getValue( eifields.get( i ) ) );

			logger.debug( "geteindex: {} {}", i, eifields.get( i ) );
			Map< EIKey, Object > ceindices = (Map< EIKey, Object >)cobj;
			cobj = ceindices.get( keyo );
			if( cobj == null ) {
				if( readOnly )
					return null;

				logger.debug( "Insert !!!!" );
				cobj = i == eifields.size()-1 ? new ArrayList< Integer >() : new TreeMap< EIKey, Object >();
				ceindices.put( keyo, cobj );
			}
		}

		return cobj;
	}

	private void updtEIndex( Map< EIKey, Object > eindices, List< JsonObject > events, Integer estart ) {
		if( estart == events.size() )
			return;

		for( int ei = estart; ei < events.size(); ei++ ) {
			List< Integer > eis = (List< Integer >)getEIndex( false, eindices, eifields, events.get( ei ) );
			logger.debug( "Add {} event to {}", ei, eis.size() );
			eis.add( ei );
		}
	}

	public static void main( String[] args ) {
		System.out.println( "Called EventCache2 Main !!!!" );
		EventCache ec = new EventCache();

		for( int i = 0; i < args.length-1; i++ ) {
			String eventsf = args[i];

			logger.debug( "Adding events!!!! {}", eventsf );
			JsonArray eventsJ = null;
			try {
				eventsJ = new JsonArray( new String( Files.readAllBytes( Paths.get( eventsf ) ) ) );
			} catch( Exception e ) {
				logger.error( "Couldnt read events file" );
			}

			ec.addEvents( eventsJ );
		}

		String feventsf = args[args.length-1];

		JsonObject feventsJ = null;
		try {
			feventsJ = new JsonObject( new String( Files.readAllBytes( Paths.get( feventsf ) ) ) );
		} catch( Exception e ) {
			logger.error( "Couldnt read find events file" );
		}

		ec.findEvents( feventsJ );
	}
}
