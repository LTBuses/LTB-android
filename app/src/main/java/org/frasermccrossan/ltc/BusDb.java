package org.frasermccrossan.ltc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.text.TextUtils;

/* although called BusDb, this is actually a helper class to abstract all the
 * database stuff into method calls
 */

public class BusDb {

	static final String ROUTE_TABLE = "routes";
	static final String ROUTE_NUMBER = "route_number";
	static final String ROUTE_NAME = "route_name";
	
	static final String STOP_TABLE = "stops";
	static final String STOP_NUMBER = "stop_number";
	static final String STOP_NAME = "stop_name";
	static final String LATITUDE = "latitude";
	static final String LONGITUDE = "longitude";
	
	// latitude must be this big for latitude data to be valid
	static final double MIN_LATITUDE = 0.1;

	static final String DIRECTION_TABLE = "directions";
	static final String DIRECTION_NUMBER = "direction_number";
	static final String DIRECTION_NAME = "direction_name";

	static final String STOP_LAST_USE_TABLE = "stop_uses";
	static final String STOP_LAST_USE_TIME = "stop_last_use_time";
	static final String STOPS_WITH_USES = "stops_with_uses";
	static final String STOP_USES_COUNT = "stop_uses_count"; // for the count view
	static final int STOP_HISTORY_LENGTH = 200;
	

	static final String FRESHNESS = "freshness";
	
	static final String FRESHNESS_TABLE = "last_updates";
	static final int WEEKDAY_LOCATION_FRESHNESS = 3;
	static final int SATURDAY_LOCATION_FRESHNESS = 4;
	static final int SUNDAY_LOCATION_FRESHNESS = 5;
	static final String WEEKDAY_FRESHNESS_COLUMN = "weekday_freshness";
	static final String SATURDAY_FRESHNESS_COLUMN = "saturday_freshness";
	static final String SUNDAY_FRESHNESS_COLUMN = "sunday_freshness";
	static final String WEEKDAY_LOCATION_FRESHNESS_COLUMN = "weekday_location_freshness";
	static final String SATURDAY_LOCATION_FRESHNESS_COLUMN = "saturday_location_freshness";
	static final String SUNDAY_LOCATION_FRESHNESS_COLUMN = "sunday_location_freshness";
	
	static final int UPDATE_NOT_REQUIRED = 0;
	static final int UPDATE_RECOMMENDED = 1;
	static final int UPDATE_REQUIRED = 2;
	static final long UPDATE_DATABASE_AGE_LIMIT_SOFT = 1000L * 60L * 60L * 24L * 15L; // 15 days
	static final long UPDATE_DATABASE_AGE_LIMIT_HARD = 1000L * 60L * 60L * 24L * 30L; // 30 days

	static final String LINK_TABLE = "route_stops";

	static final String DISTANCE_TEXT = "distance";
	static final String DISTANCE_ORDER = "distance_order";
	static final String ROUTE_LIST = "route_list";

	static final private ReentrantLock blocker = new ReentrantLock();
	
	SQLiteDatabase db;
	Context context;
	
	BusDb(Context c) {
		context = c;
		blocker.lock();
		BusDbOpenHelper helper = new BusDbOpenHelper(context);
		db = helper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
		blocker.unlock();
	}
	
	public String getCurrentFreshnessDayType(Calendar time) {
		switch(time.get(Calendar.DAY_OF_WEEK)) {
		case Calendar.SATURDAY:
			return SATURDAY_FRESHNESS_COLUMN;
		case Calendar.SUNDAY:
			return SUNDAY_FRESHNESS_COLUMN;
		default:
			return WEEKDAY_FRESHNESS_COLUMN;
		}
	}

	private String currentFreshnessColumn(Calendar time) {
		String curFresh = getCurrentFreshnessDayType(time);
		if (curFresh.equals(SATURDAY_FRESHNESS_COLUMN)) {
			return SATURDAY_FRESHNESS_COLUMN;
		}
		if (curFresh.equals(SUNDAY_FRESHNESS_COLUMN)) {
			return SUNDAY_FRESHNESS_COLUMN;
		}
		return WEEKDAY_FRESHNESS_COLUMN;
	}
	
	private String currentFreshnessColumnNow() {
		return currentFreshnessColumn(Calendar.getInstance());
	}
	
