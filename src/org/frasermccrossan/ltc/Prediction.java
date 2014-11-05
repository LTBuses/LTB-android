package org.frasermccrossan.ltc;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;

public class Prediction implements Comparable<Prediction> {

	static final Pattern DESTINATION_PATTERN = Pattern.compile("(?i) *to +((\\d+[a-z]?) +)?(.*)");
	static final int OVER_12_HOURS = 12*60+5;
	static final int VERY_FAR_AWAY = 999999999; // something guaranteed to sort after everything

	LTCRoute route;
	String rawCrossingTime;
	String destination;
	String routeNumber;
	String errorMessage;
	Boolean seriousError; // just different visual effects when displayed
	String crossInMinutes;
	String crossAt;
	HourMinute hourMin;
	public int timeDifference;
	Boolean isQuerying = false;
	
	Prediction(LTCRoute r, String crossTime, String dest, Calendar reference) {
		route = r;
		routeNumber = route.getRouteNumber();
		rawCrossingTime = crossTime;
		destination = dest;
		errorMessage = null;
		seriousError = false;
		Matcher destMatcher = DESTINATION_PATTERN.matcher(destination);
		if (destMatcher.find()) {
			// a heuristic to convert "2 TO 2A Bla bla Street" into "2A Bla Bla Street"
			destination = destMatcher.group(3);
			if (destMatcher.group(2) != null) {
				routeNumber = destMatcher.group(2);
			}
		}
		hourMin = new HourMinute(reference, crossTime);
		timeDifference = hourMin.timeDiff(reference);
	}
	
	Prediction(LTCRoute r, String err, Boolean serious) {
		route = r;
		routeNumber = route.getRouteNumber();
		rawCrossingTime = destination = null;
		errorMessage = err;
		seriousError = serious;
		timeDifference = VERY_FAR_AWAY;
	}
	
	Prediction(Context c, LTCRoute r, int errRes, Boolean serious) {
		route = r;
		routeNumber = route.getRouteNumber();
		rawCrossingTime = destination = null;
		Resources resources = c.getResources();
		errorMessage = resources.getString(errRes);
		seriousError = serious;
		timeDifference = VERY_FAR_AWAY;
	}
	
	Boolean isValid() {
		return errorMessage == null;
	}
	
	Boolean blankDestination() {
		return destination != null && destination.equals("");
	}
	
	Boolean isError() {
		return !isValid();
	}
	
	Boolean isSerious() {
		return seriousError;
	}
	
	Boolean isOnRoute(LTCRoute otherRoute) {
		return otherRoute.number.equals(route.number) && otherRoute.directionName.equals(route.directionName);
	}
	
	String routeNumber() {
		return routeNumber;
	}
	
	int routeDirectionImgRes() {
		return route.getDirectionDrawableRes();
	}
	
	String routeLongName() {
		return route.name;
	}
	
	String destination() {
		if (errorMessage == null) {
			return destination;
		}
		else {
			return errorMessage;
		}
	}
	
	String crossAt() {
		if (isQuerying) {
			return "";
		}
		else {
			return crossAt;
		}
	}
	
	String crossInMinutes() {
		if (isQuerying) {
			return "";
		}
		else {
			return crossInMinutes;
		}
	}
	
	// update the text time representations at the given time-stamp
	void updateFields(Context context, Calendar time) {
		if (isValid()) {
			if (timeDifference >= 0 && timeDifference < HourMinute.DAY_MINUTES - 60) {
				Calendar absTime = (Calendar)time.clone();
				absTime.add(Calendar.MINUTE, timeDifference);
				java.text.DateFormat absFormatter = DateFormat.getTimeFormat(context);
				absFormatter.setCalendar(absTime);
				crossInMinutes = minutesAsText(context, timeDifference);
				crossAt = absFormatter.format(absTime.getTime());
			}
			else {
				crossInMinutes = crossAt = "";				
			}
		}
		else {
			crossInMinutes = crossAt = "";
		}
	}
	
	String minutesAsText(Context c, long minutes) {
		if (minutes < 0) {
			return "?";
		}
		if (minutes < 60) {
			return String.format("%d min", minutes);
		}
		if (minutes > OVER_12_HOURS) {
			return "----";
		}
		else {
			return String.format("%dh%dm", minutes / 60, minutes % 60);
		}
	}

	void setQuerying() {
		isQuerying = true;
	}
	
	Boolean isQuerying() {
		return isQuerying;
	}
	
	@Override
	public int compareTo(Prediction other) {
		int timeDiff = timeDifference - other.timeDifference;
		if (timeDiff != 0) {
			return timeDiff;
		}
		return route.number.compareTo(other.route.number);
	}

}
