package org.frasermccrossan.ltc;

public class LoadProgress {
	
	static final int FAILED = -1;

	public String title;
	public String message;
	public int percent = 0;
	boolean alert = false;
	
	LoadProgress() {
		title = message = "";
		percent = 0;
	}
	
	LoadProgress title(String t) {
		title = t;
		return this;
	}
	
	LoadProgress message(String m) {
		message = m;
		return this;
	}
	
	LoadProgress percent(int p) {
		percent = p;
		return this;
	}
	
	LoadProgress complete() {
		percent = 100;
		alert = true;
		return this;
	}
	
	LoadProgress reset() {
		title = message = "";
		percent = 0;
		alert = false;
		return this;
	}
	
	LoadProgress failed() {
		percent = FAILED;
		alert = true;
		return this;
	}
	
	boolean isComplete() {
		return (percent >= 100 || percent < 0);
	}
	
	boolean isFailed() {
		return percent == FAILED;
	}
	
}
