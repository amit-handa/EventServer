package com.ahanda.techops.noty.ds;
import com.ahanda.techops.noty.ds.event.*;

import java.util.*;
import java.lang.reflect.*;	//method

import org.joda.time.*;
import org.joda.time.format.*;	//DateTimeFormat

import javax.sql.DataSource;
import java.sql.*;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.*;	// executors, scheduledexecutorsrvice, scheduledfuture, timeunit
import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.databind.node.*; //objectnode
import com.fasterxml.jackson.core.type.*; //typereference

public class EventCache {
	private static Logger logger = LoggerFactory.getLogger( EventCache.class.getName() );

	private DataSource orads, sybds;
	private Map< Map< String, String >, EventSet > srcsEvents = null;
	private ObjectMapper jsonOM = Utils.jsonOM;
	private static final DateTimeFormatter dtf = Utils.jsonOM.getDTFormat();
	private ETACache etamgr;
	private ToolConfig tc;

	public EventCache() {
		logger.debug( "Ctor called!!" );
		System.out.println(" EventCache Iniited" );
		srcsEvents = new HashMap< Map< String, String >, EventSet >();
	}

	public void setToolConfig( ToolConfig tc ) {
		this.tc = tc;
	}

	public void setJsonOM( ObjectMapper jsonOM ) {
		this.jsonOM = jsonOM;
	}

	public void setETACache( ETACache etamgr ) {
		logger.debug( "ETA Cache {}", etamgr );
		this.etamgr = etamgr;
	}

	public void setOraDataSource( DataSource ds ) {
		logger.debug( "Ora DATA SOURCE {}", ds );
		this.orads = ds;
	}

	public void setSybDataSource( DataSource ds ) {
		logger.debug( "Syb DATA SOURCE {}", ds );
		this.sybds = ds;
	}

	public void addEvents( String trackerFeed ) {
		try {
			JsonNode trackerFeedJ = jsonOM.readTree( trackerFeed );
			//logger.debug( "adding event feed" );
			logger.debug( "adding event feed {}", trackerFeed );

			JsonNode esrcj = trackerFeedJ.get( "eventSource" );
			Map< String, String > esrc = jsonOM.convertValue( esrcj, new TypeReference<Map< String, String > >(){}  );

			logger.debug( "srcsevents {}", srcsEvents );
			EventSet eset = srcsEvents.get( esrc );
			String esClass = null;
			if( eset == null ) {
				logger.debug( "new event source!" );

				try {
					String iesType = tc.getESrcClass( esrc );
					if( iesType == null )
						iesType = "Std";
					esClass = String.format( "com.ahanda.techops.pint.ds.%sEventSet", iesType );
					Class<?> esc = Class.forName( esClass );
					eset = (EventSet)esc.newInstance();
				} catch( Exception e ) {
					logger.error( "Error in instantiating eventset!" );
				}

				srcsEvents.put( esrc, eset );
				//List< Event > eventETAs = etamgr.getETAs( esrc );
				//events.setETAs( eventETAs );
			}

			logger.debug( "events {}, {}", esClass, eset );

			eset.addEvents( trackerFeedJ.get( "events" ) );
		} catch( Exception e ) {
			logger.error( "reading TrackerFeed: {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

	// map of arguments: cycledate, eventset
	public List< Event > findEvents( String args ) {
		List< Event > events = null;
		try {
			JsonNode argsj = jsonOM.readTree( args );
			JsonNode eventSourceJ = argsj.get( "eventSource" ).get( "body" );
			assert eventSourceJ != null;

			Map< String, String > eventSourceP = jsonOM.convertValue( eventSourceJ, new TypeReference< Map< String, String > >() {} );

			EventSet es = srcsEvents.get( eventSourceP );

			events = es.findEvents( argsj.get( "events" ) );
		} catch( Exception e ) {
			logger.error( "findEvents error: {} {}", e.getMessage(), e.getStackTrace() );
		}

		return events;
	}

	public static void main( String[] args ) {
		ObjectMapper jsonOM = new JodaObjectMapper();

		ObjectNode objNode = jsonOM.createObjectNode();
		Map< String, String > esrc = new HashMap< String, String >() { {
			put( "dboEnv", "PM" );
			put( "region", "U" );
			put( "ncEnv", "BETA" );
			put( "dbEnv", "SYBBTAPIM" );
		} };
		objNode.put( "eventSource", jsonOM.convertValue( esrc, JsonNode.class ) );

		ArrayNode eventsListJ = jsonOM.createArrayNode();
		ObjectNode eventsJ = jsonOM.createObjectNode();
		eventsJ.put( "type", "NC" );
		eventsJ.put( "values", eventsListJ );
		objNode.put( "events", eventsJ );
		DateTime dt = dtf.parseDateTime( "Jan 20 2014 00:00:00" );
		eventsJ.put( "cycleDate", jsonOM.convertValue( dt, JsonNode.class ) );
		eventsJ.put( "cycleDate", eventsListJ );

		NCEvent e = new NCEvent();
		e.setETime( dtf.parseDateTime( "Jan 20 2014 01:00:00" ) );
		e.setEndTime( dtf.parseDateTime( "Jan 20 2014 02:00:00" ) );
		e.setStatus( "0" );

		EventID eid = new EventID();
		eid.getIds().add( "" );
		eid.getIds().add( "P4BO-000" );
		e.setEId( eid );

		eventsListJ.add( jsonOM.convertValue( e, JsonNode.class ) );

		EventCache ec = new EventCache();
		try {
			ec.addEvents( jsonOM.writeValueAsString( objNode ) );
		} catch( Exception exc ) {
			logger.error( "addEvents: {} {}", exc.getMessage(), exc.getStackTrace() );
		}

		/*ExpressionParser spep = new SpelExpressionParser();

		StandardEvaluationContext ctxt = new StandardEvaluationContext();
		ctxt.registerFunction( "cmpDate", EventSet.class.getDeclaredMethod( "cmpDate", new Class[] { DateTime.class, String.class } ) );
		ctxt.setVariable( "e", e );
		ctxt.setVariable( "dtf", dtf );*/
	}
}
