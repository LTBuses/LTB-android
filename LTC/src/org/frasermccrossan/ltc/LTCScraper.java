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

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;

// everything required to load the LTC_supplied data into the database
public class LTCScraper {

	BusDb db = null;
	ScrapingStatus status = null;
	Context c;
	static final String ROUTE_URL = "http://teuchter.lan:8000/routes.html";
	static final String DIRECTION_URL = "http://teuchter.lan:8000/direction%s.html";
	static final String STOPS_URL = "http://teuchter.lan:8000/direction%sd%d.html";
	static final String PREDICTIONS_URL = "http://teuchter.lan:8000/dundas-westbound-sample.html";
	static Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}) ([AP])");
	//static Pattern TIME_PATTERN = Pattern.compile("");
	
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
	
	String getTimeAsMilliseconds(Calendar reference, Pattern timePattern, String textTime) {
		Calendar cal = (Calendar)reference.clone();
		Matcher m = timePattern.matcher(textTime);
		if (m.find()) {
			cal.set(Calendar.HOUR, Integer.valueOf(m.group(1)));
			cal.set(Calendar.MINUTE, Integer.valueOf(m.group(2)));
			cal.set(Calendar.AM_PM, m.group(3) == "A" ? Calendar.AM : Calendar.PM);
			if (cal.before(reference)) {
				// make sure expiry time is in the future
				cal.add(Calendar.HOUR_OF_DAY, 24);
			}
		}
		return String.format("%015d", cal.getTimeInMillis());
	}
		
	public ArrayList<HashMap<String, String>> getPredictions(LTCRoute route, String stopNumber) throws ScrapeException {
		ArrayList<HashMap<String, String>> predictions = new ArrayList<HashMap<String, String>>(3); // usually get 3 of them
		try {
			Calendar now = Calendar.getInstance();
			now.set(Calendar.SECOND, 0);
			now.set(Calendar.MILLISECOND, 0); // now we have 'now' set to the current time
			Connection conn = Jsoup.connect(predictionUrl(route, stopNumber));
			Document doc = conn.get();
			Elements timeRows = doc.select("table.CrossingTimes tr");
			//Log.i("GP", String.format("rows=%d", timeRows.size()));
			for (Element timeRow: timeRows) {
				Elements cols = timeRow.select("td");
				//Log.i("GP", String.format("cols=%d", cols.size()));
				if (cols.size() >= 2) {
					Element timeLink = cols.get(0).select("a.ada").first();
					HashMap<String, String> crossingTime = new HashMap<String, String>(3);
					crossingTime.put(BusDb.ROUTE_NAME, route.name);
					String textTime = timeLink.attr("title");
					crossingTime.put(BusDb.CROSSING_TIME, textTime);
					crossingTime.put(BusDb.DATE_VALUE, getTimeAsMilliseconds(now, TIME_PATTERN, textTime));
					crossingTime.put(BusDb.DESTINATION, cols.get(1).text());
					predictions.add(crossingTime);
				}
			}
		}
		catch (IOException e) {
			// we'll think of something
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
	
	ArrayList<LTCStop> loadStops(String routeNum, int direction) throws ScrapeException, IOException {
		ArrayList<LTCStop> stops = new ArrayList<LTCStop>();
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
				stops.add(stop);
			}
			else {
				throw new ScrapeException("unrecognized route URL format");
			}
		}
		return stops;

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
        					ArrayList<LTCStop> stops = loadStops(routes.get(i).number, dir.number);
        					for (LTCStop stop: stops) {
        						if (!allStops.containsKey(stop.number)) {
        							allStops.put(stop.number, stop);
        						}
        						links.add(new RouteStopLink(routes.get(i).number, dir.number, stop.number));
        					}
        				}
    				}
    				publishProgress(new LoadProgress(String.valueOf(routes.size()) + " routes "
    						+ String.valueOf(allDirections.size()) + " directions "
    						+ String.valueOf(allStops.size()) + " stops "
    						+ String.valueOf(links.size() + " links"), 100));
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