	private String currentLocationFreshnessColumn(Calendar time) {
		String curFresh = getCurrentFreshnessDayType(time);
		if (curFresh.equals(SATURDAY_FRESHNESS_COLUMN)) {
			return SATURDAY_LOCATION_FRESHNESS_COLUMN;
		}
		if (curFresh.equals(SUNDAY_FRESHNESS_COLUMN)) {
			return SUNDAY_LOCATION_FRESHNESS_COLUMN;
		}
		return WEEKDAY_LOCATION_FRESHNESS_COLUMN;
	}
	
	public int updateStrRes(int updateStatus) {
		switch(updateStatus) {
		case UPDATE_NOT_REQUIRED:
			return R.string.update_not_required;
		case UPDATE_RECOMMENDED:
			return R.string.update_recommended;
		case UPDATE_REQUIRED:
			return R.string.update_required;
		default:
			return R.string.update_not_required;
		}
	}

	HashMap<String, Long> getFreshnesses(long nowMillis) {
		Cursor c = db.rawQuery(String.format(Locale.US, "select %s, %s, %s, %s, %s, %s from %s",
				WEEKDAY_FRESHNESS_COLUMN, SATURDAY_FRESHNESS_COLUMN, SUNDAY_FRESHNESS_COLUMN,
				WEEKDAY_LOCATION_FRESHNESS_COLUMN, SATURDAY_LOCATION_FRESHNESS_COLUMN, SUNDAY_LOCATION_FRESHNESS_COLUMN,
				FRESHNESS_TABLE), null);
		if (c.moveToFirst()) {
			 HashMap<String, Long> f = new HashMap<String, Long>(3);
			 f.put(WEEKDAY_FRESHNESS_COLUMN, nowMillis - c.getLong(0));
			 f.put(SATURDAY_FRESHNESS_COLUMN, nowMillis - c.getLong(1));
			 f.put(SUNDAY_FRESHNESS_COLUMN, nowMillis - c.getLong(2));
			 f.put(WEEKDAY_LOCATION_FRESHNESS_COLUMN, nowMillis - c.getLong(3));
			 f.put(SATURDAY_LOCATION_FRESHNESS_COLUMN, nowMillis - c.getLong(4));
			 f.put(SUNDAY_LOCATION_FRESHNESS_COLUMN, nowMillis - c.getLong(5));
			 c.close();
			 return f;
		}
		c.close();
		return null;
	}
	
	// determines if an update is recommended or required
	public int updateStatus(HashMap<String, Long> freshnesses, Calendar now) {
		if (freshnesses == null) {
			return UPDATE_NOT_REQUIRED; // shouldn't happen
		}
		String currentFreshnessDayType = getCurrentFreshnessDayType(now);
		long currentFreshness = freshnesses.get(currentFreshnessDayType);
		if (currentFreshness < UPDATE_DATABASE_AGE_LIMIT_SOFT) {
			// freshness for today's day type is younger than the threshold, we can bail out now
			return UPDATE_NOT_REQUIRED;
		}
		if (currentFreshness < UPDATE_DATABASE_AGE_LIMIT_HARD) {
			return UPDATE_RECOMMENDED;
		}
		return UPDATE_REQUIRED;
	}
	
	// this gets called from the main stop list screen so it does everything itself
	public int getUpdateStatus() {
		Calendar now = Calendar.getInstance();
		HashMap<String, Long> freshnesses = getFreshnesses(now.getTimeInMillis());
		return updateStatus(freshnesses, now);
	}
	
	void noteStopUse(int stopNumber) {
		long now = System.currentTimeMillis();
		ContentValues cv = new ContentValues(2);
		cv.put(STOP_NUMBER, stopNumber);
		cv.put(STOP_LAST_USE_TIME, now);
		db.insert(STOP_LAST_USE_TABLE, null, cv);
		// now delete all but the last STOP_HISTORY_LENGTH
		String q = String.format(Locale.US, "delete from %s " +
				"where %s < " +
				"(select %s from %s " +
				"order by %s desc limit 1 offset %d)",
				STOP_LAST_USE_TABLE,
				STOP_LAST_USE_TIME,
				STOP_LAST_USE_TIME,
				STOP_LAST_USE_TABLE,
				STOP_LAST_USE_TIME,
				STOP_HISTORY_LENGTH - 1);
		db.execSQL(q);
	}
	
