package com.ahanda.techops.noty.ds.event;

import com.fasterxml.jackson.annotation.*;

import java.util.*;
import org.joda.time.*;	// DateTime
import org.apache.commons.lang3.*;

public class NCEvent extends Event implements Comparable<NCEvent> {
	private Map< String, String > eTypeInfo;
	private Map< String, String > eInfo;

	private DateTime endTime;

	public static final List< NCEvent > defceis = new ArrayList< NCEvent >();
	public transient List< NCEvent > cevents = null;	// ordered by starttimes
	public transient List< Integer > timelineis = null;

	@JsonIgnore
	public List< Integer > getTimelineis() {
		if( timelineis == null ) {
			timelineis = new ArrayList< Integer >( 2 );
			timelineis.add( -1 );	// startime
			timelineis.add( -1 );	// endtime
		}
		return timelineis;
	}

	public NCEvent() {
	}

	public void addChild( NCEvent ce ) {
		if( cevents == null )
			cevents = new ArrayList< NCEvent >();
		cevents.add( ce );
	}

	public void printTree( int level ) {
		StringBuilder sb = new StringBuilder();
		sb.append( level );
		String space = " ";
		sb.append( StringUtils.repeat( space, level ) );
		sb.append( eId.getIds().get( 1 ) );
		if( cevents != null ) {
			sb.append( ":" );
			for( NCEvent ce : cevents ) {
				sb.append( " ," );
				sb.append( ce.getEId().getIds().get( 1 ) );
			}
		}
		System.out.println( sb );
		if( cevents != null ) {
			for( NCEvent ce : cevents )
				ce.printTree( level+1 );
		}
	}

	@JsonIgnore
	public List< NCEvent > getChilds() {
		return cevents;
	}

	@JsonProperty( "eTypeInfo" )
	public Map< String, String > getETypeInfo() {
		return eTypeInfo;
	}

	public void setETypeInfo( Map< String, String > eTypeInfo ) {
		this.eTypeInfo = eTypeInfo;
	}

	@JsonProperty( "eInfo" )
	public Map< String, String > getEInfo() {
		return eInfo;
	}

	public void setEInfo( Map< String, String > eInfo ) {
		this.eInfo = eInfo;
	}

	public DateTime getEndTime() {
		return endTime;
	}

	public void setEndTime( DateTime endTime ) {
		this.endTime = endTime;
	}

	public int compareTo( NCEvent o ) {
		assert( eTime != null && o.getETime() != null);
		int rval = eTime.compareTo( o.getETime() );

		if( rval != 0 )
			return rval;

		if( endTime == null && o.getEndTime() == null )
			return 0;
		if( endTime == null )
			return 1;
		if( o.getEndTime() == null )
			return -1;

		return endTime.compareTo( o.getEndTime() );
	}

	public String toString() {
		return eId.toString();
	}
}
