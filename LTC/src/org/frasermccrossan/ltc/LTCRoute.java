package org.frasermccrossan.ltc;

public class LTCRoute {

	public String number; // string because parts of website require leading zeros e.g. "02"
	public String name;
	public int direction; // not part of the table, but here for convenience
	public String directionName; // cache of the direction letter
	
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
	
	LTCRoute(String number, String name, int dir, String dirName) {
		this.number = number;
		this.name = name;
		this.direction = dir;
		this.directionName = dirName;
	}
	
	String getRouteNumber() {
		return number.replaceFirst("^0+", "");
	}
	
	String getShortRouteDirection() {
		return String.format("%d%s", Integer.valueOf(number), directionName == null ? "" : directionName.substring(0,1));
	}
	
	String getShortDirection() {
		return directionName.substring(0,1);
	}
	
	@Override
	public String toString() {
		return getShortRouteDirection(); // ew, dirty hack
	}
}
