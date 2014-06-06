package com.ahanda.techops.noty.ds.event;

import com.fasterxml.jackson.annotation.*;

import java.util.*;
import org.joda.time.*;	// DateTime
import org.apache.commons.lang3.*;

public class Event {
	protected EventID eId;

	protected DateTime eTime;
	protected String status;
	protected String message;

	public Event() {}

	@JsonProperty( "eId" )
	public EventID getEId() {
		return eId;
	}

	public void setEId( EventID eId ) {
		this.eId = eId;
	}

	@JsonProperty( "eTime" )
	public DateTime getETime() {
		return eTime;
	}

	public void setETime( DateTime eTime ) {
		this.eTime = eTime;
	}

	@JsonProperty( "status" )
	public String getStatus() {
		return status;
	}

	public void setStatus( String status ) {
		this.status = status;
	}

	@JsonProperty( "message" )
	public String getMessage() {
		return message;
	}

	public void setMessage( String message ) {
		this.message = message;
	}

	public int compareTo( Event o ) {
		assert( eTime != null && o.getETime() != null);
		assert( eId != null && o.getEId() != null);
		int rval = eTime.compareTo( o.getETime() );
		if( rval == 0 )
			rval = eId.compareTo( o.getEId() );

		return rval;
	}

	public String toString() {
		return String.format( "[ Event : %s, %s, %s, %s ]", eId, eTime, status, message );
	}
}
