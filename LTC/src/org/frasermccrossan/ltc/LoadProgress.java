package org.frasermccrossan.ltc;

public class LoadProgress {
	
	static final int FAILED = -1;

	public String message;
	public int percent = 0;
	
	LoadProgress(String m, int p) {
		message = m;
		percent = p;
	}
	
	LoadProgress(String m) {
		message = m;
		percent = 0;
	}
	
	static LoadProgress makeFailed(String m) {
		return new LoadProgress(m, FAILED);
	}
	
	void chain(LoadProgress other) {
		message = other.message;
		percent = other.percent;
	}
	
	void setProgress(String m, int p) {
		message = m;
		percent = p;
	}
	
	void setFailed(String m) {
		setProgress(m, FAILED);
	}
	
	boolean isComplete() {
		return (percent >= 100 || percent < 0);
	}
	
	boolean failed() {
		return percent == FAILED;
	}
}
