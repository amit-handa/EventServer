package com.ahanda.techops.noty.ds;

import java.util.*;

import java.io.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.annotation.*;	//jsonproperty
import com.fasterxml.jackson.databind.ObjectMapper;

public class ToolConfig {
	private static Logger logger = LoggerFactory.getLogger( ToolConfig.class.getName() );
	Map< String, ESrcInfo > eventSources;
	Map< String, String > eSrcClass;

	public static class ESrcInfo {
		public ArrayList< Map< String, ArrayList< String > > > idInfo;
		public ArrayList< Map< String, String > > ids;
	}

	// annotation-config
	private @Value( "${PINT.datadir}" ) String datadir; //$TRDATADIR
	private final String toolConfFile = "toolConfig.json";

	public ToolConfig() {}

	public ToolConfig( String datadir ) {
		this.datadir = datadir;
		read();
	}

	public void read() {
		String filepath = String.format( "%s/%s", datadir, toolConfFile );
		ToolConfig tc = _read( filepath );

		if( tc == null ) {
			eventSources = new HashMap< String, ESrcInfo >();
		} else {
			eventSources = tc.getEventSources();
			EventSet.types.putAll( tc.eSrcClass );
		}

	    logger.debug( "deserialization info {} {} {}", new Object[] { eventSources.size() } );
	}

	private ToolConfig _read( String filepath ) {
		ToolConfig tc = null;

		ObjectMapper jsonOM = Utils.jsonOM;
		Path path = Paths.get( filepath );
		if( Files.exists( path ) ) {
			try {
			  // Open FileInputStream to the file
			  FileInputStream fis = new FileInputStream( filepath );
			  tc = jsonOM.readValue( fis, ToolConfig.class );
			  fis.close();
			} catch( Exception e ) {
				logger.error( "deserialization error: {} {}", e.getMessage(), e.getStackTrace() );
			}
		}

		return tc;
	}

	public Map< String, ESrcInfo > getEventSources() {
		return eventSources;
	}

	public void setEventSources( Map< String, ESrcInfo > eventSources ) {
		this.eventSources = eventSources;
	}

	@JsonProperty( "eSrcClass" )
	public Map< String, String > getESrcClass() {
		return eSrcClass;
	}

	public void setESrcClass( Map< String, String > eSrcClass ) {
		this.eSrcClass = eSrcClass;
	}

	public static void main( String[] args ) {
		assert( args.length == 1 );
		ToolConfig tc = new ToolConfig();
		tc.datadir = args[0];
		tc._read( String.format( "%s/%s", tc.datadir, tc.toolConfFile ) );
	}
}
