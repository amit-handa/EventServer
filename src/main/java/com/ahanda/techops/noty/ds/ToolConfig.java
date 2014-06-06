package com.ahanda.techops.noty.ds;

import java.util.*;

import java.io.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.type.*; //typereference
import com.fasterxml.jackson.annotation.*;	//jsonproperty
import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import com.fasterxml.jackson.databind.node.*; //objectnode

public class ToolConfig {
	private static Logger logger = LoggerFactory.getLogger( ToolConfig.class.getName() );
	JsonNode configJ;
	Map< Map< String, String >, JsonNode > leafNodes;

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
		configJ = _read( filepath );

		if( configJ != null ) {
			leafNodes = new HashMap< Map< String, String >, JsonNode >();
			popuRevMap( configJ );
		}

	    logger.debug( "deserialization info {} {} {}", new Object[] { configJ.size() } );
	}

	public void popuRevMap( JsonNode configJ ) {
		JsonNode childs = configJ.get( "children" );
		if( childs == null ) {	// leaf level node
			Map< String, String > id = Utils.jsonOM.convertValue( configJ.get( "id" ), new TypeReference< Map< String, String > >() {} );
			leafNodes.put( id, configJ );
			return;
		}

		for( int i = 0; i < childs.size(); i++ )
			popuRevMap( childs.get( i ) );
	}

	public JsonNode getConfig() {
		return configJ;
	}

	private JsonNode _read( String filepath ) {
		JsonNode tc = null;

		ObjectMapper jsonOM = Utils.jsonOM;
		Path path = Paths.get( filepath );
		if( Files.exists( path ) ) {
			try {
			  // Open FileInputStream to the file
			  FileInputStream fis = new FileInputStream( filepath );
			  tc = jsonOM.readTree( fis );
			  fis.close();
			} catch( Exception e ) {
				logger.error( "deserialization error: {} {}", e.getMessage(), e.getStackTrace() );
			}
		}

		return tc;
	}

	public String getESrcClass( Map< String, String > esrc ) {
		JsonNode treeN = leafNodes.get( esrc );
		if( treeN == null )
			return null;

		JsonNode idN = treeN.findParent( "idType" );
		if( idN == null )
			return null;

		return idN.get( "idType" ).asText();
	}

	public static void main( String[] args ) {
		assert( args.length == 1 );
		ToolConfig tc = new ToolConfig();
		tc.datadir = args[0];
		tc._read( String.format( "%s/%s", tc.datadir, tc.toolConfFile ) );
	}
}