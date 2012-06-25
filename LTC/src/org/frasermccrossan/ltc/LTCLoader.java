package org.frasermccrossan.ltc;

import java.io.IOException;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.content.Context;

// everything required to load the LTC_supplied data into the database
public class LTCLoader {

	BusDb helper;
	static final String ROUTE_URL = "http://teuchter.lan:8000/map.html";
	static final String TEST_ROUTE_URL = "http://localhost:8000/route02.html";
	
	LTCLoader(Context c) {
		helper = new BusDb(c);
	}
	
	public String loadAll() {
		return loadRoutes();
	}
	
	public String loadRoutes() {
		String title;
		try {
			Connection conn = Jsoup.connect(ROUTE_URL);
			Document doc = conn.get();
			title = doc.title();
		} catch (IOException e) {
			title = e.toString();
		}
		return title;
	}
}
