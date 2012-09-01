package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.TextView;

/* a sub-class of TextView that contains an LTC route and knows how to render it properly */

@SuppressLint("ViewConstructor")
public class RouteDirTextView extends TextView implements ScrapeStatus {

	LTCRoute route = null;
	ArrayList<HashMap<String, String>> predictions = null;
	int defaultFlags;
	int state;
	String errorMessage;
	
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

	public void setStatus(int newState, String newMessage) {
		state = newState;
		errorMessage = newMessage;
	}
	
	public boolean isOkToPost() {
		return predictions != null && predictions.size() > 0;
	}
	
	public void scrapePredictions(LTCScraper scraper, String stopNumber) {
		predictions = null;
		predictions = scraper.getPredictions(route, stopNumber, this);
	}
	
	public ArrayList<HashMap<String,String>> getPredictions() {
		return predictions;
	}
}
