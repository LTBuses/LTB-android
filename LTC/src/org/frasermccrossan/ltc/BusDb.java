package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
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
	
	static final String DIRECTION_TABLE = "directions";
	static final String DIRECTION_NUMBER = "direction_number";
	static final String DIRECTION_NAME = "direction_name";
	
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
	static final String WEEKDAY_FRESHNESS_COLUMN = "weekday_freshness";
	static final String SATURDAY_FRESHNESS_COLUMN = "saturday_freshness";
	static final String SUNDAY_FRESHNESS_COLUMN = "sunday_freshness";
	
	static final int UPDATE_NOT_REQUIRED = 0;
	static final int UPDATE_RECOMMENDED = 1;
	static final int UPDATE_REQUIRED = 2;
	static final long UPDATE_DATABASE_AGE_LIMIT = 1000L * 60L * 60L * 24L * 60L; // 60 days
	
	static final String LINK_TABLE = "route_stops";
	
	// fake columns, defined here for consistency
	static final String DESTINATION = "destination";
	static final String CROSSING_TIME = "crossing_time";
	static final String DATE_VALUE = "date_value";
	static final String FAILED = "failed";
	static final String RAW_TIME = "rawtime";

	SQLiteDatabase db;
	Context context;
	
	BusDb(Context c) {
		context = c;
		BusDbOpenHelper helper = new BusDbOpenHelper(context);
		db = helper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
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
	
//	public int freshnessStrRes() {
//		Calendar now = Calendar.getInstance();
//		switch(getCurrentFreshnessDayType(now)) {
//		case SATURDAY_FRESHNESS:
//			return R.string.saturday;
//		case SUNDAY_FRESHNESS:
//			return R.string.sunday;
//		default:
//			return R.string.weekday;
//		}
//	}

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

//	public int updateStrRes() {
//		switch(updateStatus()) {
//		case UPDATE_REQUIRED:
//			return R.string.update_required;
//		case UPDATE_RECOMMENDED:
//			return R.string.update_recommended;
//		default:
//			return R.string.update_not_required;
//		}
//	}
	
	HashMap<Integer, Long> getFreshnesses(long nowMillis) {
		Cursor c = db.rawQuery(String.format("select %s, %s, %s from %s",
				WEEKDAY_FRESHNESS_COLUMN, SATURDAY_FRESHNESS_COLUMN, SUNDAY_FRESHNESS_COLUMN, FRESHNESS_TABLE), null);
		if (c.moveToFirst()) {
			 HashMap<Integer, Long> f = new HashMap<Integer, Long>(3);
			 f.put(WEEKDAY_FRESHNESS, nowMillis - c.getLong(0));
			 f.put(SATURDAY_FRESHNESS, nowMillis - c.getLong(1));
			 f.put(SUNDAY_FRESHNESS, nowMillis - c.getLong(2));
			 c.close();
			 return f;
		}
		c.close();
		return null;
	}
	
	// determines if an update is recommended or required
	public int updateStatus(HashMap<Integer, Long> freshnesses, Calendar now) {
		int currentFreshnessDayType = getCurrentFreshnessDayType(now);
		if (freshnesses == null) {
			return UPDATE_NOT_REQUIRED; // shouldn't happen
		}
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
	
	// this gets called from the main stop list screen so it does everything itself
	public int getUpdateStatus() {
		Calendar now = Calendar.getInstance();
		HashMap<Integer, Long> freshnesses = getFreshnesses(now.getTimeInMillis());
		return updateStatus(freshnesses, now);
	}
	
//	public boolean isValid() {
//		int status = updateStatus();
//		return status != UPDATE_REQUIRED;
//	}
	
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
	LTCRoute[] findStopRoutes(String stopNumber) {
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
		if (c.moveToFirst()) {
			LTCRoute[] routes = new LTCRoute[c.getCount()];
			int i;
			for (i = 0; !c.isAfterLast(); i++, c.moveToNext()) {
				routes[i] = new LTCRoute(c.getString(0), c.getString(1), c.getInt(2), c.getString(3));
			}
			c.close();
			return routes;
		}
		else {
			return new LTCRoute[0];
		}
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
	
	List<HashMap<String, String>> findStops(CharSequence text, Location location) {
		String searchText = text.toString();
		String whereClause;
		String[] words = searchText.trim().toLowerCase().split("\\s+");
		String[] likes = new String[words.length];
		int i; // note - used multiple places
		for (i = 0; i < words.length; ++i) {
			likes[i] = String.format("stop_name like %s", DatabaseUtils.sqlEscapeString("%"+words[i]+"%"));
		}
		whereClause = "(" + TextUtils.join(" and ", likes) + ")";
		if (searchText.matches("^\\d+$")) {
			whereClause += String.format(" or (%s like %s)", STOP_NUMBER, DatabaseUtils.sqlEscapeString(text + "%"));
		}
		String order;
		if (location == null) {
			order = String.format("%s desc, %s", STOP_USES_COUNT, STOP_NAME);
		}
		else {
			double lat = location.getLatitude();
			double lon = location.getLongitude();
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
			stops.add(map);
		}
		c.close();
		return stops;
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
			Collection<RouteStopLink> links) throws SQLException {
		db.beginTransaction();
		/* we use insert for everything below because all tables have an appropriate UNIQUE constraint with
		 * ON CONFLICT REPLACE
		 */
		try {
			Calendar now = Calendar.getInstance();
			long nowMillis = now.getTimeInMillis();
			ContentValues cv = new ContentValues(5); // 5 should deal with everything
			for (LTCRoute route : routes) {
				cv.clear();
				cv.put(ROUTE_NUMBER, route.number);
				cv.put(ROUTE_NAME, route.name);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict(ROUTE_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
			}
			for (LTCDirection dir : directions) {
				cv.clear();
				cv.put(DIRECTION_NUMBER, dir.number);
				cv.put(DIRECTION_NAME, dir.name);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict (DIRECTION_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);				
			}
			for (LTCStop dir : stops) {
				cv.clear();
				cv.put(STOP_NUMBER, dir.number);
				cv.put(STOP_NAME, dir.name);
				cv.put(LATITUDE, dir.latitude);
				cv.put(LONGITUDE, dir.longitude);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict (STOP_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);				
			}
			for (RouteStopLink link : links) {
				cv.clear();
				cv.put(ROUTE_NUMBER, link.routeNumber);
				cv.put(DIRECTION_NUMBER, link.directionNumber);
				cv.put(STOP_NUMBER, link.stopNumber);
				cv.put(FRESHNESS, nowMillis);
				db.insertWithOnConflict (LINK_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);				
			}
			cv.clear();
			cv.put(currentFreshnessColumn(now), nowMillis);
			db.update(FRESHNESS_TABLE, cv, null, null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
	
}
