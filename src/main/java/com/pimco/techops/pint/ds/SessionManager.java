package com.pimco.techops.pint.ds;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pimco.techops.pint.ds.*;

public class SessionManager {
	private Logger logger = LoggerFactory.getLogger( SessionManager.class.getName() );
	private Map< String, ArrayList< Session > > sessm;

	public SessionManager() {
		sessm = new HashMap< String, ArrayList< Session > >();
	}

	public Session openSession( String userId ) {
		logger.info( "New Session {}", userId );

		ArrayList< Session > sessl = sessm.get( userId );
		if( sessl == null ) {
			sessl = new ArrayList< Session >();
			sessm.put( userId, sessl );
		}

		Session s = new Session( sessl.size() );
		sessl.add( s );

		return s;
	}

	public Session getSession( String userId, int uiid ) {
		try {
			ArrayList< Session > sessl = sessm.get( userId );
			return sessl.get( uiid );
		} catch( Exception e ) {
			return (Session)null;
		}
	}

	public void closeSession( String userId, int uiid ) {
		ArrayList< Session > sessl = sessm.get( userId );
		sessl.set( uiid, null );
	}

}
