package org.frasermccrossan.ltc;

public class ScrapeException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	int problemType;
	Boolean seriousProblem;

	ScrapeException(String message, int prob, Boolean serious) {
		super(message);
		problemType = prob;
		seriousProblem = serious;
	}
	
}
