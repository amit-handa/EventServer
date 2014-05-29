package com.pimco.techops.pint.ds;
import com.pimco.techops.pint.ds.event.*;

import java.util.*;

import org.joda.time.*;	//DateTime
import org.joda.time.format.*;	//DateTimeFormat

import org.springframework.expression.*;	// expressionparser
import org.springframework.expression.spel.standard.*;	// spelexpressionparser
import org.springframework.expression.spel.support.*;	// standardevaluationcontext

import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.databind.node.*; //ObjectMapper;JsonNode

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class NCDEventSet implements EventSet {
	private static Logger logger = LoggerFactory.getLogger( NCDEventSet.class.getName() );
	private Map< DateTime, NCEventSet > eventSets = new HashMap< DateTime, NCEventSet >();

	private ObjectMapper jsonOM = Utils.jsonOM;

	public NCDEventSet() { }

	public void addEvents( JsonNode ieventsj ) {
		JsonNode cycDatej = ieventsj.get( "cycleDate" );
		assert( cycDatej != null );
		DateTime cycDate = jsonOM.convertValue( cycDatej, DateTime.class );

		NCEventSet es = eventSets.get( cycDate );
		if( es == null ) {
			es = new NCEventSet();
			eventSets.put( cycDate, es );
		}

		es.addEvents( ieventsj );
	}

	// binary search to be used if 'events' is specified, because its understood to be indexed on eTime
	public List< Event > findEvents( JsonNode eventsFs ) {
		JsonNode cycDateJ = eventsFs.get( "cycleDate" );
		assert( cycDateJ != null );

		DateTime cycDateP = jsonOM.convertValue( cycDateJ, DateTime.class );
		NCEventSet es = eventSets.get( cycDateP );
		logger.info( "FindEvents : {} {}", cycDateP, es );
		if( es == null )
			return null;

		return es.findEvents( eventsFs );
	}

	public BitSet evalExpr( BitSet ievents, String op, String e1, String e2 ) {
		return null;
}

	public static void main( String[] args ) {
		System.out.printf( "Starting NCDEventSet !!!!\n" );
		NCDEventSet es = new NCDEventSet();
		final DateTimeFormatter dtf = Utils.jsonOM.getDTFormat();
		final NCEvent e = new NCEvent() { {
			setEId( new EventID( new ArrayList<String>() { { add( "parent" ); add( "eid" ); } } ) );
			setETime( dtf.parseDateTime( "Jan 20 2014 00:00:00" ) );
			setEndTime( dtf.parseDateTime( "Jan 20 2014 00:00:00" ) );
		} };
		List< NCEvent > events = new ArrayList< NCEvent >() { { add( e ); } };
		ObjectNode ieventsj = es.jsonOM.createObjectNode();
		ieventsj.put( "values", es.jsonOM.convertValue( events, JsonNode.class ) );
		es.addEvents( ieventsj );
		
		try {
			//logger.debug( "{}", e );
			Utils.ctxt.setVariable( "e", e );
			Utils.ctxt.setVariable( "dtf", dtf );

			Expression pexpr = Utils.exprP.parseExpression( "#cmpDate( #e.eTime, \"Jan 20 2014 00:00:00\" )" );
			logger.debug( "expr eval: {}", (Integer)pexpr.getValue( Utils.ctxt ) );
			//Expression pexpr = Utils.exprP.parseExpression( "${date:compare( e.eTime, \"Jan 19 2014 00:00:00\" )} > 0 && ${date:compare( e.endTime, \"Jan 21 2014 00:00:00\" )} < 0", Boolean.class );
		} catch( Exception ex ) {
			logger.debug( "{}", ex.getMessage() );
		}
	}
}
