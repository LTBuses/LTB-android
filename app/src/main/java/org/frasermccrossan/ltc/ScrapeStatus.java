package org.frasermccrossan.ltc;

public interface ScrapeStatus {

	public static final int IDLE = 0;
	public static final int QUERYING = 1;
	public static final int OK = 2;
	public static final int FAILED = 3;
	
	public static final int NOT_PROBLEM = 0;
	public static final int PROBLEM_IMMEDIATELY = 1;
	public static final int PROBLEM_IF_ALL = 2;

	public void setStatus(int newState, String message);
	public void setStatus(int newState, int newProbType, String message);

}
