package com.ahanda.techops.noty.ds.event;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventTN {
	private static Logger logger = LoggerFactory.getLogger( EventTN.class.getName() );

	private EventTN pevent;
	private Map< EventID, EventTN > cevents;
	private int fromei, toei;
}
