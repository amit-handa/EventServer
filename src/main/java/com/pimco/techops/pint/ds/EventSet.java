package com.pimco.techops.pint.ds;

import com.pimco.techops.pint.ds.event.*;
import com.fasterxml.jackson.databind.*; //ObjectMapper;JsonNode
import java.util.*;	//list

public interface EventSet {
	Map< String, String > types = new HashMap< String, String >();
	void addEvents( JsonNode events );
	List< Event > findEvents( JsonNode eventsFs );
	BitSet evalExpr( BitSet ievents, String op, String e1, String e2 );
}
