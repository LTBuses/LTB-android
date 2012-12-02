package org.frasermccrossan.ltc;

public class LoadProgress {

	public String message;
	public int percent = 0;
	
	LoadProgress(String m, int p) {
		message = m;
		percent = p;
	}
	
	void chain(LoadProgress other) {
		message = other.message;
		percent = other.percent;
	}
	
	boolean isComplete() {
		return (percent >= 100 || percent < 0);
	}
}
