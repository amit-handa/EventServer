package com.ahanda.techops.noty.ds;

import java.util.*;

import java.io.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.type.*;

public class UserConfigManager {
	private Map< String, UserConfig > userConfs;
	private static Logger logger = LoggerFactory.getLogger( UserConfigManager.class.getName() );
	// annotation-config
	private @Value( "${PINT.datadir}" ) String datadir; //$TRDATADIR
	private final String userConfFile = "userconfs.json";
	private ObjectMapper jsonOM = Utils.jsonOM;

	public UserConfigManager() {}
	public UserConfigManager( String datadir ) {
		this.datadir = datadir;
		read();
	}

	public void read() {
		String filepath = datadir + "/" + userConfFile;
		userConfs = _read( filepath );

		if( userConfs == null )
			userConfs = new HashMap< String, UserConfig >();

	    logger.debug( "deserialization info {}", new Object[] { userConfs.size() } );
	}

	private Map< String, UserConfig > _read( String filepath ) {
		Map< String, UserConfig > uc = new HashMap< String, UserConfig >();

		Path path = Paths.get( filepath );
		if( Files.exists( path ) ) {
			try {
				// Open FileInputStream to the file
				FileInputStream fis = new FileInputStream( filepath );
				MapType mtype = jsonOM.getTypeFactory().constructMapType( HashMap.class, String.class, UserConfig.class );
				uc = jsonOM.readValue( fis, mtype );

				fis.close();
			} catch( Exception e ) {
				logger.error( "deserialization error: {} {}", e.getMessage(), e.getStackTrace() );
			}
		}

		return uc;
	}

	public UserConfig getUserConf( String userId ) {
		UserConfig uc = userConfs.get( userId );
		return uc;
	}

	public void setUserConf( String userId, UserConfig uc ) {
		userConfs.put( userId, uc );
	}

	@JsonProperty( "userConfs" )
	public Map< String, UserConfig > getUserConfs() {
		return userConfs;
	}

	public void setUserConfs( Map< String, UserConfig > ucs ) {
		userConfs = ucs;
	}
}
