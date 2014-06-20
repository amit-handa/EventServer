package com.ahanda.techops.noty.ds;

import java.util.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.vertx.java.core.json.*;	//JsonObject,JsonArray

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class ToolConfig {
	private static Logger logger = LoggerFactory.getLogger( ToolConfig.class.getName() );
	private final String toolConfFile = "toolConfig2.json";

	private String datadir;
	private JsonObject configJ;

	public ToolConfig( String datadir ) {
		this.datadir = datadir;
		String filepath = String.format( "%s/%s", datadir, toolConfFile );
		configJ = read( filepath );
	}

	public JsonObject read( String filepath ) {
		JsonObject confJ = null;
		try {
			confJ = new JsonObject( new String( Files.readAllBytes( Paths.get( filepath ) ) ) );
		} catch( Exception e ) {
			logger.error( "Error while parsing toolconfig... {} {}", e.getMessage(), e.getStackTrace() );
		}

		return confJ;
	}

	public JsonObject getConfig() {
		return configJ;
	}
}
