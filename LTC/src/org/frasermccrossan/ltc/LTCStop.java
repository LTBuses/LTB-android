package org.frasermccrossan.ltc;

public class LTCStop {
	
	public Integer number;
	public String name;
	public double latitude;
	public double longitude;
	
	LTCStop(Integer number, String name) {
		this.number = number.intValue();
		this.name = name;
	}
	
	LTCStop(Integer number, String name, double lat, double longi) {
		this.number = number.intValue();
		this.name = name;
		this.latitude = lat;
		this.longitude = longi;
	}

}
