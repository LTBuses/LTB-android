package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
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

	static final String LINK_TABLE = "route_stops";
	
	// fake columns, defined here for consistency
	static final String DESTINATION = "destination";
	static final String CROSSING_TIME = "crossing_time";
	static final String DATE_VALUE = "date_value";

	SQLiteDatabase db;
	
	BusDb(Context c) {
		BusDbOpenHelper helper = new BusDbOpenHelper(c);
		db = helper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
	}
	
	public boolean isValid() {
		Cursor c = db.rawQuery(String.format("select count(*) from %s", ROUTE_TABLE), null);
		if (c.moveToFirst()) {
			boolean valid = (c.getInt(0) > 0);
			c.close();
			return valid;
		}
		return false;
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
	
	LTCRoute[] findStopRoutes(String stopNumber) {
		Cursor c = db.rawQuery(String.format("select %s.%s, %s.%s, %s.%s from %s, %s where %s.%s = %s.%s and %s.%s = ?",
											 ROUTE_TABLE, ROUTE_NUMBER,
											 ROUTE_TABLE, ROUTE_NAME,
											 LINK_TABLE, DIRECTION_NUMBER,
											 ROUTE_TABLE, LINK_TABLE,
											 ROUTE_TABLE, ROUTE_NUMBER, LINK_TABLE, ROUTE_NUMBER,
											 LINK_TABLE, STOP_NUMBER),
				new String[] { stopNumber });
		if (c.moveToFirst()) {
			LTCRoute[] routes = new LTCRoute[c.getCount()];
			int i;
			for (i = 0; !c.isAfterLast(); i++, c.moveToNext()) {
				routes[i] = new LTCRoute(c.getString(0), c.getString(1), c.getInt(2));
			}
			c.close();
			return routes;
		}
		else {
			return new LTCRoute[0];
		}
	}
	
	List<HashMap<String, String>> findStops(CharSequence text) {
		String[] words = text.toString().trim().toLowerCase().split("\\s+");
		String[] likes = new String[words.length];
		List<HashMap<String, String>> stops = new ArrayList<HashMap<String, String>>();
		int i;
		for (i = 0; i < words.length; ++i) {
			likes[i] = String.format("stop_name like %s", DatabaseUtils.sqlEscapeString("%"+words[i]+"%"));
		}
		String whereLike = TextUtils.join(" and ", likes);
		Cursor c = db.query(STOP_TABLE, new String[] { STOP_NUMBER, STOP_NAME }, whereLike, null, null, null, STOP_NAME, "20");
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
		try {
			ContentValues cv = new ContentValues(5); // 5 should deal with everything
			db.delete(ROUTE_TABLE, null, null);
			for (LTCRoute route : routes) {
				cv.clear();
				cv.put(ROUTE_NUMBER, route.number);
				cv.put(ROUTE_NAME, route.name);
				db.insertOrThrow (ROUTE_TABLE, null, cv);
			}
			db.delete(DIRECTION_TABLE, null, null);
			for (LTCDirection dir : directions) {
				cv.clear();
				cv.put(DIRECTION_NUMBER, dir.number);
				cv.put(DIRECTION_NAME, dir.name);
				db.insertOrThrow (DIRECTION_TABLE, null, cv);				
			}
			db.delete(STOP_TABLE, null, null);
			for (LTCStop dir : stops) {
				cv.clear();
				cv.put(STOP_NUMBER, dir.number);
				cv.put(STOP_NAME, dir.name);
				cv.put(LATITUDE, dir.latitude);
				cv.put(LONGITUDE, dir.longitude);
				db.insertOrThrow (STOP_TABLE, null, cv);				
			}
			db.delete(LINK_TABLE, null, null);
			for (RouteStopLink link : links) {
				cv.clear();
				cv.put(ROUTE_NUMBER, link.routeNumber);
				cv.put(DIRECTION_NUMBER, link.directionNumber);
				cv.put(STOP_NUMBER, link.stopNumber);
				db.insertOrThrow (LINK_TABLE, null, cv);				
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
	
}
