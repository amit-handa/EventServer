package com.ahanda.techops.noty.ds.event;

import java.util.*;	//List
import com.fasterxml.jackson.annotation.*;	//jsonproperty

import org.apache.commons.lang3.builder.*;

public class EventID {
	private List< String > ids;

	public EventID() {}

	public EventID( List< String > ids ) {
		this.ids = ids;
	}

	@JsonProperty( "ids" )
	public List< String > getIds() {
		return ids;
	}

	public void setIds( List< String > ids ) {
		this.ids = ids;
	}


	public int hashCode() {
		return new HashCodeBuilder( 17, 37 ).append( ids ).toHashCode();
	}

	public int compareTo( EventID o ) {
		return new CompareToBuilder().append( ids, o.ids ).toComparison();
	}

	public boolean equals( Object o ) {
		if( o == null )
			return false;
		if( !(o instanceof EventID ) )
			return false;

		EventID eid = (EventID)o;
		return new EqualsBuilder().append( ids, eid.getIds() ).isEquals();
	}

	public String toString() {
		return String.format( "%s", ids );
	}
}
