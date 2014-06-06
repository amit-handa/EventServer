package com.ahanda.techops.noty.ds;
import java.util.*;
import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode

public class EventSets {
	private EventSets() {}	// prevent instantiation

	private static final Map< String, Class<? extends EventSet > > esets = new HashMap< String, Class<? extends EventSet> >();

	static {	// static initializers
		esets.put( "NightCycle", NCEventSet.class );
		esets.put( "Topaz", TopazEventSet.class );
	}

	public static Class<? extends EventSet > get( String esetID ) {
		return esets.get( esetID );
	}
}
