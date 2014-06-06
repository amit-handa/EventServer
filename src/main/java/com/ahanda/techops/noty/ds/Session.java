package com.ahanda.techops.noty.ds;

public class Session {
	private int sessId;
	public static final long validityWindow = 30*60*60;	// 30 hours

	public Session( int sessId ) {
		this.sessId = sessId;
	}

	public int getSessId() {
		return sessId;
	}

	public void setSessId( int sessId ) {
		this.sessId = sessId;
	}
}
