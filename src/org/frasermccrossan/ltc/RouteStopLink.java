package org.frasermccrossan.ltc;

public class RouteStopLink {

	public String routeNumber;
	public Integer directionNumber;
	public Integer stopNumber;
	
	RouteStopLink(String route, Integer direction, Integer stop) {
		this.routeNumber = route;
		this.directionNumber = direction;
		this.stopNumber = stop;
	}	

}
