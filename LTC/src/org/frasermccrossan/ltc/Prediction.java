package org.frasermccrossan.ltc;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;

public class Prediction {

	static final Pattern DESTINATION_PATTERN = Pattern.compile("(?i) *to +((\\d+[a-z]?) +)?(.*)");
	static final int VERY_FAR_AWAY = 999999999; // something guaranteed to sort after everything

	LTCRoute route;
	String rawCrossingTime;
	String destination;
	String routeNumber;
	String errorMessage;
	Boolean seriousError; // just different visual effects when displayed
	String crossInMinutes;
	String crossAt;
	int timeDifference;
	
	Prediction(LTCRoute r, String crossTime, String dest) {
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
	}
	
	Prediction(LTCRoute r, String err, Boolean serious) {
		route = r;
		routeNumber = route.getRouteNumber();
		rawCrossingTime = destination = null;
		errorMessage = err;
		seriousError = serious;
	}
	
	Prediction(Context c, LTCRoute r, int errRes, Boolean serious) {
		route = r;
		routeNumber = route.getRouteNumber();
		rawCrossingTime = destination = null;
		Resources resources = c.getResources();
		errorMessage = resources.getString(errRes);
		seriousError = serious;
	}
	
	Boolean isValid() {
		return errorMessage == null;
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
		return crossAt;
	}
	
	String crossInMinutes() {
		return crossInMinutes;
	}
	
	// update the text time representations at the given time-stamp
	void update(Context context, Calendar time) {
		if (isValid()) {
			timeDifference = getTimeDiffAsMinutes(time, rawCrossingTime);
			if (timeDifference >= 0) {
				Calendar absTime = (Calendar)time.clone();
				absTime.add(Calendar.MINUTE, timeDifference);
				java.text.DateFormat absFormatter = DateFormat.getTimeFormat(context);
				absFormatter.setCalendar(absTime);
				crossInMinutes = minutesAsText(timeDifference);
				crossAt = absFormatter.format(absTime.getTime());
			}
			else {
				crossInMinutes = crossAt = "";				
			}
		}
		else {
			crossInMinutes = crossAt = "";
			timeDifference = VERY_FAR_AWAY;
		}
	}
	
	String minutesAsText(long minutes) {
		if (minutes < 0) {
			return "?";
		}
		if (minutes < 60) {
			return String.format("%d min", minutes);
		}
		else {
			return String.format("%dh%dm", minutes / 60, minutes % 60);
		}
	}

	/* given a time in text scraped from the web-site, get the best guess of the time it
	 * represents; we do this all manually rather than use Calendar because the AM/PM
	 * behaviour of Calendar is broken
	 */
	int getTimeDiffAsMinutes(Calendar reference, String textTime) {
		HourMinute time = new HourMinute(textTime);
		return time.timeDiff(reference);
	}

}
