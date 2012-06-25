package org.frasermccrossan.ltc;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/* although called BusDb, this is actually a helper class to abstract all the
 * database stuff into method calls
 */

public class BusDb {

	SQLiteDatabase db;
	
	BusDb(Context c) {
		BusDbOpenHelper helper = new BusDbOpenHelper(c);
		db = helper.getWritableDatabase();
	}
	
}
