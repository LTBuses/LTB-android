package org.frasermccrossan.ltc;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BusDbOpenHelper extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "ltcdb";
	
	static final String ROUTE_TABLE = "routes";
	static final String ROUTE_NUMBER = "route_number";
	static final String ROUTE_NAME = "route_name";
	
	static final String STOP_TABLE = "stops";
	static final String STOP_NUMBER = "stop_number";
	static final String STOP_NAME = "stop_name";
	static final String DIRECTION = "direction";
	static final String LATITUDE = "latitude";
	static final String LONGITUDE = "longitude";
	
	BusDbOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String s;
		s = String.format("CREATE TABLE %s (_id integer primary key, %s TEXT, %s TEXT)",
				ROUTE_TABLE, ROUTE_NUMBER, ROUTE_NAME);
		db.execSQL(s);
		s = String.format("CREATE TABLE %s (_id integer primary key, %s number, %s TEXT, %s TEXT, %s NUMBER, %s NUMBER)",
				STOP_TABLE, STOP_NUMBER, STOP_NAME, DIRECTION, LATITUDE, LONGITUDE);
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
