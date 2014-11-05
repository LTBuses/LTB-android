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
	int defaultFlags;
	int state;
	int problemType;
	String errorMessage;
	static final String VERY_CLOSE = ""; // something guaranteed to sort before everything
	static final String VERY_FAR_AWAY = "999999999999999"; // something guaranteed to sort after everything
	
	public RouteDirTextView(Context c, LTCRoute r) {
		super(c);
		context = c;
		route = r;
		setText(route.getShortRouteDirection());
		setPadding(2, 0, 2, 0);
//		LayoutParams layout = new LayoutParams(0, LayoutParams.WRAP_CONTENT);
//		layout.weight = 1.0f;
//		layout.gravity = Gravity.FILL_HORIZONTAL;
//		setLayoutParams(layout);
		state = IDLE;
		updateDisplay();
	}
	
	public void updateDisplay() {
		switch (state) {
		case IDLE:
			setTextAppearance(context, R.style.route_idle);
			setBackgroundResource(R.color.bg_idle);
			//setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
			break;
		case QUERYING:
			setTextAppearance(context, R.style.route_querying);
			setBackgroundResource(R.color.bg_querying);
			//setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
			break;
		case OK:
			setTextAppearance(context, R.style.route_ok);
			setBackgroundResource(R.color.bg_ok);
			//setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			break;
		case FAILED:
			setTextAppearance(context, R.style.route_failed);
			setBackgroundResource(R.color.bg_failed);
			//setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
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

	public boolean failed() {
		return state == FAILED;
	}
	
	public void setStatus(int newState, String newMessage) {
		setStatus(newState, NOT_PROBLEM, newMessage);
	}
	
	public void setStatus(int newState, int newProbType, String newMessage) {
		state = newState;
		problemType = newProbType;
		errorMessage = newMessage;
	}
	
	public boolean isImmediateProblem() {
		return problemType == PROBLEM_IMMEDIATELY;
	}
	
	public boolean isProbIfAll() {
		return problemType == PROBLEM_IF_ALL;		
	}
	
	public boolean isOkToPost() {
		return predictions != null && predictions.size() > 0;
	}
	
	public boolean shouldBeInvalidated() {
		return state == QUERYING;
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
