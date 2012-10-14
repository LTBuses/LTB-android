package org.frasermccrossan.ltc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.text.TextUtils.SimpleStringSplitter;
import android.text.TextUtils.StringSplitter;

// everything required to load the LTC_supplied data into the database
@SuppressLint("UseSparseArrays")
public class LTCScraper {

	BusDb db = null;
	ScrapingStatus status = null;
	Context c;
	// for development only
//	static final String ROUTE_URL = "http://teuchter.lan:8000/routes.html";
//	static final String DIRECTION_URL = "http://teuchter.lan:8000/direction%s.html";
//	static final String STOPS_URL = "http://teuchter.lan:8000/direction%sd%d.html";
//	static final String MAP_URL = "http://teuchter.lan:8000/map%s.html";
	// for production
	static final String ROUTE_URL = "http://www.ltconline.ca/WebWatch/ada.aspx?mode=d";
	static final String DIRECTION_URL = "http://www.ltconline.ca/WebWatch/ada.aspx?r=%s";
	static final String STOPS_URL = "http://www.ltconline.ca/WebWatch/ada.aspx?r=%s&d=%d";
	static final String MAP_URL = "http://teuchter.lan:8000/map%s.html";
	static final String PREDICTIONS_URL = "http://teuchter.lan:8000/foo.html";
	static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}) ?([AP])?");
	static final Pattern LOCATION_STOP_PATTERN = Pattern.compile("(\\d+)");
	static final String VERY_FAR_AWAY = "999999999999999"; // something guaranteed to sort after everything
	static final int DAY_MINUTES = 24 * 60;
	static final int HALF_DAY_MINUTES = DAY_MINUTES / 2;
	
	LTCScraper(Context c, ScrapingStatus s) {
		db = new BusDb(c);
		status = s;
	}
	
	LTCScraper() {
		/* no init - only instantiate this way if you only plan to check bus
		 * predictions
		 */
	}
	
	public void close() {
		if (db != null) {
			db.close();
		}
	}
	
	public void loadAll() {
		LoadTask task = new LoadTask();
		task.execute();
	}
	
	String predictionUrl(LTCRoute route, String stopNumber) {
//		return PREDICTIONS_URL;
		return String.format("http://www.ltconline.ca/WebWatch/ada.aspx?r=%s&d=%s&s=%s",
				route.number, route.direction, stopNumber);
	}
	
	/* given a time in text scraped from the website, get the best guess of the time it
	 * represents; we do this all manually rather than use Calendar because the AM/PM
	 * behaviour of Calendar is broken
	 */
	int getTimeDiffAsMinutes(Calendar reference, Pattern timePattern, String textTime) {
		Matcher m = timePattern.matcher(textTime);
		if (m.find()) {
			int hour=Integer.valueOf(m.group(1));
			if (hour == 12) { hour = 0; } // makes calculations easier
			int minute = Integer.valueOf(m.group(2));
			String am_pm = m.group(3);
			if (m.group(3) == null) {
				// no am/pm indicator, assume it's within 12 hours of now and take a best guess
				int ref12Hour = reference.get(Calendar.HOUR);
				if (ref12Hour == 12) { ref12Hour = 0; }
				int minsAfterMid = hour * 60 + minute; // after midnight or midday, don't care
				int refMinsAfterMid = ref12Hour * 60 + reference.get(Calendar.MINUTE);
				if (minsAfterMid >= refMinsAfterMid) {
					// it looks like it's shortly after the current time
					return minsAfterMid - refMinsAfterMid;
				}
				else {
					// it looks like it has crossed the midday/midnight boundary
					return HALF_DAY_MINUTES - refMinsAfterMid + minsAfterMid;
				}
			}
			else {
				am_pm = am_pm.toLowerCase();
				if (am_pm.equals("p")) {
					hour += 12; // we fixed the hour values above, so 12 is zero
				}
				int minsAfterMidnight = hour*60 + minute;
				int refMinsAfterMidnight = reference.get(Calendar.HOUR_OF_DAY) * 60 + reference.get(Calendar.MINUTE);
				if (minsAfterMidnight >= refMinsAfterMidnight) {
					return minsAfterMidnight - refMinsAfterMidnight;
				}
				else {
					return DAY_MINUTES - refMinsAfterMidnight + minsAfterMidnight;
				}
			}
		}
		else {
			return -1;
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
			
	public ArrayList<HashMap<String, String>> getPredictions(LTCRoute route, String stopNumber, ScrapeStatus scrapeStatus) {
		ArrayList<HashMap<String, String>> predictions = new ArrayList<HashMap<String, String>>(3); // usually get 3 of them
		try {
			Calendar now = Calendar.getInstance();
			now.set(Calendar.SECOND, 0);
			now.set(Calendar.MILLISECOND, 0); // now we have 'now' set to the current time
			Connection conn = Jsoup.connect(predictionUrl(route, stopNumber));
			conn.timeout(20000);
			Document doc = conn.get();
			Elements timeRows = doc.select("table.CrossingTimes tr");
			if (timeRows.size() == 0) {
				throw new ScrapeException("time table not found");
			}
			//Log.i("GP", String.format("rows=%d", timeRows.size()));
			for (Element timeRow: timeRows) {
				Elements cols = timeRow.select("td");
				if (cols.size() == 0) {
					throw new ScrapeException("missing time columns");
				}
				//Log.i("GP", String.format("cols=%d", cols.size()));
				Element timeLink = cols.get(0).select("a.ada").first();
				if (timeLink == null) {
					throw new ScrapeException("missing time");
				}
				HashMap<String, String> crossingTime = new HashMap<String, String>(3);
				String textTime = timeLink.attr("title");
				crossingTime.put(BusDb.ROUTE_NUMBER, route.getRouteNumber());
				crossingTime.put(BusDb.DIRECTION_NAME, route.directionName);
				if (cols.size() >= 2) {
					long timeDifference = getTimeDiffAsMinutes(now, TIME_PATTERN, textTime);
					crossingTime.put(BusDb.DATE_VALUE, String.format("%08d", timeDifference));
					crossingTime.put(BusDb.CROSSING_TIME, minutesAsText(timeDifference));
					crossingTime.put(BusDb.DESTINATION, String.format("%s %s", route.directionName, cols.get(1).text()));
					predictions.add(crossingTime);
				}
				else if (textTime.matches("^No further.*$")) {
					crossingTime.put(BusDb.DATE_VALUE, VERY_FAR_AWAY);
					crossingTime.put(BusDb.CROSSING_TIME, "None");
					crossingTime.put(BusDb.DESTINATION, "");
					predictions.add(crossingTime);
				}
			}
			scrapeStatus.setStatus(ScrapeStatus.OK, null);
		}
		catch (ScrapeException e) {
			HashMap<String, String> scrapeReport = new HashMap<String, String>(3);
			scrapeReport.put(BusDb.ROUTE_NUMBER, route.number);
			scrapeReport.put(BusDb.DIRECTION_NAME, route.directionName);
			scrapeReport.put(BusDb.DATE_VALUE, VERY_FAR_AWAY);
			scrapeReport.put(BusDb.CROSSING_TIME, "BUG!");
			scrapeReport.put(BusDb.DESTINATION, route.directionName);
			scrapeStatus.setStatus(ScrapeStatus.FAILED, e.getMessage());
			predictions.add(scrapeReport);

		}
		catch (IOException e) {
			HashMap<String, String> failReport = new HashMap<String, String>(3);
			failReport.put(BusDb.ROUTE_NUMBER, route.number);
			failReport.put(BusDb.DIRECTION_NAME, route.directionName);
			failReport.put(BusDb.DATE_VALUE, VERY_FAR_AWAY);
			failReport.put(BusDb.CROSSING_TIME, "Fail");
			failReport.put(BusDb.DESTINATION, route.directionName);
			scrapeStatus.setStatus(ScrapeStatus.FAILED, e.getMessage());
			predictions.add(failReport);
		}
		return predictions;
	}
	
	public ArrayList<LTCRoute> loadRoutes() throws ScrapeException, IOException {
		ArrayList<LTCRoute> routes = new ArrayList<LTCRoute>();
		Connection conn = Jsoup.connect(ROUTE_URL);
		Document doc = conn.get();
		Elements routeLinks = doc.select("a.ada");
		Pattern numFinder = Pattern.compile("r=(\\d{1,2})$");
		for (Element routeLink : routeLinks) {
			Attributes attrs = routeLink.attributes();
			String name = attrs.get("title");
			String href = attrs.get("href");
			Matcher m = numFinder.matcher(href);
			if (m.find()) {
				String number = m.group(1);
				LTCRoute route = new LTCRoute(number, name/*, href*/);
				routes.add(route);
			}
			else {
				throw new ScrapeException("unrecognized route URL format");
			}
		}
		return routes;
	}
	
	ArrayList<LTCDirection> loadDirections(String routeNum) throws ScrapeException, IOException {
		ArrayList<LTCDirection> directions = new ArrayList<LTCDirection>(2); // probably 2
		String url = String.format(DIRECTION_URL, routeNum);
		Connection conn = Jsoup.connect(url);
		Document doc = conn.get();
		Elements dirLinks = doc.select("a.ada");
		Pattern numFinder = Pattern.compile("d=(\\d{1,2})$");
		for (Element dirLink : dirLinks) {
			Attributes attrs = dirLink.attributes();
			String name = attrs.get("title");
			String href = attrs.get("href");
			Matcher m = numFinder.matcher(href);
			if (m.find()) {
				Integer number = Integer.valueOf(m.group(1));
				LTCDirection dir = new LTCDirection(number, name);
				directions.add(dir);
			}
			else {
				throw new ScrapeException("unrecognized route URL format");
			}
		}
		return directions;

	}
	
	HashMap<Integer, LTCStop> loadStops(String routeNum, int direction) throws ScrapeException, IOException {
		HashMap<Integer, LTCStop> stops = new HashMap<Integer, LTCStop>();
		String url = String.format(STOPS_URL, routeNum, direction);
		Connection conn = Jsoup.connect(url);
		Document doc = conn.get();
		Elements stopLinks = doc.select("a.ada");
		Pattern numFinder = Pattern.compile("s=(\\d+)$");
		for (Element stopLink : stopLinks) {
			Attributes attrs = stopLink.attributes();
			String name = attrs.get("title");
			String href = attrs.get("href");
			Matcher m = numFinder.matcher(href);
			if (m.find()) {
				Integer number = Integer.valueOf(m.group(1));
				LTCStop stop = new LTCStop(number, name);
				stops.put(stop.number, stop);
			}
			else {
				throw new ScrapeException("unrecognized route URL format");
			}
		}
		return stops;

	}
	
	/* this just updates existing stops with any locations found from the google map URL */
	void loadStopLocations(String routeNum, HashMap<Integer, LTCStop> stops) throws ScrapeException, IOException {
		String url = String.format(MAP_URL, routeNum);
		Connection conn = Jsoup.connect(url);
		Document doc = conn.get();
		Elements scripts = doc.select("script");
		for (Element script : scripts) {
			String stopData = script.data();
			if (stopData.contains("var initInfoString")) {
				int offset = 0;
				// skip past the crap at the start by finding the 4th asterisk
				for (int i = 0; i < 4; ++i) {
					offset = stopData.indexOf('*', offset) + 1;
				}
				/* get the interesting part, and while we're at it, remove all asterisks
				 * so we don't have to deal with them at the start of latitudes later
				 */
				String actualStopText = stopData.substring(offset).replace("*", "");
				StringSplitter splitter = new SimpleStringSplitter(';');
				splitter.setString(actualStopText);
				for (String stopInfo : splitter) {
					String elems[] = stopInfo.split("\\|");
					if (elems.length == 7) {
						double latitude = Double.valueOf(elems[0]);
						double longitude = Double.valueOf(elems[1]);
						Matcher stopNumMatch = LOCATION_STOP_PATTERN.matcher(elems[4]);
						if (stopNumMatch.find()) {
							int stopNum = Integer.valueOf(stopNumMatch.group(1));
							LTCStop stop = stops.get(stopNum);
							if (stop != null) {
								stop.latitude = latitude;
								stop.longitude = longitude;
							}
						}
					}
				}
				return;
			}
		}
	}
	
    private class LoadTask extends AsyncTask<Void, LoadProgress, Void> {

    	protected Void doInBackground(Void... thing) {
    		ArrayList<LTCRoute> routes; // all routes
    		// all distinct directions (should only end up with four)
    		HashMap<Integer, LTCDirection> allDirections = new HashMap<Integer, LTCDirection>(4);
    		// all distinct stops
    		HashMap<Integer, LTCStop> allStops = new HashMap<Integer, LTCStop>();
    		// all stops that each route stops at in each direction
    		ArrayList<RouteStopLink> links = new ArrayList<RouteStopLink>();
    		publishProgress(new LoadProgress("Downloading routes", 0));
    		try {
    			routes = loadRoutes();
    			if (routes.size() == 0) {
    				publishProgress(new LoadProgress("No routes found", 100));
    			}
    			else {
    				int i;
    				for (i = 0; i < routes.size(); ++i) {
        				publishProgress(new LoadProgress("Loading route " + routes.get(i).name, 5 + 90 * i / routes.size()));
        				ArrayList<LTCDirection> routeDirections = loadDirections(routes.get(i).number);
        				for (LTCDirection dir: routeDirections) {
        					if (!allDirections.containsKey(dir.number)) {
        						allDirections.put(dir.number, dir);
        					}
        					HashMap<Integer, LTCStop> stops = loadStops(routes.get(i).number, dir.number);
        					loadStopLocations(routes.get(i).number, stops);
        					for (int stopNumber: stops.keySet()) {
        						if (!allStops.containsKey(stopNumber)) {
        							allStops.put(stopNumber, stops.get(stopNumber));
        						}
        						links.add(new RouteStopLink(routes.get(i).number, dir.number, stopNumber));
        					}
        				}
    				}
    				publishProgress(new LoadProgress("", 100));
    				db.saveBusData(routes, allDirections.values(), allStops.values(), links);
    			}
    		}
    		catch (IOException e) {
    			publishProgress(new LoadProgress(e.getMessage(), -1));
    		}
    		catch (ScrapeException e) {
    			publishProgress(new LoadProgress(e.getMessage(), -1));
    		}
    		catch (SQLiteException e) {
    			publishProgress(new LoadProgress(e.getMessage(), -1));
    		}

    		return null;
        }

        protected void onProgressUpdate(LoadProgress... progress) {
            status.update(progress[0]);
        }

    }

}
