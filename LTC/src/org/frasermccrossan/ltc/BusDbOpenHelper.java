package org.frasermccrossan.ltc;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BusDbOpenHelper extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "ltcdb";
	

	BusDbOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String s;
		s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT, %s TEXT)",
				BusDb.ROUTE_TABLE, BusDb.ROUTE_NUMBER, BusDb.ROUTE_NAME);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s number, %s TEXT, %s NUMBER, %s NUMBER)",
				BusDb.STOP_TABLE, BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.LATITUDE, BusDb.LONGITUDE);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s NUMBER, %s TEXT)",
				BusDb.DIRECTION_TABLE, BusDb.DIRECTION_NUMBER, BusDb.DIRECTION_NAME);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT, %s NUMBER, %s NUMBER)",
				BusDb.LINK_TABLE, BusDb.ROUTE_NUMBER, BusDb.DIRECTION_NUMBER, BusDb.STOP_NUMBER);
		db.execSQL(s);
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
		switch(version) {
		default:
			// nothing yet
			break;
		}
	}

//	private void upgrade1to2(SQLiteDatabase db) {
//	}

}
