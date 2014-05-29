package com.pimco.techops.pint.ds;
//userConfig { userid, timezone, eventSub, uiLayout }

//import java.util.ArrayList;

//import com.pimco.techops.pint.ds.EventsSub;
//import com.pimco.techops.pint.ds.UILayout;

import com.fasterxml.jackson.annotation.*;

public class UserConfig {
	private String uiLayout;
	private String role;
	private String password;

	public void setUiLayout( String uiLayout ) {
		this.uiLayout = uiLayout;
	}

	@JsonProperty( "uiLayout" )
	public String getUiLayout() {
		return uiLayout;
	}

	public void setRole( String role ) {
		this.role = role;
	}

	@JsonProperty( "role" )
	public String getRole() {
		return role;
	}

	public void setPassword( String password ) {
		this.password = password;
	}

	@JsonProperty( "password" )
	public String getPassword() {
		return password;
	}
}
