package com.ahanda.techops.noty.ds;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.*;
import java.text.*;
import com.fasterxml.jackson.core.*;//JsonParser
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.*;	//simplemodule
import com.fasterxml.jackson.databind.deser.std.*;	//stddeserializer
import com.fasterxml.jackson.databind.ser.std.*;	//stddeserializer

import com.fasterxml.jackson.datatype.joda.*;

import org.springframework.beans.factory.annotation.Value;

import org.joda.time.format.*;	//DateTimeFormat
import org.joda.time.*;	//datetime

public class JodaObjectMapper extends ObjectMapper {
	private static Logger logger = LoggerFactory.getLogger( JodaObjectMapper.class.getName() );
	public @Value( "PINT.dateTime" ) String datetime;
	public final DateTimeFormatter dtf;

	JodaObjectMapper() {
		super();
		if( datetime == null )
			datetime = "MMM dd yyyy HH:mm:ss";

		dtf = DateTimeFormat.forPattern( datetime );

		configure( SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false );
		setDateFormat( new SimpleDateFormat( datetime ) );
		//registerModule( new JodaModule() );
		SimpleModule dtmod = new SimpleModule() { {
			addDeserializer( org.joda.time.DateTime.class, //new JodaDTDeser() );
				new StdDeserializer<DateTime>( DateTime.class ) {
					@Override
					public DateTime deserialize( JsonParser p, DeserializationContext context ) throws IOException {
						return dtf.parseDateTime( p.getText() );
					}
				} );
			addSerializer( org.joda.time.DateTime.class, //new JodaDTDeser() );
				new StdSerializer<DateTime>( DateTime.class ) {
					@Override
					public void serialize( DateTime val, JsonGenerator jgen, SerializerProvider provider ) throws IOException {
						jgen.writeString( dtf.print( val ) );
					}
				} );
		} };
		registerModule( dtmod );
	}

	class JodaDTDeser extends JsonDeserializer<DateTime> {
		@Override
		public DateTime deserialize( JsonParser p, DeserializationContext context ) throws IOException {
			return DateTimeFormat.forPattern( "MMM dd yyyy HH:mm:ss")
				.parseDateTime( p.getText() );
		}
	}

	public DateTimeFormatter getDTFormat() {
		return dtf;
	}

	public static void main( String[] args ) {
		JodaObjectMapper om = new JodaObjectMapper();
		try {
			DateTime dt = om.readValue( "\"Jan 20 2014 00:00:00\"", DateTime.class );
			//String dt = om.readValue( "\"a b c\"", String.class );
			logger.debug( "{}", om.writeValueAsString( dt ) );
		} catch( Exception e ) {
			logger.debug( "{} {}", e.getMessage(), e.getStackTrace() );
		}
	}
}


