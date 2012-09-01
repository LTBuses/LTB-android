package org.frasermccrossan.ltc;

public interface ScrapeStatus {

	public static final int IDLE = 0;
	public static final int QUERYING = 1;
	public static final int OK = 2;
	public static final int FAILED = 3;

	public void setStatus(int newState, String message);
	
}
