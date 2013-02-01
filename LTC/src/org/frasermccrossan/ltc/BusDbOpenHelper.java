package org.frasermccrossan.ltc;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class BusDbOpenHelper extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 7;
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
			break;
		case 4:
			for (String col : new String[] {
					BusDb.WEEKDAY_LOCATION_FRESHNESS_COLUMN,
					BusDb.SATURDAY_LOCATION_FRESHNESS_COLUMN,
					BusDb.SUNDAY_LOCATION_FRESHNESS_COLUMN
					}) {
				s = String.format("alter table %s add column %s NUMBER NOT NULL default 0",
						BusDb.FRESHNESS_TABLE,
						col);
				db.execSQL(s);
			}
			break;
		case 5:
			s = String.format("update %s set %s = %s, %s = %s, %s = %s",
						BusDb.FRESHNESS_TABLE,
						BusDb.WEEKDAY_LOCATION_FRESHNESS_COLUMN, BusDb.WEEKDAY_FRESHNESS_COLUMN,
						BusDb.SATURDAY_LOCATION_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN,
						BusDb.SUNDAY_LOCATION_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN);
				db.execSQL(s);
			break;
		case 6:
			// sqlite can't rename columns so rename old, create new and copy
			db.beginTransaction();
			try {
				s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT UNIQUE ON CONFLICT REPLACE, %s TEXT, %s NUMBER NOT NULL, %s NUMBER NOT NULL, %s NUMBER NOT NULL)",
						BusDb.ROUTE_TABLE, BusDb.ROUTE_NUMBER, BusDb.ROUTE_NAME,
						BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN);
				updateTable3Freshness(db, BusDb.ROUTE_TABLE, s);
				s = String.format("CREATE TABLE %s (_id integer primary key, %s NUMBER UNIQUE ON CONFLICT REPLACE, %s TEXT, %s NUMBER, %s NUMBER, %s NUMBER NOT NULL, %s NUMBER NOT NULL, %s NUMBER NOT NULL)",
						BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.LATITUDE, BusDb.LONGITUDE,
						BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN);
				updateTable3Freshness(db, BusDb.STOP_TABLE, s);
				s = String.format("CREATE TABLE %s (_id integer primary key, %s NUMBER UNIQUE, %s TEXT UNIQUE ON CONFLICT REPLACE, %s NUMBER NOT NULL, %s NUMBER NOT NULL, %s NUMBER NOT NULL)",
						BusDb.DIRECTION_TABLE, BusDb.DIRECTION_NUMBER, BusDb.DIRECTION_NAME,
						BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN);
				updateTable3Freshness(db, BusDb.DIRECTION_TABLE, s);
				s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT, %s NUMBER, %s NUMBER, %s NUMBER NOT NULL, %s NUMBER NOT NULL, %s NUMBER NOT NULL, UNIQUE(%s, %s, %s) ON CONFLICT REPLACE)",
						BusDb.LINK_TABLE, BusDb.ROUTE_NUMBER, BusDb.DIRECTION_NUMBER, BusDb.STOP_NUMBER,
						BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN,
						BusDb.ROUTE_NUMBER, BusDb.DIRECTION_NUMBER, BusDb.STOP_NUMBER);
				updateTable3Freshness(db, BusDb.LINK_TABLE, s);
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
			break;
		case 7:
			/* this view should contain the same stuff that the stop table does, plus a count of
			 * recent usage
			 */
			s = String.format("DROP VIEW %s", BusDb.STOPS_WITH_USES);
			db.execSQL(s);
			s = String.format("CREATE VIEW %s as " +
					"select %s.%s, %s, %s, %s, %s, %s, %s, " +
					"count(%s) as %s " +
					"from %s left outer join %s " +
					"on %s.%s = %s.%s " +
					"group by 1, 2, 3, 4, 5",
					BusDb.STOPS_WITH_USES,
					BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.LATITUDE, BusDb.LONGITUDE,
					BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN,
					BusDb.STOP_LAST_USE_TIME, BusDb.STOP_USES_COUNT,
					BusDb.STOP_TABLE, BusDb.STOP_LAST_USE_TABLE,
					BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_LAST_USE_TABLE, BusDb.STOP_NUMBER);
			db.execSQL(s);
			break;
		default:
			// nothing yet
			break;
		}
	}

	private void updateTable3Freshness(SQLiteDatabase db, String table, String createSQL) {
		Log.i("ut3f", table);
		Log.i("ut3f", createSQL);
		String s;
		s = String.format("ALTER TABLE %s rename to foo", table);
		db.execSQL(s);
		db.execSQL(createSQL);
		s = String.format("INSERT INTO %s select *, 0, 0 from foo", table);
		db.execSQL(s);
		s = String.format("UPDATE %s SET %s = (SELECT %s from %s), %s = (SELECT %s from %s), %s = (SELECT %s from %s)",
				table,
				BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.WEEKDAY_FRESHNESS_COLUMN, BusDb.FRESHNESS_TABLE,
				BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.SATURDAY_FRESHNESS_COLUMN, BusDb.FRESHNESS_TABLE,
				BusDb.SUNDAY_FRESHNESS_COLUMN, BusDb.SUNDAY_FRESHNESS_COLUMN, BusDb.FRESHNESS_TABLE);
		db.execSQL(s);
		s = String.format("DROP TABLE foo");
		db.execSQL(s);
	}
//	private void upgrade1to2(SQLiteDatabase db) {
//	}

}
