package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.TextView;

/* a sub-class of TextView that contains an LTC route and knows how to render it properly */

@SuppressLint("ViewConstructor")
public class RouteDirTextView extends TextView implements ScrapeStatus {

	public LTCRoute route = null;
	ArrayList<HashMap<String, String>> predictions = null;
	int defaultFlags;
	int state;
	String errorMessage;
	static final String VERY_CLOSE = ""; // something guaranteed to sort before everything
	static final String VERY_FAR_AWAY = "999999999999999"; // something guaranteed to sort after everything
	
	public RouteDirTextView(Context c, LTCRoute r) {
		super(c);
		route = r;
		setText(route.getShortRouteDirection());
		setPadding(0, 0, 3, 0);
		defaultFlags = getPaintFlags();
		state = IDLE;
		updateDisplay();
	}
	
	public void updateDisplay() {
		switch (state) {
		case IDLE:
			setPaintFlags(defaultFlags);
			setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
			break;
		case QUERYING:
			setPaintFlags(defaultFlags | Paint.UNDERLINE_TEXT_FLAG);
			setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
			break;
		case OK:
			setPaintFlags(defaultFlags);
			setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			break;
		case FAILED:
			setPaintFlags(defaultFlags | Paint.STRIKE_THRU_TEXT_FLAG);
			setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
			break;
		}
	}
	
	public int msgResource() {
		switch (state) {
		case IDLE:
			return R.string.msg_idle;
		case QUERYING:
			return R.string.msg_querying;
		case FAILED:
			return R.string.msg_fail;
		default:
			return 0;
		}
	}

	public void setStatus(int newState, String newMessage) {
		state = newState;
		errorMessage = newMessage;
	}
	
	public boolean isOkToPost() {
		return predictions != null && predictions.size() > 0;
	}
	
	public boolean shouldBeInvalidated() {
		return state == QUERYING;
	}
	
	public void scrapePredictions(LTCScraper scraper, String stopNumber) {
		predictions = null;
		predictions = scraper.getPredictions(route, stopNumber, this);
	}
	
	public ArrayList<HashMap<String,String>> getPredictions() {
		if (isOkToPost()) {
			return predictions;
		}
		else {
			return predictionSugar();
		}
	}
	
	// add visual sugar about a particular route (e.g. fetching, failed, etc.),
	// used when predictions aren't available
	ArrayList<HashMap<String, String>> predictionSugar() {
		Resources res = getResources();
		HashMap<String, String> sugar = new HashMap<String, String>(3);
		sugar.put(BusDb.ROUTE_NUMBER, route.getRouteNumber());
		sugar.put(BusDb.DIRECTION_NAME, route.directionName);
		sugar.put(BusDb.DESTINATION, route.directionName);
		switch (state) {
		case FAILED:
			sugar.put(BusDb.DATE_VALUE, VERY_FAR_AWAY);
			break;
		default:
			sugar.put(BusDb.DATE_VALUE, VERY_CLOSE);
			break;
		}
		sugar.put(BusDb.CROSSING_TIME, res.getString(msgResource()));
		ArrayList<HashMap<String, String>> arl = new ArrayList<HashMap<String, String>>(1);
		arl.add(sugar);
		return arl;
	}

}
