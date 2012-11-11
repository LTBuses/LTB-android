package org.frasermccrossan.ltc;

public class ScrapeException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	int problemType;

	ScrapeException(String message) {
		super(message);
		problemType = ScrapeStatus.NOT_PROBLEM;
	}
	
	ScrapeException(String message, int prob) {
		super(message);
		problemType = prob;
	}
	
}
