package com.pimco.techops.pint.ds;

import java.lang.reflect.*;	//method
import java.util.*;	//list

import org.springframework.expression.*;	// expressionparser
import org.springframework.expression.spel.standard.*;	// spelexpressionparser
import org.springframework.expression.spel.support.*;	// standardevaluationcontext

import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.databind.node.*; //ArrayNode
import org.joda.time.*;	//DateTime

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class Utils {
	private static Logger logger = LoggerFactory.getLogger( Utils.class.getName() );
	public static final JodaObjectMapper jsonOM = new JodaObjectMapper();
	public static final ExpressionParser exprP = new SpelExpressionParser();

	public static final StandardEvaluationContext ctxt = new StandardEvaluationContext();
	private static final String logicOpsRegex = "&&|\\|\\|";

	static {
		try {
			ctxt.registerFunction( "cmpDate", Utils.class.getDeclaredMethod( "cmpDate", new Class[] { DateTime.class, String.class } ) );
		} catch( Exception e ) {
			logger.error( "cmpdate doesnt exist. {} {}", e.getMessage(), e.getStackTrace() );
		}
	}

	public static int cmpDate( DateTime dt1, String dt2 ) {
		int cmp = dt1.compareTo( jsonOM.dtf.parseDateTime( dt2 ) );
		logger.info( "cmpDate {} {}", dt2, cmp );
		return cmp;
	}

	public static boolean optimizeExpr( JsonNode exprs, Class<?> eventC ) {
		if( !exprs.isArray() )
			return false;

		boolean rval = false;
		List< Integer > ands = new ArrayList< Integer >();
		List< Integer > dts = new ArrayList< Integer >();
		ands.add( -1 );

		for( int i = 0; i < exprs.size(); ) {
			JsonNode sexpr = exprs.get( i );
			if( sexpr.isArray() ) {
				if( optimizeExpr( sexpr, eventC ) ) {
					dts.add( i );
					rval = true;
				}
				i++;
				continue;
			} else {
				String fstr = sexpr.asText();
				Field f = null;
				try {
					f = eventC.getDeclaredField( fstr );
				} catch( Exception e ) {
					logger.error( "finding field: {} {}", e.getMessage(), e.getStackTrace() );
				}
				if( f != null && f.getType().isAssignableFrom( DateTime.class ) ) {
					rval = true;
					dts.add( i+1 );
				} else if( fstr.matches( "&&" ) ) {
					int prevandi = ands.get( ands.size()-1 );
					int prevdti = dts.isEmpty() ? -1 : dts.get( dts.size()-1 );
					if( i-prevandi > 2 && prevdti > prevandi )
						dts.set( dts.size()-1, -1*prevdti );

					ands.add( i );
				} else if( fstr.matches( "\\|\\|" ) ) {
					int prevandi = ands.get( ands.size()-1 );
					int prevdti = dts.isEmpty() ? -1 : dts.get( dts.size()-1 );
					if( i-prevandi > 2 && prevdti > prevandi )
						dts.set( dts.size()-1, -1*prevdti );

					ands.add( i );
					orderExpr( ands, dts, (ArrayNode)exprs );
					ands.clear();
					ands.add( i );
				}
				i++;
			}
		}
		return rval;
	}

	private static void orderExpr( List< Integer > ands, List<Integer > dts, ArrayNode exprs ) {
		List< JsonNode > oexprs = new ArrayList< JsonNode >( ands.get( ands.size()-1 ) - ands.get( 0 ) );

		boolean inclOp = false;

		int dtfi = 0;
		boolean mergedExpr;
		Iterator< Integer > dti = dts.iterator();
		while( dti.hasNext() ) {
			dtfi = dti.next();
			mergedExpr = dtfi > 0;
			dtfi--;
			if( inclOp )
				oexprs.add( exprs.get( dtfi-1 ) );

			if( mergedExpr ) {
				oexprs.add( exprs.get( dtfi ) );
			} else {
				dtfi *= -1;
				oexprs.add( exprs.get( dtfi ) );
				oexprs.add( exprs.get( dtfi+1 ) );
				oexprs.add( exprs.get( dtfi+2 ) );
			}

			inclOp = true;
		}

		int j = 0;
		dtfi = j < dts.size() ? dts.get( j ) : ands.get( ands.size()-1 );
		mergedExpr = dtfi > 0;
		if( !mergedExpr )
			dtfi *= -1;
		dtfi--;
		for( int i = ands.get( 0 )+1; i < ands.get( ands.size()-1 ); ) {
			if( i == dtfi ) {
				i += mergedExpr ? 1 : 3;
				if( j == 0 ) i++;
				++j;
				dtfi = j < dts.size() ? dts.get( j ) : ands.get( ands.size()-1 );
				mergedExpr = dtfi > 0;
				if( !mergedExpr )
					dtfi *= -1;
				dtfi-=2;
			} else {
				oexprs.add( exprs.get( i ) );
				i++;
			}
		}

		assert( oexprs.size() == (ands.get( ands.size()-1 )-ands.get( 0 )-1) );
		j = 0;
		for( int i = ands.get( 0 )+1; i < ands.get( ands.size()-1 ); i++, j++ ) {
			exprs.set( i, oexprs.get( j ) );
		}
	}

	// input: array of <left side> <op> <right-side>
	public static BitSet eventsFilter( BitSet ievents, JsonNode exprs, EventSet es ) {
		String op = "";

		BitSet revents = ievents;
		Iterator< JsonNode > exprI = exprs.isArray() ? exprs.iterator() : null;
		logger.debug( "exprs: {} {}", exprs.isArray(), exprs );
		while( exprI == null || exprI.hasNext() ) {
			JsonNode expr = exprI == null ? exprs : exprI.next();
			BitSet tevents = null;
			BitSet sevents = op.startsWith( "|" ) ? ievents : revents;
			logger.debug( "filter: {} {}", op, revents.cardinality() );
			if( expr.isArray() ) {
				tevents = eventsFilter( sevents, expr, es );
			} else if( expr.asText().matches( logicOpsRegex ) ) {
				op = expr.asText();	// logical &&,||
				continue;
			} else {
				String left = expr.asText();
				String mid = exprI == null ? null : exprI.next().asText();
				String right = exprI == null ? null : exprI.next().asText();
				logger.debug( "expr: {} {} {}", new Object[] { left, mid, right } );
				tevents = es.evalExpr( sevents, mid, left, right );
			}

			if( op.isEmpty() )
				revents = tevents;
			else if( op.startsWith( "&" ) )
				revents.intersects( tevents );
			else revents.or( tevents );

			op = "";
			if( exprI == null ) {
				logger.debug( "Breaking Filtering !!!" ); break;
			}
		}

		return revents;
	}
}
