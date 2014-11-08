package org.frasermccrossan.ltc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils.SimpleStringSplitter;
import android.text.TextUtils.StringSplitter;

// everything required to load the LTC_supplied data into the database
@SuppressLint("UseSparseArrays")
public class LTCScraper {

	LoadDataTask task = null;
	ScrapingStatus status = null;
	Context context;

    static final String PROXY_BASE = "";
    static final Boolean PROXY_ENABLED = false;
    static final String LTC_BASE = "http://www.ltconline.ca";
    static final String ROUTE_PATH = "/WebWatch/MobileAda.aspx";
    //static final String ROUTE_PATH = "/";
    public static final String ROUTE_URL = LTC_BASE + ROUTE_PATH; // used when calling the diagnostic screen
	static final String DIRECTION_PATH = "/WebWatch/MobileAda.aspx?r=%s";
	static final String STOPS_PATH = "/WebWatch/MobileAda.aspx?r=%s&d=%d";
	static final String LOCATIONS_PATH = "/WebWatch/UpdateWebMap.aspx?u=%s";
    static final String PROXY_PREDICTION_PATH = "/WebWatch/ProxyMobileAda.aspx?r=%s&d=%s&s=%s";
    static final String LTC_PREDICTION_PATH = "/WebWatch/MobileAda.aspx?r=%s&d=%s&s=%s";
	static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}) ?([AP])?");
	// matches arrival text in the MobileAda.aspx prediction
	static final Pattern ARRIVAL_PATTERN = Pattern.compile("(?i) *(\\d{1,2}:\\d{2} *[\\.apm]*) +(to .*)");
	// pattern for route number in a[href]
	static final Pattern ROUTE_NUM_PATTERN = Pattern.compile("\\?r=(\\d{1,2})");
	// pattern for direction number in a[href]
	static final Pattern DIRECTION_NUM_PATTERN = Pattern.compile("\\&d=(\\d+)");
	// pattern for stop number in a[href]
	static final Pattern STOP_NUM_PATTERN = Pattern.compile("\\&s=(\\d+)");
	// if no buses are found
	static final Pattern NO_INFO_PATTERN = Pattern.compile("(?mi)no stop information");
	static final Pattern NO_BUS_PATTERN = Pattern.compile("(?mi)no further buses");
	static final Pattern LOCATION_STOP_PATTERN = Pattern.compile("(\\d+)");
	static final String VERY_CLOSE = "000000000000000"; // something guaranteed to sort before everything
	static final String VERY_FAR_AWAY = "999999999999999"; // something guaranteed to sort after everything
	/* parseDocFromUri() starts with the initial timeout then retries doubling the timeout each time until
	 * greater than the maximum timeout, thus we get (for example) 1, 2, 4, 8, 16
	 */
	static final int INITIAL_FETCH_TIMEOUT = 2000;
	static final int MAXIMUM_FETCH_TIMEOUT = 128*1000;
	static final int FAILURE_LIMIT = 40;

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

	private Document parseDocFromUri(String proxyPath, String ltcPath) throws IOException, MalformedURLException {
		return parseDocFromUri(proxyPath, ltcPath, INITIAL_FETCH_TIMEOUT);
	}

    /*
       * the first path is the one to try with the proxy, the second is for the real
       * site; these are only different for fetching predictions, and only because
       * we want to change the caching time and need a different path in nginx */
      private Document parseDocFromUri(String proxyPath, String ltcPath, int initial_timeout) throws IOException, MalformedURLException {
		
		Document doc;
		URL url;
        boolean try_proxy = PROXY_ENABLED;
		
		int timeout = initial_timeout;
		while (true) {
			try {
                if (try_proxy) {
                    url = new URL(PROXY_BASE + proxyPath);
                }
                else {
                    url = new URL(LTC_BASE + ltcPath);
                }
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setConnectTimeout(timeout);
				connection.setReadTimeout(timeout);
				doc = null;
				try {
					InputStream in = new BufferedInputStream(connection.getInputStream(), 8192);
					doc = Jsoup.parse(in, null, proxyPath);
				}
				finally {
					connection.disconnect();
				}
				break;
			}
			catch (SocketTimeoutException e) {
				timeout *= 8;
				if (timeout <= MAXIMUM_FETCH_TIMEOUT) {
					continue;
				}
				else {
                    if (try_proxy) {
                        // reached the maximum timeout, try switching from proxy to real site
                        try_proxy=false;
                        timeout = initial_timeout;
                        continue;
                    }
					throw e;
				}
			}
            catch (SocketException e) {
                /*
                 *  if we get a connection refused while trying the proxy then the proxy may be down,
                 *  so switch back to the LTC site
                  */
                if (try_proxy) {
                    try_proxy = false;
                    timeout = initial_timeout;
                    continue;
                }
                else {
                    throw e;
                }
            }
		}
		return doc;
	}

    public String ltcPredictionPath(LTCRoute route, String stopNumber) {
        //		return PREDICTIONS_URL;
        return String.format(LTC_PREDICTION_PATH, route.number, route.direction, stopNumber);
    }

    public String proxyPredictionPath(LTCRoute route, String stopNumber) {
        //		return PREDICTIONS_URL;
        return String.format(PROXY_PREDICTION_PATH, route.number, route.direction, stopNumber);
    }

	public ArrayList<Prediction> getPredictions(LTCRoute route, String stopNumber, ScrapeStatus scrapeStatus) {
		ArrayList<Prediction> predictions = new ArrayList<Prediction>(3); // usually get 3 of them
		Resources res = context.getResources();
		try {
			Calendar now = Calendar.getInstance();
			now.set(Calendar.SECOND, 0);
			now.set(Calendar.MILLISECOND, 0); // now we have 'now' set to the current time
			Document doc = parseDocFromUri(proxyPredictionPath(route,stopNumber),
                    ltcPredictionPath(route, stopNumber),
                    INITIAL_FETCH_TIMEOUT);
			Elements divs = doc.select("div");
			if (divs.size() == 0) {
				throw new ScrapeException("LTC down?", ScrapeStatus.PROBLEM_IMMEDIATELY, true);
			}
			//Log.i("GP", String.format("rows=%d", timeRows.size()));
			for (Element div: divs) {
				//Log.i("GP", String.format("cols=%d", cols.size()));
				List<TextNode> textNodes = div.textNodes();
				for (TextNode node: textNodes) {
					String text = node.text();
					Matcher noBusMatcher = NO_BUS_PATTERN.matcher(text);
					if (noBusMatcher.find()) {
						throw new ScrapeException(res.getString(R.string.no_further), ScrapeStatus.PROBLEM_IF_ALL, false);
					}
					Matcher noStopMatcher = NO_INFO_PATTERN.matcher(text);
					if (noStopMatcher.find()) {
						throw new ScrapeException(res.getString(R.string.no_service), ScrapeStatus.PROBLEM_IF_ALL, false);
					}
					Matcher arrivalMatcher = ARRIVAL_PATTERN.matcher(text);
					while (arrivalMatcher.find()) {
						String textTime = arrivalMatcher.group(1);
						String destination = arrivalMatcher.group(2);
						predictions.add(new Prediction(route, textTime, destination, now));
					}
				}
			}
			if (predictions.size() == 0) {
				throw new ScrapeException(res.getString(R.string.no_bus), ScrapeStatus.PROBLEM_IF_ALL, true);
			}
			scrapeStatus.setStatus(ScrapeStatus.OK, ScrapeStatus.NOT_PROBLEM, null);
		}
		catch (ScrapeException e) {
			scrapeStatus.setStatus(ScrapeStatus.FAILED, e.problemType, e.getMessage());
			predictions.add(new Prediction(route, e.getMessage(), e.seriousProblem));

		}
		catch (SocketTimeoutException e) {
			scrapeStatus.setStatus(ScrapeStatus.FAILED, ScrapeStatus.PROBLEM_IMMEDIATELY, e.getMessage());
			predictions.add(new Prediction(context, route, R.string.times_timeout, true));
		}
		catch (IOException e) {
			scrapeStatus.setStatus(ScrapeStatus.FAILED, ScrapeStatus.PROBLEM_IMMEDIATELY, e.getMessage());
			predictions.add(new Prediction(context, route, R.string.times_fail, true));
		}
		return predictions;
	}

	public ArrayList<LTCRoute> loadRoutes() throws ScrapeException, IOException {
		ArrayList<LTCRoute> routes = new ArrayList<LTCRoute>();
		Document doc = parseDocFromUri(ROUTE_PATH, ROUTE_PATH);
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
        String path = String.format(DIRECTION_PATH, routeNum);
		Document doc = parseDocFromUri(path, path);
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
        String path = String.format(STOPS_PATH, routeNum, direction);
		Document doc = parseDocFromUri(path, path);
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
        StringBuilder builder = new StringBuilder(8192);
        String line;
        URL url;
        HttpURLConnection connection;
        boolean try_proxy = PROXY_ENABLED;
        while (true) {
            try {
                if (try_proxy) {
                    url = new URL(String.format(PROXY_BASE + LOCATIONS_PATH, routeNum));
                    connection = (HttpURLConnection) url.openConnection();
                } else {
                    url = new URL(String.format(LTC_BASE + LOCATIONS_PATH, routeNum));
                    connection = (HttpURLConnection) url.openConnection();
                }
                connection.setConnectTimeout(MAXIMUM_FETCH_TIMEOUT);
                connection.setReadTimeout(MAXIMUM_FETCH_TIMEOUT);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                int offset = 0;
                String stopData = builder.toString();
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
                connection.disconnect();
                return;
            } catch (SocketException e) {
                if (try_proxy) {
                    try_proxy = false;
                    continue;
                }
                // if this is already not the proxy, give up
                throw e;
            }
        }
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
			BusDb db;
			try {
				publishProgress(progress.title(res.getString(R.string.loading_stop_cache)).percent(0));
				db = new BusDb(context);
				db.ensureStopPreload();
				db.close();
				publishProgress(progress.title(res.getString(R.string.downloading_routes)).percent(3));
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
					MAINLOOP: while (routesToDo.size() > 0) {
						int i = 0;
						while (i < routesToDo.size()) {
							try {
								publishProgress(progress.message(String.format(res.getString(R.string.loading_route_nodir), routesToDo.get(i).name))
										.percent(5 + 90 * routesDone.size() / totalToDo));
								ArrayList<LTCDirection> routeDirections = loadDirections(routesToDo.get(i).number);
								//        				Log.d("loadtask", String.format("route %s has %d directions", routes.get(i).number, routeDirections.size()));
								int routeStopCount = 0;
								for (LTCDirection dir: routeDirections) {
									if (!allDirections.containsKey(dir.number)) {
										allDirections.put(dir.number, dir);
									}
//									publishProgress(progress.message(String.format(res.getString(R.string.loading_route_dir), routesToDo.get(i).name, dir.name))
//											.percent(pct));
									if (isCancelled()) {
										break MAINLOOP;
									}
									HashMap<Integer, LTCStop> dirStops = loadStops(routesToDo.get(i).number, dir.number);
									routeStopCount += dirStops.size();
									if (isCancelled()){
										break MAINLOOP;
									}
									for (int stopNumber: dirStops.keySet()) {
										if (!allStops.containsKey(stopNumber)) {
											allStops.put(stopNumber, dirStops.get(stopNumber));
										}
										links.add(new RouteStopLink(routesToDo.get(i).number, dir.number, stopNumber));
									}
								}
								db = new BusDb(context);
								boolean doLoadStop = db.getStopCount(routesToDo.get(i)) < routeStopCount ||
										db.getLocationlessStopCount(routesToDo.get(i)) > 0;
								db.close();
								if (doLoadStop) {
									publishProgress(progress.message(String.format(res.getString(R.string.loading_route_stop_locations), routesToDo.get(i).name))
											.percent(6 + 90 * routesDone.size() / totalToDo));
									loadStopLocations(routesToDo.get(i).number, allStops);
								}
								routesDone.add(routesToDo.get(i));
								routesToDo.remove(i); // don't increment i, just remove the one we just did
							}
							catch (IOException e) {
								failures++; // note that one failed
                                //Toast.makeText(context, "IOException: " + e.getMessage(), Toast.LENGTH_SHORT).show();
								if (failures > FAILURE_LIMIT) {
									throw(new ScrapeException(res.getString(R.string.too_many_failures), ScrapeStatus.PROBLEM_IMMEDIATELY, true));
								}
								i++; // go to the next one
							}
						}
					}
					publishProgress(progress.message(res.getString(R.string.saving_database))
							.percent(95));
					if (!isCancelled()) {
						db = new BusDb(context);
						db.saveBusData(routesDone, allDirections.values(), allStops.values(), links, false);
						db.close();
						publishProgress(progress.title(res.getString(R.string.stop_download_complete))
								.message(res.getString(R.string.database_ready))
								.percent(100).complete());
					}
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
