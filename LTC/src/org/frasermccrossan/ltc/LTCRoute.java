package org.frasermccrossan.ltc;

public class LTCRoute {

	public String number; // string because parts of website require leading zeros e.g. "02"
	public String name;
	public int direction; // not part of the table, but here for convenience
	//public String url;
	
	LTCRoute(String number, String name/*, String url*/) {
		this.number = number;
		this.name = name;
		//this.url = url;
	}
	
	LTCRoute(String number, String name, int dir) {
		this.number = number;
		this.name = name;
		this.direction = dir;
	}
	
	@Override
	public String toString() {
		return "#" + number; // ew, dirty hack
	}
}
