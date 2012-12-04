package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

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
	static final String SHORT_DIRECTION_NAME = "short_direction_name";
	static final String DIRECTION_IMG_RES = "direction_img_res";
	
	static final String STOP_LAST_USE_TABLE = "stop_uses";
	static final String STOP_LAST_USE_TIME = "stop_last_use_time";
	static final String STOPS_WITH_USES = "stops_with_uses";
	static final String STOP_USES_COUNT = "stop_uses_count"; // for the count view
	static final int STOP_HISTORY_LENGTH = 200;
	
	static final String ROUTE_LIST = "route_list";

	static final String FRESHNESS = "freshness";
	
	static final String FRESHNESS_TABLE = "last_updates";
	static final int WEEKDAY_FRESHNESS = 0;
	static final int SATURDAY_FRESHNESS = 1;
	static final int SUNDAY_FRESHNESS = 2;
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
	static final long UPDATE_DATABASE_AGE_LIMIT = 1000L * 60L * 60L * 24L * 90L; // 90 days
	static final long DELETE_ROWS_AFTER = 1000L * 60L * 60L * 24L * 180L;
	
	static final String LINK_TABLE = "route_stops";
	
	// fake columns, defined here for consistency
	static final String DESTINATION = "destination";
	static final String CROSSING_TIME = "crossing_time";
	static final String DATE_VALUE = "date_value";
	static final String FAILED = "failed";
	static final String RAW_TIME = "rawtime";
	static final String DISTANCE_TEXT = "distance";
	static final String DISTANCE_ORDER = "distance_order";
	static final String ROUTE_INTERNAL_NUMBER = "route_object"; // for storing route object in prediction entry
	static final String ROUTE_DIRECTION_NAME = "route_name";
	
	static final private ReentrantLock blocker = new ReentrantLock();
	
	SQLiteDatabase db;
	Context context;
	
	BusDb(Context c) {
		context = c;
		Log.i("BusDb", String.format("%d:%s %s", Thread.currentThread().getId(), "waiting to lock", blocker.toString()));
		blocker.lock();
		Log.i("BusDb", String.format("%d:%s %s", Thread.currentThread().getId(), "just locked", blocker.toString()));
		BusDbOpenHelper helper = new BusDbOpenHelper(context);
		db = helper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
		blocker.unlock();
		Log.i("BusDb", String.format("%d:%s %s", Thread.currentThread().getId(), "just unlocked", blocker.toString()));
	}
	
	public int getCurrentFreshnessDayType(Calendar time) {
		switch(time.get(Calendar.DAY_OF_WEEK)) {
		case Calendar.SATURDAY:
			return SATURDAY_FRESHNESS;
		case Calendar.SUNDAY:
			return SUNDAY_FRESHNESS;
		default:
			return WEEKDAY_FRESHNESS;
		}
	}
	
	public int getCurrentLocationFreshnessDayType(Calendar time) {
		switch(time.get(Calendar.DAY_OF_WEEK)) {
		case Calendar.SATURDAY:
			return SATURDAY_LOCATION_FRESHNESS;
		case Calendar.SUNDAY:
			return SUNDAY_LOCATION_FRESHNESS;
		default:
			return WEEKDAY_LOCATION_FRESHNESS;
		}
	}
	
	private String currentFreshnessColumn(Calendar time) {
		switch(getCurrentFreshnessDayType(time)) {
		case SATURDAY_FRESHNESS:
			return SATURDAY_FRESHNESS_COLUMN;
		case SUNDAY_FRESHNESS:
			return SUNDAY_FRESHNESS_COLUMN;
		default:
			return WEEKDAY_FRESHNESS_COLUMN;
		}
	}
	
	private String currentLocationFreshnessColumn(Calendar time) {
		switch(getCurrentFreshnessDayType(time)) {
		case SATURDAY_FRESHNESS:
			return SATURDAY_LOCATION_FRESHNESS_COLUMN;
		case SUNDAY_FRESHNESS:
			return SUNDAY_LOCATION_FRESHNESS_COLUMN;
		default:
			return WEEKDAY_LOCATION_FRESHNESS_COLUMN;
		}
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

	HashMap<Integer, Long> getFreshnesses(long nowMillis) {
		Cursor c = db.rawQuery(String.format("select %s, %s, %s, %s, %s, %s from %s",
				WEEKDAY_FRESHNESS_COLUMN, SATURDAY_FRESHNESS_COLUMN, SUNDAY_FRESHNESS_COLUMN,
				WEEKDAY_LOCATION_FRESHNESS_COLUMN, SATURDAY_LOCATION_FRESHNESS_COLUMN, SUNDAY_LOCATION_FRESHNESS_COLUMN,
				FRESHNESS_TABLE), null);
		if (c.moveToFirst()) {
			 HashMap<Integer, Long> f = new HashMap<Integer, Long>(3);
			 f.put(WEEKDAY_FRESHNESS, nowMillis - c.getLong(0));
			 f.put(SATURDAY_FRESHNESS, nowMillis - c.getLong(1));
			 f.put(SUNDAY_FRESHNESS, nowMillis - c.getLong(2));
			 f.put(WEEKDAY_LOCATION_FRESHNESS, nowMillis - c.getLong(3));
			 f.put(SATURDAY_LOCATION_FRESHNESS, nowMillis - c.getLong(4));
			 f.put(SUNDAY_LOCATION_FRESHNESS, nowMillis - c.getLong(5));
			 c.close();
			 return f;
		}
		c.close();
		return null;
	}
	
	// determines if an update is recommended or required
	public int updateStatus(HashMap<Integer, Long> freshnesses, Calendar now) {
		if (freshnesses == null) {
			return UPDATE_NOT_REQUIRED; // shouldn't happen
		}
		int currentFreshnessDayType = getCurrentFreshnessDayType(now);
		long currentFreshness = freshnesses.get(currentFreshnessDayType);
		if (currentFreshness < UPDATE_DATABASE_AGE_LIMIT) {
			// freshness for today's day type is younger than the threshold, we can bail out now
			return UPDATE_NOT_REQUIRED;
		}
		// nope, we need to know how old the others are
		long latestOtherFreshness = UPDATE_DATABASE_AGE_LIMIT;
		// at this point we are computing other freshness in epoch time
		for (int ft: freshnesses.keySet()) {
			if (ft != currentFreshnessDayType) {
				if (freshnesses.get(ft) < latestOtherFreshness) {
					latestOtherFreshness = freshnesses.get(ft);
				}
			}
		}
		if (latestOtherFreshness < UPDATE_DATABASE_AGE_LIMIT) {
			// one of the others is recent enough, just recommend an update
			return UPDATE_RECOMMENDED;
		}
		// nothing is young enough, require an update
		return UPDATE_REQUIRED;
	}
	
	public int locationUpdateStatus(HashMap<Integer, Long> freshnesses, Calendar now) {
		if (freshnesses == null) {
			return UPDATE_REQUIRED; // shouldn't happen
		}
		int currentFreshnessDayType = getCurrentFreshnessDayType(now);
		int currentLocationFreshnessDayType = getCurrentLocationFreshnessDayType(now);
		if (freshnesses.get(currentLocationFreshnessDayType) ==
				freshnesses.get(currentFreshnessDayType)) {
			// location freshness and stop freshness are the same, all up to date!
			return UPDATE_NOT_REQUIRED;
		}
		if (freshnesses.get(currentLocationFreshnessDayType) - freshnesses.get(currentFreshnessDayType) > UPDATE_DATABASE_AGE_LIMIT) {
			return UPDATE_REQUIRED; // means no locations, disable the menu
		}
		// means locations are fairly new, but show warning
		return UPDATE_RECOMMENDED;
	}
	
	// this gets called from the main stop list screen so it does everything itself
	public int getUpdateStatus() {
		Calendar now = Calendar.getInstance();
		HashMap<Integer, Long> freshnesses = getFreshnesses(now.getTimeInMillis());
		return updateStatus(freshnesses, now);
	}

	// this gets called from the main stop list screen so it does everything itself
	public int getLocationUpdateStatus() {
		Calendar now = Calendar.getInstance();
		HashMap<Integer, Long> freshnesses = getFreshnesses(now.getTimeInMillis());
		return locationUpdateStatus(freshnesses, now);
	}
	
	void noteStopUse(int stopNumber) {
		long now = System.currentTimeMillis();
		ContentValues cv = new ContentValues(2);
		cv.put(STOP_NUMBER, stopNumber);
		cv.put(STOP_LAST_USE_TIME, now);
		db.insert(STOP_LAST_USE_TABLE, null, cv);
		// now delete all but the last STOP_HISTORY_LENGTH
		String q = String.format("delete from %s " +
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
				String.format("%s = %d", STOP_NUMBER, stopNumber),
				null);
	}
	
	LTCRoute[] getAllRoutes(boolean withNull) {
		LTCRoute[] routes = null;
		Cursor c = db.query(ROUTE_TABLE,
				new String[] { ROUTE_NUMBER, ROUTE_NAME },
				null, null, null, null, ROUTE_NUMBER);
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
		return routes;
	}

	LTCRoute findRoute(String routeNumber, int directionNumber) {
		LTCRoute route = null;
		Cursor c = db.query(ROUTE_TABLE,
				new String[] { ROUTE_NUMBER, ROUTE_NAME, DIRECTION_NUMBER, DIRECTION_NAME },
				String.format("%s = ?", ROUTE_NUMBER), new String[] { routeNumber },
				null, null, null);
		if (c.moveToFirst()) {
			route = new LTCRoute(c.getString(0), c.getString(1), c.getInt(2), c.getString(3));
		}
		c.close();
		return route;		
	}
	
	LTCStop findStop(String stopNumber) {
		LTCStop stop = null;
		Cursor c = db.query(STOP_TABLE,
				new String[] { STOP_NUMBER, STOP_NAME },
				String.format("%s = ?", STOP_NUMBER), new String[] { stopNumber },
				null, null, null);
		if (c.moveToFirst()) {
			stop = new LTCStop(c.getInt(0), c.getString(1));
		}
		c.close();
		return stop;
	}
	
	/* this fetches routes, but it also adds the direction and direction initial letter */
	ArrayList<LTCRoute> findStopRoutes(String stopNumber, String routeNumber, int directionNumber) {
		Cursor c = db.rawQuery(String.format("select %s.%s, %s.%s, %s.%s, %s.%s from %s, %s, %s where %s.%s = %s.%s and %s.%s = %s.%s and %s.%s = ?",
											 ROUTE_TABLE, ROUTE_NUMBER,
											 ROUTE_TABLE, ROUTE_NAME,
											 LINK_TABLE, DIRECTION_NUMBER,
											 DIRECTION_TABLE, DIRECTION_NAME,
											 ROUTE_TABLE, LINK_TABLE, DIRECTION_TABLE,
											 ROUTE_TABLE, ROUTE_NUMBER, LINK_TABLE, ROUTE_NUMBER,
											 LINK_TABLE, DIRECTION_NUMBER, DIRECTION_TABLE, DIRECTION_NUMBER,
											 LINK_TABLE, STOP_NUMBER),
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
		Cursor c = db.rawQuery(String.format("select ltrim(%s.%s, '0'), substr(%s.%s, 1, 1) " +
				"from %s, %s, %s " +
				"where %s.%s = %s.%s and %s.%s = %s.%s and %s.%s = ? " +
				"order by %s.%s",
				ROUTE_TABLE, ROUTE_NUMBER, DIRECTION_TABLE, DIRECTION_NAME,
				ROUTE_TABLE, LINK_TABLE, DIRECTION_TABLE,
				ROUTE_TABLE, ROUTE_NUMBER, LINK_TABLE, ROUTE_NUMBER,
				LINK_TABLE, DIRECTION_NUMBER, DIRECTION_TABLE, DIRECTION_NUMBER,
				LINK_TABLE, STOP_NUMBER,
				ROUTE_TABLE, ROUTE_NUMBER),
				new String[] { stopNumber });
		if (c.moveToFirst()) {
			String summary = null;
			String lastRouteNum = "";
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
			return "";
		}
	}
	
	List<HashMap<String, String>> findStops(CharSequence text, Location location, LTCRoute mustServiceRoute) {
		String searchText = text.toString();
		String whereClause;
		String[] words = searchText.trim().toLowerCase().split("\\s+");
		String[] likes = new String[words.length];
		float results[] = new float[1];
		double lat = 0, lon = 0; // cache of contents of location
		int i; // note - used multiple places
		for (i = 0; i < words.length; ++i) {
			likes[i] = String.format("stop_name like %s", DatabaseUtils.sqlEscapeString("%"+words[i]+"%"));
		}
		whereClause = "(" + TextUtils.join(" and ", likes) + ")";
		if (searchText.matches("^\\d+$")) {
			whereClause += String.format(" or (%s like %s)", STOP_NUMBER, DatabaseUtils.sqlEscapeString(text + "%"));
		}
		if (mustServiceRoute != null) {
			whereClause = String.format("(%s) and stop_number in (select %s from %s where %s = '%s')",
					whereClause, STOP_NUMBER, LINK_TABLE, ROUTE_NUMBER, mustServiceRoute.number);
		}
		String order;
		if (location == null) {
			order = String.format("%s desc, %s", STOP_USES_COUNT, STOP_NAME);
		}
		else {
			/* this pretends that the coordinates are Cartesian for quick fetching from
			 * the database, note that we still need to re-sort them again later when
			 * we compute their actual distance from each other
			 */
			lat = location.getLatitude();
			lon = location.getLongitude();
			order = String.format("(latitude-(%f))*(latitude-(%f)) + (longitude-(%f))*(longitude-(%f))",
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
				String distorder = String.format("%09.0f", results[0]);
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
			return String.format("%.0fm", val);
		}
		else if (val < 250) {
			return String.format("%.0fm", Math.rint(val/5) * 5);
		}
		else if (val < 1000) {
			return String.format("%.0fm", Math.rint(val/10) * 10);
		}
		else {
			return String.format("%.1fkm", val/1000);
		}
	}
	
	void addRoutesToStopList(List<HashMap<String, String>> stops)
	{
		for (HashMap<String, String> stopEntry : stops) {
			stopEntry.put(ROUTE_LIST, findStopRouteSummary(stopEntry.get(STOP_NUMBER)));
		}
	}

	// called by the scraper to load everything it found into the database
	public void saveBusData(Collection<LTCRoute> routes,
			Collection<LTCDirection> directions,
			Collection<LTCStop> stops,
			Collection<RouteStopLink> links,
			Boolean locationsIncluded) throws SQLException {
		db.beginTransaction();
		/* we use insert for everything below because all tables have an appropriate UNIQUE constraint with
		 * ON CONFLICT REPLACE
		 */
		try {
			Calendar now = Calendar.getInstance();
			long nowMillis = now.getTimeInMillis();
			long deleteAge = nowMillis - DELETE_ROWS_AFTER;
			String deleteCond = "freshness < ?";
			String deleteArgs[] = new String[] { String.valueOf(deleteAge) };
			ContentValues cv = new ContentValues(5); // 5 should deal with everything
			for (LTCRoute route : routes) {
				cv.clear();
				cv.put(ROUTE_NUMBER, route.number);
				cv.put(ROUTE_NAME, route.name);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict(ROUTE_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
			}
			db.delete(ROUTE_TABLE, deleteCond, deleteArgs);
			for (LTCDirection dir : directions) {
				cv.clear();
				cv.put(DIRECTION_NUMBER, dir.number);
				cv.put(DIRECTION_NAME, dir.name);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict (DIRECTION_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);				
			}
			db.delete(DIRECTION_TABLE, deleteCond, deleteArgs);
			for (LTCStop stop : stops) {
				cv.clear();
				cv.put(STOP_NUMBER, stop.number);
				cv.put(STOP_NAME, stop.name);
				if (stop.latitude > MIN_LATITUDE) {
					// latitude should be zero if it wasn't fetched
					cv.put(LATITUDE, stop.latitude);
					cv.put(LONGITUDE, stop.longitude);
				}
				cv.put(FRESHNESS, nowMillis);
				if (db.update(STOP_TABLE, cv, String.format("%s = %d", STOP_NUMBER, stop.number), null) == 0) {
					db.insertWithOnConflict (STOP_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
				}
			}
			db.delete(STOP_TABLE, deleteCond, deleteArgs);
			for (RouteStopLink link : links) {
				cv.clear();
				cv.put(ROUTE_NUMBER, link.routeNumber);
				cv.put(DIRECTION_NUMBER, link.directionNumber);
				cv.put(STOP_NUMBER, link.stopNumber);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict (LINK_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);				
			}
			db.delete(LINK_TABLE, deleteCond, deleteArgs);
			cv.clear();
			cv.put(currentFreshnessColumn(now), nowMillis);
			if (locationsIncluded) {
				cv.put(currentLocationFreshnessColumn(now), nowMillis);
			}
			db.update(FRESHNESS_TABLE, cv, null, null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
	
}