	void forgetStopUse(int stopNumber) {
		db.delete(STOP_LAST_USE_TABLE,
				String.format(Locale.US, "%s = %d", STOP_NUMBER, stopNumber),
				null);
	}
	
	LTCRoute[] getAllRoutes(boolean withNull, boolean favsOnly) {
		LTCRoute[] routes = null;
		String selection = null;
		if (favsOnly) {
			selection = String.format(Locale.US, "%s in (select distinct %s from %s where %s in (select %s from %s where %s > 0))",
					ROUTE_NUMBER, ROUTE_NUMBER, LINK_TABLE, STOP_NUMBER, STOP_NUMBER, STOPS_WITH_USES, STOP_USES_COUNT);
		}
		Cursor c = db.query(ROUTE_TABLE,
				new String[] { ROUTE_NUMBER, ROUTE_NAME },
				selection, null, null, null, ROUTE_NUMBER);
		if (c.moveToFirst()) {
			int size = c.getCount();
			int i = 0;
			if (withNull) {
				++size;
			}
			routes = new LTCRoute[size];
			if (withNull) {
				routes[i++] = null;
			}
			for (; !c.isAfterLast(); c.moveToNext()) {
				routes[i++] = new LTCRoute(c.getString(0), c.getString(1));
			}
			c.close();
		}
		else {
			routes = new LTCRoute[0];
		}
		return routes;
	}

	LTCStop findStop(String stopNumber) {
		LTCStop stop = null;
		Cursor c = db.query(STOP_TABLE,
				new String[] { STOP_NUMBER, STOP_NAME },
				String.format(Locale.US, "%s = ?", STOP_NUMBER), new String[] { stopNumber },
				null, null, null);
		if (c.moveToFirst()) {
			stop = new LTCStop(c.getInt(0), c.getString(1));
		}
		c.close();
		return stop;
	}

	/* given a route, return how many stops exist */
	int getStopCount() {
		Cursor c = db.rawQuery(String.format(Locale.US, "select count(*) from %s;",
				LINK_TABLE),
				null);
		int count = 0;
		if (c.moveToFirst()) {
			count = c.getInt(0);
			c.close();
		}
		return count;

	}
	
	/* given a route, return how many stops are on that route */
	int getStopCount(LTCRoute route) {
		Cursor c = db.rawQuery(String.format(Locale.US, "select count(*) from %s where %s = ?;",
				LINK_TABLE,
				ROUTE_NUMBER),
				new String[] { route.number });
		int count = 0;
		if (c.moveToFirst()) {
			count = c.getInt(0);
			c.close();
		}
		return count;

	}
	
	/* given a route, return how many stops on that route lack location information */
	int getLocationlessStopCount(LTCRoute route) {
		Cursor c = db.rawQuery(String.format(Locale.US, "select count(*) from %s where (%s is null or %s < 0.1) and %s in (select %s from %s where %s = ?);",
				STOP_TABLE,
				LATITUDE, LATITUDE,
				STOP_NUMBER,
				STOP_NUMBER,
				LINK_TABLE,
				ROUTE_NUMBER),
				new String[] { route.number });
		int count = 0;
		if (c.moveToFirst()) {
			count = c.getInt(0);
			c.close();
		}
		return count;

	}
	
	/* this fetches routes, but it also adds the direction and direction initial letter */
	ArrayList<LTCRoute> findStopRoutes(String stopNumber, String routeNumber, int directionNumber) {
		String freshCol = currentFreshnessColumnNow();
		Cursor c = db.rawQuery(String.format(Locale.US, "select %s.%s, %s.%s, %s.%s, %s.%s from %s, %s, %s where %s.%s = %s.%s and %s.%s = %s.%s and %s.%s = ? and %s.%s != 0",
											 ROUTE_TABLE, ROUTE_NUMBER,
											 ROUTE_TABLE, ROUTE_NAME,
											 LINK_TABLE, DIRECTION_NUMBER,
											 DIRECTION_TABLE, DIRECTION_NAME,
											 ROUTE_TABLE, LINK_TABLE, DIRECTION_TABLE,
											 ROUTE_TABLE, ROUTE_NUMBER, LINK_TABLE, ROUTE_NUMBER,
											 LINK_TABLE, DIRECTION_NUMBER, DIRECTION_TABLE, DIRECTION_NUMBER,
											 LINK_TABLE, STOP_NUMBER,
											 LINK_TABLE, freshCol),
				new String[] { stopNumber });
		ArrayList<LTCRoute> routes = new ArrayList<LTCRoute>();
		if (c.moveToFirst()) {
			for (; !c.isAfterLast(); c.moveToNext()) {
				LTCRoute route = new LTCRoute(c.getString(0), c.getString(1), c.getInt(2), c.getString(3));
				if ((routeNumber == null || (routeNumber.equals(route.number) && directionNumber == route.direction))) {
					routes.add(route);
				}
			}
			c.close();
		}
		return routes;
	}
	
