package com.ahanda.techops.noty.ds;

import com.ahanda.techops.noty.ds.event.*;

import java.util.*;
import java.text.*;	// simpledatetime

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.joda.time.*;
import org.joda.time.format.*;	//DateTimeFormat

import java.sql.*;

import javax.sql.*;	//datasource

import org.springframework.jdbc.core.*;	//rowmapper
import org.springframework.jdbc.core.simple.*;	//SimpleJdbcTemplate

public class ETACache {
	private static Logger logger = LoggerFactory.getLogger( ETACache.class.getName() );
	private DataSource orads, sybds;
	private Map< Map< String, String >, List< Event > > esrcsETAs = null;
	private static final DateTimeFormatter dtf = DateTimeFormat.forPattern( "mm");

	public ETACache() {
		esrcsETAs = new HashMap< Map< String, String >, List< Event > >();
	}

	public void setOraDataSource( DataSource ds ) {
		logger.debug( "Ora DATA SOURCE {}", ds );
		this.orads = ds;
	}

	public void setSybDataSource( DataSource ds ) {
		logger.debug( "Syb DATA SOURCE {}", ds );
		this.sybds = ds;
	}

	public List< Event > getETAs( Map< String, String > esrc ) {
		List< Event > eventETAs = esrcsETAs.get( esrc );

		if( eventETAs == null ) {
			String dbEnv = esrc.get( "dbEnv" );
			DataSource ds = null;
			if( dbEnv.matches( "SYB.*" ) )
				ds = sybds;
			else if( dbEnv.matches( "ORA.*" ) )
				ds = orads;

			if( ds == null ) {
				logger.debug( "No DS for the src, returning" );
				return null;
			}

			logger.debug( "getETA: {} {}", dbEnv, esrc.get( "region" ) );
			SimpleJdbcTemplate sqlExec = new SimpleJdbcTemplate( ds );

			String etasql = "select job_id, parent_job_id, mins_from_base_job from pm_abs..time_from_base_job where region_code = ? order by mins_from_base_job";
			etasql = "select count(*) from pm_abs..time_from_base_job where region_code = ?";
			RowMapper<Event> etaDB2J = new RowMapper< Event >() {
				public Event mapRow( ResultSet rs, int rowNum ) throws SQLException {
					final ResultSet rsf = rs;
					return new Event() { {
						setEId( new EventID( new ArrayList<String>() { { add( rsf.getString( "job_id" ) ); } } ) );
						setETime( dtf.parseDateTime( rsf.getString( rsf.getString( "mins_from_base_job" ) ) ) );
					} };
				}
			};
			//eventETAs = sqlExec.query( etasql, etaDB2J, esrc.get( "region" ).charAt( 0 ) );
			logger.debug( "sqlexec: {}", sqlExec.queryForInt( etasql, esrc.get( "region" ).charAt( 0 ) ) );
			esrcsETAs.put( esrc, eventETAs );
		}

		return eventETAs;
	}
}
