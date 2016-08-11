package org.frasermccrossan.ltc;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

/* a sub-class of TextView that contains an LTC route and knows how to render it properly */

@SuppressLint("ViewConstructor")
public class RouteDirTextView extends TextView implements ScrapeStatus {

	public LTCRoute route = null;
	ArrayList<Prediction> predictions = null;
	Context context;
	int state;
	int problemType;
	String errorMessage;

	public RouteDirTextView(Context c, LTCRoute r) {
		super(c);
		context = c;
		route = r;
		setText(route.getShortRouteDirection());
		setPadding(2, 0, 2, 0);
		state = IDLE;
		updateDisplay();
	}
	
	public void updateDisplay() {
		switch (state) {
		case IDLE:
			setTextAppearance(context, R.style.route_idle);
			setBackgroundResource(R.color.bg_idle);
			break;
		case QUERYING:
			setTextAppearance(context, R.style.route_querying);
			setBackgroundResource(R.color.bg_querying);
			break;
		case OK:
			setTextAppearance(context, R.style.route_ok);
			setBackgroundResource(R.color.bg_ok);
			break;
		case FAILED:
			setTextAppearance(context, R.style.route_failed);
			setBackgroundResource(R.color.bg_failed);
			break;
		}
	}

	public void setStatus(int newState, String newMessage) {
		setStatus(newState, NOT_PROBLEM, newMessage);
	}
	
	public void setStatus(int newState, int newProbType, String newMessage) {
		state = newState;
		problemType = newProbType;
		errorMessage = newMessage;
	}

	public boolean isOkToPost() {
		return predictions != null && predictions.size() > 0;
	}

	public String getPredictionUrl(LTCScraper scraper, String stopNumber) {
		return scraper.ltcPredictionPath(route, stopNumber);
	}
	
	public void scrapePredictions(LTCScraper scraper, String stopNumber) {
		predictions = null;
		predictions = scraper.getPredictions(route, stopNumber, this);
	}
	
	public ArrayList<Prediction> getPredictions() {
		return predictions;
	}
	
}
