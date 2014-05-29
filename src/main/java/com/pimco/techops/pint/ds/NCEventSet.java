package com.pimco.techops.pint.ds;
import com.pimco.techops.pint.ds.event.*;

import java.util.*;
import java.lang.reflect.*;	//method
import com.pimco.techops.pint.ds.*;

import org.joda.time.format.*;	//DateTimeFormat
import org.joda.time.*;	//DateTime

import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.databind.node.*; //ArrayNode

import org.springframework.expression.*;	// expressionparser
import org.springframework.expression.spel.standard.*;	// spelexpressionparser
import org.springframework.expression.spel.support.*;	// standardevaluationcontext

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class NCEventSet implements EventSet {
	private static Logger logger = LoggerFactory.getLogger( EventSet.class.getName() );
	private static final DateTimeFormatter dtf = Utils.jsonOM.getDTFormat();
	private static final StandardEvaluationContext ctxt = Utils.ctxt;

	// mapping events from id
	private Map< EventID, Integer > eventsM = new HashMap< EventID, Integer >();
	private List< Integer > revents = new ArrayList< Integer >();

	private List< NCEvent > events = new ArrayList< NCEvent >();
	// mapping events from time (start,end)
	private List< Integer > eTimeline = new ArrayList< Integer >();
	private List< NCEvent > eventETAs;

	private BitSet defbs = new BitSet();
	private List< Integer > eTimelineis = new ArrayList< Integer >();
	private Map< String, NCEvent > runningEvents = null;
	private Map< String, NCEvent > pidEvents = null;
	private static NCEvent dummyE = null;
	private int rootEI = 0;
	private ObjectMapper jsonOM = Utils.jsonOM;

	public NCEventSet() { }

	public void setEvents( List< NCEvent > events ) {
		this.events = events;
	}

	public List< NCEvent > getEvents() {
		return events;
	}

	public void setETAs( List< NCEvent > eventETAs ) {
		this.eventETAs = eventETAs;
	}

	public void addEvents( JsonNode ieventsj ) {
		JsonNode ievents = ieventsj.get( "body" );
		List< Integer > sortIEvents = new ArrayList< Integer >();
		for( JsonNode ej : ievents ) {
			NCEvent e = jsonOM.convertValue( ej, NCEvent.class );
			Integer eei = eventsM.get( e.getEId() );
			logger.debug( "ie: {} {} {} {}", new Object[] { eei, e.getETime(), e.getEndTime() } );

			if( eei == null ) {
				eei = events.size();
				events.add( (NCEvent)null );	// one for startime
				events.add( (NCEvent)null );	// other for endtime
			}

			if( events.get( eei ) == null ) {
				sortIEvents.add( eei );
				events.set( eei, e );
				eventsM.put( e.getEId(), eei );
			}

			if( events.get( ++eei ) == null && e.getEndTime() != null ) {
				sortIEvents.add( eei );
				events.set( eei-1, e );
				events.set( eei, e );
			}
		}

		Comparator<Integer> etimeComp = new Comparator<Integer>() {
			@Override
			public int compare( Integer ei1, Integer ei2 ) {
				NCEvent e1 = events.get( ei1 );
				NCEvent e2 = events.get( ei2 );
				DateTime t1 = ei1%2 == 0 ? e1.getETime() : e1.getEndTime();
				DateTime t2 = ei2%2 == 0 ? e2.getETime() : e2.getEndTime();
				//logger.debug( "comp: {} {} {} {}", new Object[] { ei1, ei2, t1, t2 } );
				return t1.compareTo( t2 );
			}
		};

		Collections.sort( sortIEvents, etimeComp );

		// update indices to the etimeline
		for( int i = 0; i < sortIEvents.size(); i++ ) {
			int ei = sortIEvents.get( i );
			NCEvent e = events.get( ei );
			e.getTimelineis().set( ei%2, eTimeline.size()+i );

			// add additional event indices to the list 
			eTimelineis.add( i+eTimeline.size() );
		}

		eTimeline.addAll( sortIEvents );
		if( runningEvents == null ) {
			runningEvents = new HashMap< String, NCEvent >();
			pidEvents = new HashMap< String, NCEvent >();
			rootEI = sortIEvents.get( 0 );
			final String pid = events.get( rootEI ).getEId().getIds().get( 0 );
			dummyE = new NCEvent() {{
				setEId( new EventID( new ArrayList<String>() { { add( "" ); add( pid ); } } ) );
				setETypeInfo( new HashMap< String, String >() );
			}};
			runningEvents.put( dummyE.getEId().getIds().get( 1 ), dummyE );
			logger.debug( "inserted parent : {}", runningEvents );
		}

		buildHTree( sortIEvents, runningEvents, pidEvents );

		logger.debug( "added events {}", sortIEvents );
		//logger.debug( "PRINT TREEEEEEEEEEEEEEEEE" );
		//dummyE.printTree( 1 );
		//Collections.sort( events );
	}

	public void buildHTree( List< Integer > timeOEvents, Map< String, NCEvent > runningEvents, Map< String, NCEvent > pidEvents ) {
		for( int i = 0; i < timeOEvents.size(); i++ ) {
			int ei = timeOEvents.get( i );
			final NCEvent e = events.get( ei );
			if( ei%2 == 1 ) {	//end event
				List< NCEvent > cevents =  e.getChilds();
				if( cevents != null && cevents.size() == 1 && cevents.get( 0 ).getEId().getIds().get( 1 ).matches( "\\d+" ) ) {
					NCEvent pidE = cevents.get( 0 );
					pidE.getTimelineis().set( 1, e.getTimelineis().get( 1 ) );
					pidE.setStatus( e.getStatus() );
					runningEvents.remove( pidE.getEId().getIds().get( 1 ) );
				}
				runningEvents.remove( e.getEId().getIds().get( 1 ) );
				continue;
			}

			final String pename = e.getEId().getIds().get( 0 );

			logger.debug( "finding parent: {}", e.getEId() );
			NCEvent pe = runningEvents.get( pename );
			if( pe == null ) {
				if( pename.matches( "\\d+" ) ) {
					NCEvent pidE = null;
					try {
					pidE = pidEvents.get( pename );
					if( pidE == null )
						pidE = getEventByPID( pename, runningEvents );

					logger.debug( "found pid: {}", pidE );
					//assert pidE != null;
					/*pidE = new Event() {{
						setEId( new EventID( pename, epid.getEId().getId() ) );
						setETime( epid.getETime() );
						setEndTime( epid.getEndTime() );
						setETypeInfo( epid.getETypeInfo() );
						setEInfo( epid.getEInfo() );
						getTimelineis().set( 0, epid.getTimelineis().get( 0 ) );
						setStatus( epid.getStatus() );
					}};
					epid.addChild( pidE );*/
					} catch( Exception ex ) {
						logger.error( "didnt get event by pid: {} {} {}", new Object[] { e.getEId(), runningEvents, ex.getStackTrace() } );
					}

					//eventsM.put( pidE.getEId(), eTimeline.get( pidE.getTimelineis().get( 0 ) ) );
					//runningEvents.put( pidE.getEId().getId(), pidE );
					pe = pidE;
				}
			}
			if( pe == null ) {
				logger.error( "found no parent node for {} {}", e.getEId() );
				final String pname = dummyE.getEId().getIds().get( 1 );
				NCEvent pidE = new NCEvent() {{
					setEId( new EventID( new ArrayList<String>() { { add( pename ); add( pname ); } } ) );
					setETime( e.getETime() );
					setEndTime( e.getEndTime() );
					setETypeInfo( e.getETypeInfo() );
					setEInfo( e.getEInfo() );
					getTimelineis().set( 0, e.getTimelineis().get( 0 ) );
					setStatus( e.getStatus() );
				}};
				dummyE.addChild( pidE );
				eventsM.put( pidE.getEId(), eTimeline.get( pidE.getTimelineis().get( 0 ) ) );
				runningEvents.put( pidE.getEId().getIds().get( 1 ), pidE );
				pe = pidE;
			}
			pe.addChild( e );

			NCEvent twin = runningEvents.get( e.getEId().getIds().get( 1 ) );
			if( twin != null && twin.getEId().getIds().get( 0 ) != dummyE.getEId().getIds().get( 1 ) ) {
				// put both in pidEvents
				pidEvents.put( twin.getEInfo().get( "pid" ), twin );
				pidEvents.put( e.getEInfo().get( "pid" ), e );
				runningEvents.remove( e.getEId().getIds().get( 1 ) );
			} else runningEvents.put( e.getEId().getIds().get( 1 ), e );
		}
	}

	private NCEvent getEventByPID( String pid, Map< String, NCEvent > runningEvents ) {
		for( Map.Entry< String, NCEvent > ee : runningEvents.entrySet() ) {
			Map< String, String > einfo = ee.getValue().getEInfo();
			//logger.debug( "Einfo: {}! {}", einfo, ee.getValue().getEId() );
			String epid = einfo == null ? null : einfo.get( "pid" );
			if( epid != null && epid.equals( pid ) )
				return ee.getValue();
		}
		return null;
	}

	// binary search to be used if 'events' is specified, because its understood to be indexed on starttime
	public List< Event > findEvents( JsonNode eventsFs ) {
		eventsFs = eventsFs.get( "body" );
		Utils.optimizeExpr( eventsFs, NCEvent.class );
		// resizing default strctures for performance
		if( defbs == null || defbs.size() != eTimeline.size() ) {
			defbs = new BitSet( eTimeline.size() );
			defbs.flip( 0, eTimeline.size() );
		}

		BitSet revents = Utils.eventsFilter( defbs, eventsFs, this );

		List< Event > mevents = new ArrayList< Event >();
		int eii = 0;
		while( ( eii = revents.nextSetBit( eii ) ) >= 0 ) {
			int ei = eTimeline.get( eii );
			eii++;
			if( ei%2 == 1 )
				continue;
			mevents.add( events.get( ei ) );
		}

		logger.info( "findEvents: {}", mevents );
		return mevents;
	}

	public List< Integer > toList( BitSet bs ) {
		List< Integer > bsl = new ArrayList< Integer >();

		int sb = bs.nextSetBit( 0 );
		while( sb >= 0 ) {
			bsl.add( sb );
			++sb;
			sb = bs.nextSetBit( sb );
		}
		return bsl;
	}

	public BitSet evalExpr( BitSet ievents, String op, String e1, String e2 ) {
		assert( (op != null && e2 != null)
			|| (op == null && e2 == null ) );

		op = op == null ? "" : op;
		e2 = e2 == null ? "" : e2;

		Field efield = null;
		try {
			efield = NCEvent.class.getDeclaredField( e1 );
			Class<?> fc = efield == null ? null : efield.getType();
			//logger.debug( "efield: {} {} {} {}", new Object[] { efield, fc, fc.isAssignableFrom( org.joda.time.DateTime.class ) });
			if( fc == null || !fc.isAssignableFrom( DateTime.class ) )
				efield = null;
		} catch( Exception e ) {
			logger.debug( "{} not a datetime field", e1 );
			efield = null;
		}

		List< Integer > ieTimelineis = eTimelineis;
		if( ievents != defbs )
			ieTimelineis = toList( ievents );

		logger.debug( "evalExpr: {} {} {} {}", new Object[] { op, e1, e2, efield } );
		BitSet bs = null;
		if( efield != null ) {
			final DateTime paramdt = dtf.parseDateTime( e2 );
			Comparator< Integer > dtcomp = new Comparator<Integer>() {
				@Override
				public int compare( Integer eii1, Integer eii2 ) {
					logger.debug( "cmp: {} {} {}", new Object[] { eTimeline.size(), eii1, eii2 } );
					int ei = eTimeline.get( eii1 == eTimeline.size() ? eii2 : eii1 );
					NCEvent e = events.get( ei );

					DateTime dt = ei%2 == 0 ? e.getETime() : e.getEndTime();
					return dt.compareTo( paramdt );
				}
			};

			logger.debug( "binarysearch: {} {} {}", new Object[] { ieTimelineis.size(), ieTimelineis } );
			int eii = Collections.binarySearch( ieTimelineis, ieTimelineis.size(), dtcomp );
			bs = processOp( ieTimelineis, eii, op, e1 );
		} else {
			String expr = String.format( "%s %s %s", e1, op, e2 );
			Expression pexpr = Utils.exprP.parseExpression( expr );
			bs = new BitSet( eTimeline.size() );

			for( int eii : ieTimelineis ) {
				int ei = eTimelineis.get( eii );
				if( ei%2 == 1 )
					continue;

				NCEvent e = events.get( ei );
				ctxt.setVariable( "e", e );
				if( pexpr.getValue( ctxt, Boolean.class ) ) {
					bs.set( ei );
					if( e.getTimelineis().get( 1 ) > -1 )
						bs.set( e.getTimelineis().get( 1 ) );
				}
			}
		}
		return bs;
	}

	public BitSet processOp( List< Integer > ieTimelineis, int mi, String op, String fname ) {
		boolean found = mi >= 0;
		if( !found ) mi *= -1;

		int sei = 0, eei = ieTimelineis.size();
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

		BitSet bs = new BitSet( eTimeline.size() );

		int se = fname.startsWith( "s" )? 1 : 0;
		logger.debug( "procOp: {} {} {} {} {} {} {}", new Object[] { mi, op, fname, se, sei, eei, ieTimelineis });

		for( int i = sei; i < eei; i++ ) {
			int ei = eTimeline.get( ieTimelineis.get( i ) );
			if( ei%2 == se )
				continue;

			bs.set( i );
			List< Integer > timelineis = events.get( ei ).getTimelineis();
			logger.debug( "setbits: {} {}", i, timelineis );
			if( timelineis.get( se ) > -1 )
				bs.set( timelineis.get( se ) );
		}
		return bs;
	}

	public static void main( String[] args ) {
		NCEventSet es = new NCEventSet();
		final NCEvent e = new NCEvent() { {
				setETime( dtf.parseDateTime( "Jan 20 2014 00:00:00" ) );
				setEndTime( dtf.parseDateTime( "Jan 20 2014 00:00:00" ) );
			} };
		List< NCEvent > events = new ArrayList< NCEvent >() { { add( e ); } };
		ObjectMapper jsonOM = new JodaObjectMapper();
		es.addEvents( jsonOM.convertValue( events, JsonNode.class ) );
		
		try {
			//logger.debug( "{}", e );
			ctxt.setVariable( "e", e );
			ctxt.setVariable( "dtf", dtf );

			Expression pexpr = Utils.exprP.parseExpression( "#cmpDate( #e.startTime, \"Jan 20 2014 00:00:00\" )" );
			logger.debug( "expr eval: {}", (Integer)pexpr.getValue( ctxt ) );
			//Expression pexpr = Utils.exprP.parseExpression( "${date:compare( e.startTime, \"Jan 19 2014 00:00:00\" )} > 0 && ${date:compare( e.endTime, \"Jan 21 2014 00:00:00\" )} < 0", Boolean.class );
		} catch( Exception ex ) {
			logger.debug( "{}", ex.getMessage() );
		}
	}
}
