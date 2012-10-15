package org.frasermccrossan.ltc;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BusDbOpenHelper extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 3;
	private static final String DATABASE_NAME = "ltcdb";
	

	BusDbOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String s;
		s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT UNIQUE ON CONFLICT REPLACE, %s TEXT, %s NUMBER NOT NULL)",
				BusDb.ROUTE_TABLE, BusDb.ROUTE_NUMBER, BusDb.ROUTE_NAME, BusDb.FRESHNESS);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s NUMBER UNIQUE ON CONFLICT REPLACE, %s TEXT, %s NUMBER, %s NUMBER, %s NUMBER NOT NULL)",
				BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.LATITUDE, BusDb.LONGITUDE,
				BusDb.FRESHNESS);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s NUMBER UNIQUE, %s TEXT UNIQUE ON CONFLICT REPLACE, %s NUMBER NOT NULL)",
				BusDb.DIRECTION_TABLE, BusDb.DIRECTION_NUMBER, BusDb.DIRECTION_NAME, BusDb.FRESHNESS);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT, %s NUMBER, %s NUMBER, %s NUMBER NOT NULL, UNIQUE(%s, %s, %s) ON CONFLICT REPLACE)",
				BusDb.LINK_TABLE, BusDb.ROUTE_NUMBER, BusDb.DIRECTION_NUMBER, BusDb.STOP_NUMBER, BusDb.FRESHNESS,
				BusDb.ROUTE_NUMBER, BusDb.DIRECTION_NUMBER, BusDb.STOP_NUMBER);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (%s NUMBER NOT NULL, %s NUMBER NOT NULL, %s NUMBER NOT NULL)",
				BusDb.FRESHNESS_TABLE, BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN);
		db.execSQL(s);
		// init this table with zeros
		ContentValues cv = new ContentValues(3);
		cv.put(BusDb.WEEKDAY_FRESHNESS_COLUMN, 0);
		cv.put(BusDb.SATURDAY_FRESHNESS_COLUMN, 0);
		cv.put(BusDb.SUNDAY_FRESHNESS_COLUMN, 0);
		db.insertOrThrow(BusDb.FRESHNESS_TABLE, null, cv);
		s = String.format("CREATE UNIQUE INDEX route_index ON %s ( %s )", BusDb.ROUTE_TABLE, BusDb.ROUTE_NUMBER);
		db.execSQL(s);
		s = String.format("CREATE UNIQUE INDEX stop_index ON %s ( %s )", BusDb.STOP_TABLE, BusDb.STOP_NUMBER);
		db.execSQL(s);
		// for first-time users
		onUpgrade(db, 1, DATABASE_VERSION);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int v1, int v2) {
		int v;
		for (v = v1 + 1; v <= v2; ++v) {
			upgradeTo(db, v);
		}
	}

	private void upgradeTo(SQLiteDatabase db, int version) {
		String s;
		switch(version) {
		case 2:
			// after first alpha testing release
			s = String.format("CREATE TABLE %s (%s NUMBER NOT NULL, %s NUMBER NOT NULL)",
					BusDb.STOP_LAST_USE_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_LAST_USE_TIME);
			db.execSQL(s);
			break;
		case 3:
			/* this view should contain the same stuff that the stop table does, plus a count of
			 * recent usage
			 */
			s = String.format("CREATE VIEW %s as " +
					"select %s.%s, %s, %s, %s, %s, " +
					"count(%s) as %s " +
					"from %s left outer join %s " +
					"on %s.%s = %s.%s " +
					"group by 1, 2, 3, 4, 5",
					BusDb.STOPS_WITH_USES,
					BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.LATITUDE, BusDb.LONGITUDE, BusDb.FRESHNESS,
					BusDb.STOP_LAST_USE_TIME, BusDb.STOP_USES_COUNT,
					BusDb.STOP_TABLE, BusDb.STOP_LAST_USE_TABLE,
					BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_LAST_USE_TABLE, BusDb.STOP_NUMBER);
			db.execSQL(s);
		default:
			// nothing yet
			break;
		}
	}

//	private void upgrade1to2(SQLiteDatabase db) {
//	}

}