	/* this fetches routes, but it also adds the direction and direction initial letter */
	private String findStopRouteSummary(String stopNumber) {
		String freshCol = currentFreshnessColumnNow();
		String query = String.format(Locale.US, "select ltrim(%s.%s, '0'), substr(%s.%s, 1, 1) " +
				"from %s, %s, %s " +
				"where %s.%s = %s.%s and %s.%s = %s.%s and %s.%s = ? and %s.%s != 0 " +
				"order by %s.%s",
				ROUTE_TABLE, ROUTE_NUMBER, DIRECTION_TABLE, DIRECTION_NAME,
				ROUTE_TABLE, LINK_TABLE, DIRECTION_TABLE,
				ROUTE_TABLE, ROUTE_NUMBER, LINK_TABLE, ROUTE_NUMBER,
				LINK_TABLE, DIRECTION_NUMBER, DIRECTION_TABLE, DIRECTION_NUMBER,
				LINK_TABLE, STOP_NUMBER,
				LINK_TABLE, freshCol,
				ROUTE_TABLE, ROUTE_NUMBER);
		Cursor c = db.rawQuery(query,
				new String[] { stopNumber });
		if (c.moveToFirst()) {
			String summary = null;
			String lastRouteNum = "";
			@SuppressWarnings("unused")
			int i;
			for (i = 0; !c.isAfterLast(); i++, c.moveToNext()) {
				String routeNum = c.getString(0);
				String routeDir = c.getString(1);
				if (summary == null) {
					summary = routeNum + routeDir;
				}
				else {
					if (lastRouteNum.equals(routeNum)) {
						summary += routeDir; // just append the direction to the same route number
					}
					else {
						summary += " " + routeNum + routeDir; // append both
					}
				}
				lastRouteNum = routeNum;
			}
			c.close();
			return summary;
		}
		else {
			c.close();
			return "";
		}
	}
	
