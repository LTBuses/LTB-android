package org.frasermccrossan.ltc;

import java.util.Collection;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

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

	SQLiteDatabase db;
	
	BusDb(Context c) {
		BusDbOpenHelper helper = new BusDbOpenHelper(c);
		db = helper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
	}
	
	// called by the scraper to load everything it found into the database
	public void saveBusData(Collection<LTCRoute> routes,
			Collection<LTCDirection> directions,
			Collection<LTCStop> stops,
			Collection<RouteStopLink> links) throws SQLException {
		db.beginTransaction();
		try {
			ContentValues cv = new ContentValues(5); // 5 should deal with everything
			for (LTCRoute route : routes) {
				cv.clear();
				cv.put(ROUTE_NUMBER, route.number);
				cv.put(ROUTE_NAME, route.name);
				db.insertOrThrow (ROUTE_TABLE, null, cv);
			}
			for (LTCDirection dir : directions) {
				cv.clear();
				cv.put(DIRECTION_NUMBER, dir.number);
				cv.put(DIRECTION_NAME, dir.name);
				db.insertOrThrow (DIRECTION_TABLE, null, cv);				
			}
			for (LTCStop dir : stops) {
				cv.clear();
				cv.put(STOP_NUMBER, dir.number);
				cv.put(STOP_NAME, dir.name);
				cv.put(LATITUDE, dir.latitude);
				cv.put(LONGITUDE, dir.longitude);
				db.insertOrThrow (STOP_TABLE, null, cv);				
			}
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
