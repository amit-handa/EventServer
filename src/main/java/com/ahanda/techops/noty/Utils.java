package com.ahanda.techops.noty;

import java.lang.reflect.*;	//method
import java.util.*;	//list

import org.vertx.java.core.json.*;	//JsonObject,JsonArray

import javax.crypto.*; //Mac
import org.apache.commons.codec.binary.Base64;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class Utils {
	private static Logger logger = LoggerFactory.getLogger( Utils.class.getName() );
	public static final String macAlgoName = "HmacSHA256";

	public static JsonObject checkCredential( final Mac mac, final JsonObject msg ) {
		String userId = msg.getString( "userId" );
		String password = msg.getString( "password" );
		String sessAuth = null;

		if( password != null ) {
			long sessStart = System.currentTimeMillis() / 1000L;
			sessAuth = getSessAuth( mac, userId, sessStart );
			JsonObject reply = new JsonObject().putString( "userId", userId );
			reply.putNumber( "sessStart", sessStart );
			reply.putString( "sessAuth", sessAuth );
			reply.putString( "status", "ok" );
			return reply;
		}

		sessAuth = msg.getString( "sessAuth" );
		String tmp = msg.getString( "sessStart" );
		long sessStart = Long.valueOf( tmp );
		String nsessAuth = getSessAuth( mac, userId, sessStart );
		if( sessAuth.equals( nsessAuth ) )
			msg.putString( "status", "ok" );
		else msg.putString( "status", "error" );
		return msg;
	}

	public static String getSessAuth( final Mac mac, String userId, long sessStart ) {
		String cval = String.format("userId=%s&sessStart=%d", userId, sessStart );
		return new String(Base64.encodeBase64(mac.doFinal(cval.getBytes())));
	}

	public static int compare( JsonObject o1, JsonObject o2 ) {
		logger.debug( "Compare JsonObject {} {}", o1, o2 );
		if( o1.size() != o2.size() )
			return o1.size() < o2.size() ? -1 : 1;

		Iterator< String > o1ksi = o1.getFieldNames().iterator();
		Iterator< String > o2ksi = o2.getFieldNames().iterator();
		while( o1ksi.hasNext() ) {
			String s1 = o1ksi.next();
			String s2 = o2ksi.next();

			if( s1.equals( s2 ) )
				continue;

			return s1.compareTo( s2 );
		}

		int rval = 0;
		o1ksi = o1.getFieldNames().iterator();
		o2ksi = o2.getFieldNames().iterator();
		while( o1ksi.hasNext() ) {
			Object kv1 = o1.getValue( o1ksi.next() );
			Object kv2 = o2.getValue( o2ksi.next() );

			rval = compare( kv1, kv2 );
			if( rval != 0 )
				break;
		}

		return rval;
	}

	public static int compare( JsonArray o1, JsonArray o2 ) {
		logger.debug( "Compare JsonArray {} {}", o1, o2 );
		if( o1.size() != o2.size() )
			return o1.size() < o2.size() ? -1 : 1;

		int rval = 0;
		for( int i = 0; i < o1.size(); i++ ) {
			Object kv1 = o1.get( i );
			Object kv2 = o2.get( i );

			rval = compare( kv1, kv2 );
			if( rval != 0 )
				break;
		}

		return rval;
	}

	public static int compare( Object o1, Object o2 ) {
		logger.debug( "Compare Object! {} {}", o1, o2 );
		Class<?> clso1 = o1.getClass();
		Class<?> clso2 = o2.getClass();
		if( !clso1.equals( clso2 ) )
			return clso1.toString().compareTo( clso2.toString() );

		if( JsonObject.class.isInstance( o1 ) )
			return compare( (JsonObject)o1, (JsonObject)o2 );
		if( JsonArray.class.isInstance( o1 ) )
			return compare( (JsonArray)o1, (JsonArray)o2 );
		if( Comparable.class.isInstance( o1 ) )
			return ((Comparable)o1).compareTo( (Comparable)o2 );

		return -9;
	}
}