	List<HashMap<String, String>> findStops(CharSequence text, Location location, boolean onlyFavourites, LTCRoute mustServiceRoute) {
		String searchText = text.toString();
		String whereClause;
		String[] words = searchText.trim().toLowerCase().split("\\s+");
		String[] likes = new String[words.length];
		float results[] = new float[1];
		double lat = 0, lon = 0; // cache of contents of location
		int i; // note - used multiple places
		for (i = 0; i < words.length; ++i) {
			likes[i] = String.format(Locale.US, "stop_name like %s", DatabaseUtils.sqlEscapeString("%"+words[i]+"%"));
		}
		whereClause = "(" + TextUtils.join(" and ", likes) + ")";
		if (searchText.matches("^\\d+$")) {
			whereClause += String.format(Locale.US, " or (%s like %s)", STOP_NUMBER, DatabaseUtils.sqlEscapeString(text + "%"));
		}
		if (onlyFavourites) {
			whereClause = String.format(Locale.US, "(%s) and (%s > 0)", whereClause, STOP_USES_COUNT);
		}
		if (mustServiceRoute != null) {
			whereClause = String.format(Locale.US, "(%s) and stop_number in (select %s from %s where %s = '%s')",
					whereClause, STOP_NUMBER, LINK_TABLE, ROUTE_NUMBER, mustServiceRoute.number);
		}
		String order;
		if (location == null) {
			if (onlyFavourites) {
				order = String.format(Locale.US, "%s asc", STOP_NAME);
			}
			else {
				order = String.format(Locale.US, "%s desc, %s asc", STOP_USES_COUNT, STOP_NAME);
			}
		}
		else {
			/* this pretends that the coordinates are Cartesian for quick fetching from
			 * the database, note that we still need to re-sort them again later when
			 * we compute their actual distance from each other
			 */
			lat = location.getLatitude();
			lon = location.getLongitude();
			order = String.format(Locale.US, "(latitude-(%f))*(latitude-(%f)) + (longitude-(%f))*(longitude-(%f))",
					lat, lat, lon, lon);
		}
		List<HashMap<String, String>> stops = new ArrayList<HashMap<String, String>>();
		String[] cols = new String[] { STOP_NUMBER, STOP_NAME, LATITUDE, LONGITUDE, STOP_USES_COUNT };
		Cursor c = db.query(STOPS_WITH_USES, cols, whereClause, null, null, null, order, "20");
		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			HashMap<String,String> map = new HashMap<String,String>(2);
			for (i = 0; i < cols.length; ++i) {
				map.put(cols[i], c.getString(i));
			}
			if (location != null) {
				String stopLat = map.get(LATITUDE);
				String stopLon = map.get(LONGITUDE);
				Location.distanceBetween(lat, lon,
						(stopLat == null ? 0.0 : Double.valueOf(stopLat)),
						(stopLon == null ? 0.0 : Double.valueOf(stopLon)),
						results);
				
				map.put(DISTANCE_TEXT, niceDistance(results[0]));
				String distorder = String.format(Locale.US, "%09.0f", results[0]);
				map.put(DISTANCE_ORDER, distorder);
			}
			stops.add(map);
		}
		c.close();
		if (location != null) {
			Collections.sort(stops, new StopComparator());
		}
		return stops;
	}
	
	class StopComparator implements Comparator<Map<String, String>> {
		
	    public int compare(Map<String, String> first, Map<String, String> second) {
	    	String firstValue = first.get(DISTANCE_ORDER);
	    	String secondValue = second.get(DISTANCE_ORDER);
	    	return firstValue.compareTo(secondValue);
	    }

	}

	// format a distance nicely
	private String niceDistance(float val) {
		
		if (val < 20) {
			return String.format(Locale.US, "%.0fm", val);
		}
		else if (val < 250) {
			return String.format(Locale.US, "%.0fm", Math.rint(val/5) * 5);
		}
		else if (val < 1000) {
			return String.format(Locale.US, "%.0fm", Math.rint(val/10) * 10);
		}
		else {
			return String.format(Locale.US, "%.1fkm", val/1000);
		}
	}
	
	void addRoutesToStopList(List<HashMap<String, String>> stops)
	{
		for (HashMap<String, String> stopEntry : stops) {
			stopEntry.put(ROUTE_LIST, findStopRouteSummary(stopEntry.get(STOP_NUMBER)));
		}
	}

	// zero any freshnesses older than the "current" freshness
	public void clearOldFreshnesses(String table, String col) {
		//Log.i("db", String.format(Locale.US, "clearOld(%s,  %s)", table, col));
		String s = String.format(Locale.US, "UPDATE %s set %s = 0 WHERE %s < (SELECT %s from %s)",
				table, col, col, col, FRESHNESS_TABLE);
		db.execSQL(s);
	}
	
	// delete any rows where all freshnesses are old
	public void deleteStaleRecords(String table) {
		//Log.i("db", String.format(Locale.US, "deleteStale(%s)", table));
		String s = String.format(Locale.US, "DELETE FROM %s WHERE %s = 0 and %s = 0 and %s = 0",
				table,
				WEEKDAY_FRESHNESS_COLUMN, SATURDAY_FRESHNESS_COLUMN, SUNDAY_FRESHNESS_COLUMN);
		db.execSQL(s);
	}
	
	// called by saveBusData() to clear out stale records
	public void purgeStaleRecords(String table, String col) {
		//Log.i("db", String.format(Locale.US, "purgeStale(%s,  %s)", table, col));
		clearOldFreshnesses(table, col);
		deleteStaleRecords(table);
	}

	// used to populate the stop table, with coordinates, from the included asset file
	public void ensureStopPreload() {
		if (getStopCount() == 0) {
			// don't attempt to preload unless we have no data at all
			try {
				AssetManager assetManager = context.getAssets();
				InputStream is = assetManager.open("init-stops.sql");
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader reader = new BufferedReader(isr);
				db.beginTransaction();
				String line;
				while ((line = reader.readLine()) != null) {
					db.execSQL(line);
				}
				db.setTransactionSuccessful();
				reader.close();
			}
			catch (IOException e) {
				// do nothing
			}
			catch (SQLException e) {
				// do nothing
			}
			finally {
				db.endTransaction();
			}
		}
	}
	
	// called by the scraper to load everything it found into the database
	public void saveBusData(Collection<LTCRoute> routes,
			Collection<LTCDirection> directions,
			Collection<LTCStop> stops,
			Collection<RouteStopLink> links,
			boolean withLocations) throws SQLException {
		db.beginTransaction();
		/* we use insert for everything below because all tables have an appropriate UNIQUE constraint with
		 * ON CONFLICT REPLACE
		 */
		try {
			Calendar now = Calendar.getInstance();
			long nowMillis = now.getTimeInMillis();
			String freshCol = currentFreshnessColumn(now);
			ContentValues cv = new ContentValues(5); // 5 should deal with everything
			cv.clear();
			cv.put(freshCol, nowMillis);
			if (withLocations) {
				cv.put(currentLocationFreshnessColumn(now), nowMillis);
			}
			//Log.i("db", "Update freshnesses");
			db.update(FRESHNESS_TABLE, cv, null, null);
			if (routes != null) {
				//Log.i("db", "Update routes");
				for (LTCRoute route : routes) {
					cv.clear();
					cv.put(ROUTE_NUMBER, route.number);
					cv.put(ROUTE_NAME, route.name);
					cv.put(freshCol, nowMillis);
					if (db.update(ROUTE_TABLE, cv, String.format(Locale.US, "%s = ?", ROUTE_NUMBER),
							new String[] { route.number }) == 0) {
						db.insertWithOnConflict(ROUTE_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
					}
				}
				purgeStaleRecords(ROUTE_TABLE, freshCol);
			}
			if (directions != null) {
				//Log.i("db", "Update directions");
				for (LTCDirection dir : directions) {
					cv.clear();
					cv.put(DIRECTION_NUMBER, dir.number);
					cv.put(DIRECTION_NAME, dir.name);
					cv.put(freshCol, nowMillis);
					if (db.update(DIRECTION_TABLE, cv, String.format(Locale.US, "%s = ?", DIRECTION_NUMBER),
							new String[] { String.valueOf(dir.number) }) == 0) {
						db.insertWithOnConflict (DIRECTION_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
					}
				}
				purgeStaleRecords(DIRECTION_TABLE, freshCol);
			}
			if (stops != null) {
				//Log.i("db", "Update stops");
				for (LTCStop stop : stops) {
					cv.clear();
					cv.put(STOP_NUMBER, stop.number);
					cv.put(STOP_NAME, stop.name);
					if (stop.latitude > MIN_LATITUDE) {
						// latitude should be zero if it wasn't fetched
						cv.put(LATITUDE, stop.latitude);
						cv.put(LONGITUDE, stop.longitude);
					}
					cv.put(freshCol, nowMillis);
					if (db.update(STOP_TABLE, cv, String.format(Locale.US, "%s = ?", STOP_NUMBER),
							new String[] { String.valueOf(stop.number) }) == 0) {
						db.insertWithOnConflict (STOP_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
					}
				}
				purgeStaleRecords(STOP_TABLE, freshCol);
			}
			if (links != null) {
				//Log.i("db", "Update links");
				for (RouteStopLink link : links) {
					cv.clear();
					cv.put(ROUTE_NUMBER, link.routeNumber);
					cv.put(DIRECTION_NUMBER, link.directionNumber);
					cv.put(STOP_NUMBER, link.stopNumber);
					cv.put(freshCol, nowMillis);
					if (db.update(LINK_TABLE, cv, String.format(Locale.US, "%s = ? and %s = ? and %s = ?",
							ROUTE_NUMBER,
							DIRECTION_NUMBER,
							STOP_NUMBER),
							new String[] { link.routeNumber, String.valueOf(link.directionNumber), String.valueOf(link.stopNumber) }) == 0) {
						db.insertWithOnConflict (LINK_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
					}
				}
				purgeStaleRecords(LINK_TABLE, freshCol);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
		
}
