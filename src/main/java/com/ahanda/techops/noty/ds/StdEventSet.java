package com.ahanda.techops.noty.ds;
import java.util.*;
import java.lang.reflect.*;	//method

import org.joda.time.format.*;	//DateTimeFormat
import org.joda.time.*;	//DateTime

import com.ahanda.techops.noty.ds.*;
import com.ahanda.techops.noty.ds.event.*;
import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.databind.node.*; //ArrayNode

import org.springframework.expression.*;	// expressionparser
import org.springframework.expression.spel.standard.*;	// spelexpressionparser
import org.springframework.expression.spel.support.*;	// standardevaluationcontext

import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.core.type.*; //typereference

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class StdEventSet implements EventSet {
	private static Logger logger = LoggerFactory.getLogger( StdEventSet.class.getName() );
	private static final DateTimeFormatter dtf = Utils.jsonOM.getDTFormat();
	private static final StandardEvaluationContext ctxt = Utils.ctxt;

	private List< Event > events = new ArrayList< Event >();
	private ObjectMapper jsonOM = Utils.jsonOM;

	public StdEventSet() { }

	public void setEvents( List< Event > events ) {
		this.events = events;
	}

	public List< Event > getEvents() {
		return events;
	}

	public void addEvents( JsonNode ieventsj ) {
		ieventsj = ieventsj.get( "body" );
		assert( ieventsj != null );

		ArrayList< Event > ievents = jsonOM.convertValue( ieventsj, new TypeReference< ArrayList< Event > >() {} );

		Comparator<Event> etimeComp = new Comparator<Event>() {
			@Override
			public int compare( Event e1, Event e2 ) {
				return e1.getETime().compareTo( e2.getETime() );
			}
		};

		Collections.sort( ievents, etimeComp );
		events.addAll( ievents );

		logger.debug( "added events {}", ievents );
	}

	// binary search to be used if 'events' is specified, because its understood to be indexed on starttime
	public List< Event > findEvents( JsonNode eventsFs ) {
		eventsFs = eventsFs.get( "body" );
		Utils.optimizeExpr( eventsFs, Event.class );
		BitSet defbs = new BitSet( events.size() );
		defbs.flip( 0, events.size() );

		BitSet revents = Utils.eventsFilter( defbs, eventsFs, this );

		List< Event > mevents = new ArrayList< Event >();
		int ei = -1;
		while( ( ei = revents.nextSetBit( ++ei ) ) >= 0 ) {
			mevents.add( events.get( ei ) );
		}

		return mevents;
	}

	public BitSet processOp( BitSet ebs, int mi, String op, String fname ) {
		boolean found = mi >= 0;
		if( !found ) mi *= -1;

		int sei = 0, eei = ebs.length();
		if( op.startsWith( ">" ) ) {
			sei = mi;
			if( found && !op.endsWith( "=" ) ) sei++;
		} else if( op.startsWith( "<" ) ) {
			eei = mi;
			if( found && op.endsWith( "=" ) ) eei++;
		} else {	// ==
			sei = mi; eei = mi;
			if( found ) eei++;
		}

		BitSet bs = new BitSet( ebs.size() );

		logger.debug( "procOp: {} {} {} {} {} {} {}", new Object[] { mi, op, fname, sei, eei, });

		int ei = -1;
		ei = ebs.nextSetBit( ++ei );
		while( ei > -1 && ei < eei ) {
			bs.set( ei );
			ei = ebs.nextSetBit( ++ei );
		}
		return bs;
	}
	public BitSet evalExpr( BitSet ievents, String op, String e1, String e2 ) {
		assert( (op != null && e2 != null)
			|| (op == null && e2 == null ) );

		op = op == null ? "" : op;
		e2 = e2 == null ? "" : e2;

		Field efield = null;
		try {
			efield = Event.class.getDeclaredField( e1 );
			Class<?> fc = efield == null ? null : efield.getType();
			//logger.debug( "efield: {} {} {} {}", new Object[] { efield, fc, fc.isAssignableFrom( org.joda.time.DateTime.class ) });
			if( fc == null || !fc.isAssignableFrom( DateTime.class ) )
				efield = null;
		} catch( Exception e ) {
			logger.debug( "{} not a datetime field", e1 );
			efield = null;
		}

		logger.debug( "evalExpr: {} {} {} {}", new Object[] { op, e1, e2, efield } );
		BitSet bs = null;
		if( efield != null ) {
			final DateTime paramdt = dtf.parseDateTime( e2 );
			Comparator< Event > dtcomp = new Comparator<Event>() {
				@Override
				public int compare( Event e1, Event e2 ) {
					logger.debug( "cmp: {} {} {}", new Object[] { events.size(), e1, e2 } );
					Event e = e1 == null ? e2 : e1;

					DateTime dt = e.getETime();
					return dt.compareTo( paramdt );
				}
			};

			int eii = Collections.binarySearch( events, (Event)null, dtcomp );
			bs = processOp( ievents, eii, op, e1 );
		} else {
			String expr = String.format( "%s %s %s", e1, op, e2 );
			Expression pexpr = Utils.exprP.parseExpression( expr );
			bs = new BitSet( ievents.size() );

			int ei = ievents.nextSetBit( 0 );
			while( ei > -1 && ei < ievents.length() ) {
				Event e = events.get( ei );
				ctxt.setVariable( "e", e );
				if( pexpr.getValue( ctxt, Boolean.class ) ) {
					bs.set( ei );
				}
				ei = ievents.nextSetBit( ++ei );
			}
		}
		return bs;
	}

	public static void main( String[] args ) {
		StdEventSet es = new StdEventSet();
		final Event e = new Event() { {
			setETime( dtf.parseDateTime( "Jan 20 2014 00:00:00" ) );
		} };
		List< Event > events = new ArrayList< Event >() { { add( e ); } };

		ObjectNode ieventsj = es.jsonOM.createObjectNode();
		ieventsj.put( "values", es.jsonOM.convertValue( events, JsonNode.class ) );
		es.addEvents( ieventsj );

		try {
			Utils.ctxt.setVariable( "e", e );
			Utils.ctxt.setVariable( "dtf", dtf );

			Expression pexpr = Utils.exprP.parseExpression( "#cmpDate( #e.time, \"Jan 20 2014 00:00:00\" )" );
			logger.debug( "expr eval: {}", (Integer)pexpr.getValue( Utils.ctxt ) );
			//Expression pexpr = Utils.exprP.parseExpression( "${date:compare( e.startTime, \"Jan 19 2014 00:00:00\" )} > 0 && ${date:compare( e.endTime, \"Jan 21 2014 00:00:00\" )} < 0", Boolean.class );
		} catch( Exception ex ) {
			logger.debug( "{}", ex.getMessage() );
		}
	}
}
