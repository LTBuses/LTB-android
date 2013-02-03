package org.frasermccrossan.ltc;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils.SimpleStringSplitter;
import android.text.TextUtils.StringSplitter;
import android.text.format.DateFormat;

// everything required to load the LTC_supplied data into the database
@SuppressLint("UseSparseArrays")
public class LTCScraper {

	//BusDb db = null;
	LoadDataTask task = null;
	ScrapingStatus status = null;
	Context context;
	// for development only
	//	static final String ROUTE_URL = "http://teuchter.lan:8000/routes.html";
	//	static final String DIRECTION_URL = "http://teuchter.lan:8000/direction%s.html";
	//	static final String STOPS_URL = "http://teuchter.lan:8000/direction%sd%d.html";
	//	static final String MAP_URL = "http://teuchter.lan:8000/map%s.html";
	// for production
	public static final String ROUTE_URL = "http://www.ltconline.ca/WebWatch/MobileAda.aspx";
	static final String DIRECTION_URL = "http://www.ltconline.ca/WebWatch/MobileAda.aspx?r=%s";
	static final String STOPS_URL = "http://www.ltconline.ca/WebWatch/MobileAda.aspx?r=%s&d=%d";
	static final String LOCATIONS_URL = "http://www.ltconline.ca/WebWatch/UpdateWebMap.aspx?u=%s";
	static final String PREDICTIONS_URL = "http://teuchter.lan:8000/foo.html";
	static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}) ?([AP])?");
	// matches arrival text in the MobileAda.aspx prediction
	static final Pattern ARRIVAL_PATTERN = Pattern.compile("(?i) *(\\d{1,2}:\\d{2} *[\\.apm]*) +(to .+)");
	// pattern for route number in a[href]
	static final Pattern ROUTE_NUM_PATTERN = Pattern.compile("\\?r=(\\d{1,2})");
	// pattern for direction number in a[href]
	static final Pattern DIRECTION_NUM_PATTERN = Pattern.compile("\\&d=(\\d+)");
	// pattern for stop number in a[href]
	static final Pattern STOP_NUM_PATTERN = Pattern.compile("\\&s=(\\d+)");
	// if no buses are found
	static final Pattern NO_INFO_PATTERN = Pattern.compile("(?mi)no stop information");
	static final Pattern NO_BUS_PATTERN = Pattern.compile("(?mi)no further buses");
	static final Pattern DESTINATION_PATTERN = Pattern.compile("(?i) *to +((\\d+[a-z]?) +)?(.*)");
	static final Pattern LOCATION_STOP_PATTERN = Pattern.compile("(\\d+)");
	static final String VERY_CLOSE = "000000000000000"; // something guaranteed to sort before everything
	static final String VERY_FAR_AWAY = "999999999999999"; // something guaranteed to sort after everything
	static final int FETCH_TIMEOUT = 30 * 1000;
	static final int FAILURE_LIMIT = 20;

	LTCScraper(Context c, ScrapingStatus s) {
		context = c;
		status = s;
	}

	LTCScraper(Context c) {
		/* instantiate this way if you plan only to check bus predictions
		 */
		context = c;
	}

	public void close() {
		//		if (db != null) {
		//			db.close();
		//		}
		if (task != null) {
			task.cancel(true);
		}
	}
	
	String userAgent() {
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return String.format("%s %s", info.packageName, info.versionName);
		}
		catch (PackageManager.NameNotFoundException e) {
			return "Unknown";
		}
	}

	@SuppressLint("NewApi")
	public void loadAll() {
		task = new LoadDataTask();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			/* on Honeycomb and later, ASyncTasks run on a serial executor, and since
			 * we might have another asynctask running in an activity (e.g. fetching stop lists),
			 * we don't really want them all to block
			 */
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		else {
			task.execute();
		}
	}

	public void cancelLoadAll() {
		if (task != null) {
			task.cancel(true);
		}
	}

	public String predictionUrl(LTCRoute route, String stopNumber) {
		//		return PREDICTIONS_URL;
		return String.format("http://www.ltconline.ca/WebWatch/MobileAda.aspx?r=%s&d=%s&s=%s",
				route.number, route.direction, stopNumber);
	}

	/* given a time in text scraped from the website, get the best guess of the time it
	 * represents; we do this all manually rather than use Calendar because the AM/PM
	 * behaviour of Calendar is broken
	 */
	int getTimeDiffAsMinutes(Calendar reference, Pattern timePattern, String textTime) {
		HourMinute time = new HourMinute(textTime);
		return time.timeDiff(reference);
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
		Resources res = context.getResources();
		try {
			Calendar now = Calendar.getInstance();
			now.set(Calendar.SECOND, 0);
			now.set(Calendar.MILLISECOND, 0); // now we have 'now' set to the current time
			Connection conn = Jsoup.connect(predictionUrl(route, stopNumber));
			//conn.header("Connection", "close");
			conn.timeout(FETCH_TIMEOUT);
			Document doc = conn.get();
			Elements divs = doc.select("div");
			if (divs.size() == 0) {
				throw new ScrapeException("LTC down?", ScrapeStatus.PROBLEM_IMMEDIATELY);
			}
			//Log.i("GP", String.format("rows=%d", timeRows.size()));
			for (Element div: divs) {
				//Log.i("GP", String.format("cols=%d", cols.size()));
				List<TextNode> textNodes = div.textNodes();
				for (TextNode node: textNodes) {
					String text = node.text();
					Matcher noBusMatcher = NO_BUS_PATTERN.matcher(text);
					if (noBusMatcher.find()) {
						throw new ScrapeException(res.getString(R.string.no_further), ScrapeStatus.PROBLEM_IF_ALL);
					}
					Matcher noStopMatcher = NO_INFO_PATTERN.matcher(text);
					if (noStopMatcher.find()) {
						throw new ScrapeException(res.getString(R.string.no_service), ScrapeStatus.PROBLEM_IF_ALL);
					}
					Matcher arrivalMatcher = ARRIVAL_PATTERN.matcher(text);
					HashMap<String, String> crossingTime;
					while (arrivalMatcher.find()) {
						String textTime = arrivalMatcher.group(1);
						String destination = arrivalMatcher.group(2);
						int timeDifference = getTimeDiffAsMinutes(now, TIME_PATTERN, textTime);
						Calendar absTime = (Calendar)now.clone();
						absTime.add(Calendar.MINUTE, timeDifference);
						java.text.DateFormat absFormatter = DateFormat.getTimeFormat(context);
						absFormatter.setCalendar(absTime);
						crossingTime = predictionEntry(route, 
								String.format("%08d", timeDifference),
								minutesAsText(timeDifference),
								absFormatter.format(absTime.getTime()),
								destination
								/*String.format("%s %s",
										route.directionName,
										destination)*/);
						predictions.add(crossingTime);
					}
				}
			}
			if (predictions.size() == 0) {
				throw new ScrapeException(res.getString(R.string.no_bus), ScrapeStatus.PROBLEM_IF_ALL);
			}
			scrapeStatus.setStatus(ScrapeStatus.OK, ScrapeStatus.NOT_PROBLEM, null);
		}
		catch (ScrapeException e) {
			HashMap<String, String> scrapeReport = predictionEntry(route,
					VERY_FAR_AWAY,
					null,
					null,
					e.getMessage());
			scrapeStatus.setStatus(ScrapeStatus.FAILED, e.problemType, e.getMessage());
			predictions.add(scrapeReport);

		}
		catch (SocketTimeoutException e) {
			HashMap<String, String> failReport = predictionEntry(context,
					route,
					VERY_FAR_AWAY,
					R.string.times_timeout,
					null
					);
			scrapeStatus.setStatus(ScrapeStatus.FAILED, ScrapeStatus.PROBLEM_IMMEDIATELY, e.getMessage());
			predictions.add(failReport);
		}
		catch (IOException e) {
			HashMap<String, String> failReport = predictionEntry(context,
					route,
					VERY_FAR_AWAY,
					R.string.failed,
					null
					);
			scrapeStatus.setStatus(ScrapeStatus.FAILED, ScrapeStatus.PROBLEM_IMMEDIATELY, e.getMessage());
			predictions.add(failReport);
		}
		return predictions;
	}

	static HashMap<String, String> predictionEntry(LTCRoute route,
			String dateValue,
			String crossingTime,
			String rawCrossingTime, // the actual text from the website
			String destination)
			{
		HashMap<String, String> p = new HashMap<String, String>(5);
		p.put(BusDb.ROUTE_INTERNAL_NUMBER, route.number); // useful to look up route later
		Matcher destMatcher = DESTINATION_PATTERN.matcher(destination);
		p.put(BusDb.ROUTE_NUMBER, route.getRouteNumber());		
		if (destination == null) {
			// just use the direction for the destination for sugar entries
			p.put(BusDb.DESTINATION, route.directionName);
		}
		else if (destMatcher.find()) {
			// a heuristic to convert "2 TO 2A Bla bla Street" into "2A Bla Bla Street"
			p.put(BusDb.DESTINATION, destMatcher.group(3));
			if (destMatcher.group(2) != null) {
				p.put(BusDb.ROUTE_NUMBER, destMatcher.group(2));
			}
		}
		else {
			// well, worth a try, just use whatever they gave us
			p.put(BusDb.DESTINATION, destination == null ? route.directionName : destination);
		}
		p.put(BusDb.DIRECTION_NAME, route.directionName);
		p.put(BusDb.ROUTE_NAME, route.name);
		p.put(BusDb.SHORT_DIRECTION_NAME, route.getOneLetterDirection());
		p.put(BusDb.DIRECTION_IMG_RES, route.getDirectionDrawableRes());
		p.put(BusDb.DATE_VALUE, dateValue);
		p.put(BusDb.CROSSING_TIME, crossingTime);
		p.put(BusDb.RAW_TIME, rawCrossingTime);
		return p;
			}

	static HashMap<String, String> predictionEntry(Context c, LTCRoute route,
			String dateValue,
			int errorMsgRes, // look up this string resource to get displayed dateValue
			String destination)
			{
		Resources res = c.getResources();
		return predictionEntry(route, dateValue, null, null, res.getString(errorMsgRes));
			}

	public ArrayList<LTCRoute> loadRoutes() throws ScrapeException, IOException {
		ArrayList<LTCRoute> routes = new ArrayList<LTCRoute>();
		Connection conn = Jsoup.connect(ROUTE_URL);
		conn.timeout(FETCH_TIMEOUT);
		Document doc = conn.get();
		Elements routeLinks = doc.select("a[href]");
		for (Element routeLink : routeLinks) {
			String name = routeLink.text();
			Attributes attrs = routeLink.attributes();
			String href = attrs.get("href");
			Matcher m = ROUTE_NUM_PATTERN.matcher(href);
			if (m.find()) {
				String number = m.group(1);
				LTCRoute route = new LTCRoute(number, name/*, href*/);
				routes.add(route);
			}
		}
		return routes;
	}

	ArrayList<LTCDirection> loadDirections(String routeNum) throws ScrapeException, IOException {
		ArrayList<LTCDirection> directions = new ArrayList<LTCDirection>(2); // probably 2
		String url = String.format(DIRECTION_URL, routeNum);
		Connection conn = Jsoup.connect(url);
		conn.timeout(FETCH_TIMEOUT);
		Document doc = conn.get();
		Elements dirLinks = doc.select("a[href]");
		for (Element dirLink : dirLinks) {
			String name = dirLink.text();
			Attributes attrs = dirLink.attributes();
			String href = attrs.get("href");
			Matcher m = DIRECTION_NUM_PATTERN.matcher(href);
			if (m.find()) {
				Integer number = Integer.valueOf(m.group(1));
				LTCDirection dir = new LTCDirection(number, name);
				directions.add(dir);
			}
		}
		return directions;

	}

	HashMap<Integer, LTCStop> loadStops(String routeNum, int direction) throws ScrapeException, IOException {
		HashMap<Integer, LTCStop> stops = new HashMap<Integer, LTCStop>();
		String url = String.format(STOPS_URL, routeNum, direction);
		Connection conn = Jsoup.connect(url);
		conn.timeout(FETCH_TIMEOUT);
		Document doc = conn.get();
		Elements stopLinks = doc.select("a[href]");
		for (Element stopLink : stopLinks) {
			String name = stopLink.text();
			Attributes attrs = stopLink.attributes();
			String href = attrs.get("href");
			Matcher m = STOP_NUM_PATTERN.matcher(href);
			if (m.find()) {
				Integer number = Integer.valueOf(m.group(1));
				LTCStop stop = new LTCStop(number, name);
				stops.put(stop.number, stop);
			}
		}
		return stops;

	}

	/* this just updates existing stops with any locations found from the google map URL */
	void loadStopLocations(String routeNum, HashMap<Integer, LTCStop> stops) throws ScrapeException, IOException {
		String url = String.format(LOCATIONS_URL, routeNum);
		AndroidHttpClient client = AndroidHttpClient.newInstance(userAgent());
		HttpGet request = new HttpGet();
		try {
			request.setURI(new URI(url));
		}
		catch (URISyntaxException e) {
			throw new ScrapeException("internal error: bad URL");
		}
        int offset = 0;
        String stopData = client.execute(request, new BasicResponseHandler());
        client.close();
        // skip past the crap at the start by finding the 1st asterisk
        for (int i = 0; i < 1; ++i) {
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

	private class LoadDataTask extends AsyncTask<Void, LoadProgress, Void> {

		protected Void doInBackground(Void... voids) {
			ArrayList<LTCRoute> routesToDo;
			ArrayList<LTCRoute> routesDone;
			// all distinct directions (should only end up with four)
			HashMap<Integer, LTCDirection> allDirections = new HashMap<Integer, LTCDirection>(4);
			// all distinct stops
			HashMap<Integer, LTCStop> allStops = new HashMap<Integer, LTCStop>();
			// all stops that each route stops at in each direction
			ArrayList<RouteStopLink> links = new ArrayList<RouteStopLink>();
			//Resources res = getApplicationContext().getResources();
			Resources res = context.getResources();
			LoadProgress progress = new LoadProgress();
			publishProgress(progress.title(res.getString(R.string.downloading_routes)));
			try {
				routesToDo = loadRoutes();
				if (routesToDo.size() == 0) {
					publishProgress(progress.title(res.getString(R.string.download_failed))
							.message(res.getString(R.string.no_routes_found))
							.failed());
				}
				else {
					int totalToDo = routesToDo.size();
					routesDone = new ArrayList<LTCRoute>(totalToDo);
					int failures = 0;
					while (routesToDo.size() > 0) {
						int i = 0;
						while (i < routesToDo.size()) {
							try {
								publishProgress(progress.message(String.format(res.getString(R.string.loading_route_nodir), routesToDo.get(i).name))
										.percent(5 + 90 * routesDone.size() / totalToDo));
								ArrayList<LTCDirection> routeDirections = loadDirections(routesToDo.get(i).number);
								//        				Log.d("loadtask", String.format("route %s has %d directions", routes.get(i).number, routeDirections.size()));
								for (LTCDirection dir: routeDirections) {
									if (!allDirections.containsKey(dir.number)) {
										allDirections.put(dir.number, dir);
									}
//									publishProgress(progress.message(String.format(res.getString(R.string.loading_route_dir), routesToDo.get(i).name, dir.name))
//											.percent(pct));
									HashMap<Integer, LTCStop> stops = loadStops(routesToDo.get(i).number, dir.number);
									publishProgress(progress.message(String.format(res.getString(R.string.loading_route_stop_locations), routesToDo.get(i).name))
											.percent(6 + 90 * routesDone.size() / totalToDo));
									loadStopLocations(routesToDo.get(i).number, stops);
									for (int stopNumber: stops.keySet()) {
										if (!allStops.containsKey(stopNumber)) {
											allStops.put(stopNumber, stops.get(stopNumber));
										}
										links.add(new RouteStopLink(routesToDo.get(i).number, dir.number, stopNumber));
									}
								}
								routesDone.add(routesToDo.get(i));
								routesToDo.remove(i); // don't increment i, just remove the one we just did
							}
							catch (IOException e) {
								failures++; // note that one failed
								if (failures > FAILURE_LIMIT) {
									throw(new ScrapeException(res.getString(R.string.too_many_failures), ScrapeStatus.PROBLEM_IMMEDIATELY));
								}
								i++; // go to the next one
							}
						}
					}
					publishProgress(progress.message(res.getString(R.string.saving_database))
							.percent(95));
					BusDb db = new BusDb(context);
					db.saveBusData(routesDone, allDirections.values(), allStops.values(), links, false);
					db.close();
					publishProgress(progress.message(res.getString(R.string.saving_database))
							.percent(100).enough(true));
//					if (fetchLocations[0]) {
//						// reset our trackers and prepare to download locations
//						routesToDo.addAll(routesDone);
//						routesDone.clear();
//						int tries = 0;
//						publishProgress(progress.reset().enough(true)
//								.title(res.getString(R.string.downloading_locations)));
//						while (routesToDo.size() > 0) {
//							int i = 0;
//							while (i < routesToDo.size()) {
//								try {
//									int pct = 100* routesDone.size() / totalToDo;
//									publishProgress(progress.message(routesToDo.get(i).name)
//											.percent(pct));
//									db = new BusDb(context);
//									// check if any stops on this route lack location information
//									if (db.getLocationlessStopCount(routesToDo.get(i)) > 0) {
//										// get existing stops from the database
//										HashMap<Integer, LTCStop> stops = db.findStopRoutesAnyDir(routesToDo.get(i).number);
//										db.close();
//										// update those with actual stop locations from the website
//										loadStopLocations(routesToDo.get(i).number, stops);
//										// convert stops to a plain collection and save it
//										db = new BusDb(context);
//										db.saveBusData(null, null, stops.values(), null, true);
//									}
//									db.close();
//									routesDone.add(routesToDo.get(i));
//									routesToDo.remove(i); // don't increment i, just remove the one we just did
//								}
//								catch (IOException e) {
//									failures++; // note that one failed
//									if (failures > FAILURE_LIMIT) {
//										throw(new ScrapeException(res.getString(R.string.too_many_failures), ScrapeStatus.PROBLEM_IMMEDIATELY));
//									}
//									i++; // go to the next one
//								}
//								tries++;
//							}
//						}
//					}
//					publishProgress(progress.title(res.getString(R.string.stop_download_complete))
//							.message(res.getString(R.string.database_ready))
//							.complete());
				}
			}
			catch (IOException e) {
				publishProgress(progress.title(res.getString(R.string.unable_to_load_routes))
						.message(e.getMessage())
						.failed());
			}
			catch (ScrapeException e) {
				publishProgress(progress.title(e.getMessage())
						.message("")
						.failed());
			}
			catch (SQLiteException e) {
				publishProgress(progress.title(e.getMessage())
						.message("")
						.failed());
			}

			return null;
		}

		protected void onProgressUpdate(LoadProgress... progress) {
			if (!isCancelled() && status != null) {
				status.update(progress[0]);
			}
		}

	}

}
