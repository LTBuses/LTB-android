package org.frasermccrossan.ltc;

import java.security.acl.LastOwnerException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
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
	static final long ONE_MONTH = 1000L * 60L * 60L * 24L * 30L;
	
	static final String LINK_TABLE = "route_stops";
	
	// fake columns, defined here for consistency
	static final String DESTINATION = "destination";
	static final String CROSSING_TIME = "crossing_time";
	static final String DATE_VALUE = "date_value";
	static final String FAILED = "failed";

	SQLiteDatabase db;
	
	BusDb(Context c) {
		BusDbOpenHelper helper = new BusDbOpenHelper(c);
		db = helper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
	}
	
	public int updateFreshness(Calendar time) {
		switch(time.get(Calendar.DAY_OF_WEEK)) {
		case Calendar.SATURDAY:
			return SATURDAY_FRESHNESS;
		case Calendar.SUNDAY:
			return SUNDAY_FRESHNESS;
		default:
			return WEEKDAY_FRESHNESS;
		}
	}
	
	private String freshnessColumn(Calendar time) {
		switch(updateFreshness(time)) {
		case SATURDAY_FRESHNESS:
			return SATURDAY_FRESHNESS_COLUMN;
		case SUNDAY_FRESHNESS:
			return SUNDAY_FRESHNESS_COLUMN;
		default:
			return WEEKDAY_FRESHNESS_COLUMN;
		}
	}
	
	public int freshnessStrRes() {
		Calendar now = Calendar.getInstance();
		switch(updateFreshness(now)) {
		case SATURDAY_FRESHNESS:
			return R.string.on_saturday;
		case SUNDAY_FRESHNESS:
			return R.string.on_sunday;
		default:
			return R.string.on_weekday;
		}
	}
	
	public int updateStrRes() {
		switch(updateStatus()) {
		case UPDATE_REQUIRED:
			return R.string.update_required;
		case UPDATE_RECOMMENDED:
			return R.string.update_recommended;
		default:
			return R.string.update_not_required;
		}
	}
	
	// determines if an update is recommended or required
	public int updateStatus() {
		Calendar now = Calendar.getInstance();
		Cursor c = db.rawQuery(String.format("select %s from %s", freshnessColumn(now), FRESHNESS_TABLE), null);
		if (c.moveToFirst()) {
			long freshness = c.getLong(0);
			c.close();
			if (freshness == 0) {
				return UPDATE_REQUIRED;
			}
			/* I don't know why we need this bullshit here, but the expression
			 * now.getTimeInMillis() - freshness >= ONE_MONTH gives an incorrect result;
			 * some weird Java casting rule I'm not aware of?
			 */
			long gtim = now.getTimeInMillis();
			long diff = gtim - freshness;
			if (diff >= ONE_MONTH) {
				return UPDATE_RECOMMENDED;
			}
			return UPDATE_NOT_REQUIRED; // I know, it's down there too
		}
		return UPDATE_NOT_REQUIRED;
	}
	
	public boolean isValid() {
		int status = updateStatus();
		return status != UPDATE_REQUIRED;
	}
	
	void noteStopUse(LTCStop stop) {
		long now = System.currentTimeMillis();
		ContentValues cv = new ContentValues(2);
		cv.put(STOP_NUMBER, stop.number);
		cv.put(STOP_LAST_USE_TIME, now);
		db.insert(STOP_LAST_USE_TABLE, null, cv);
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
	
	List<HashMap<String, String>> findStops(CharSequence text, Location location) {
		String searchText = text.toString();
		List<HashMap<String, String>> stops = new ArrayList<HashMap<String, String>>();
		String whereLike;
		if (searchText.matches("^\\d+$")) {
			whereLike = String.format("%s = %s", STOP_NUMBER, text);
		}
		else {
			String[] words = searchText.trim().toLowerCase().split("\\s+");
			String[] likes = new String[words.length];
			int i;
			for (i = 0; i < words.length; ++i) {
				likes[i] = String.format("stop_name like %s", DatabaseUtils.sqlEscapeString("%"+words[i]+"%"));
			}
			whereLike = TextUtils.join(" and ", likes);
		}
		String order;
		if (location == null) {
			order = STOP_NAME;
		}
		else {
			double lat = location.getLatitude();
			double lon = location.getLongitude();
			order = String.format("(latitude-(%f))*(latitude-(%f)) + (longitude-(%f))*(longitude-(%f))",
					lat, lat, lon, lon);
		}
		Cursor c = db.query(STOP_TABLE, new String[] { STOP_NUMBER, STOP_NAME }, whereLike, null, null, null, order, "20");
		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			HashMap<String,String> map = new HashMap<String,String>(2);
			map.put(STOP_NUMBER, c.getString(0));
			map.put(STOP_NAME, c.getString(1));
			stops.add(map);
		}
		c.close();
		return stops;
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
			cv.put(freshnessColumn(now), nowMillis);
			db.update(FRESHNESS_TABLE, cv, null, null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
	
}
