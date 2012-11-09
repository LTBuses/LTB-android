package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class StopTimes extends Activity {

	// how long before popping up an Toast about how long the LTC site is taking
	static final long WEBSITE_DELAY = 5000;
	
	BusDb db;
	String stopNumber;
	LinearLayout routeViewLayout;
	Button refreshButton;
	LTCRoute[] routeList;
	RouteDirTextView[] routeViews;
	SimpleAdapter adapter;
	ListView predictionList;
	PredictionTask task = null;
	ArrayList<HashMap<String, String>> predictions;
	
	OnClickListener refreshListener = new OnClickListener() {
		public void onClick(View v) {
			if (v.getId() == R.id.refresh) {
				getPredictions();
			}
		}
	};
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stop_times);
        db = new BusDb(this);
                
        Intent intent = getIntent();
        stopNumber = intent.getStringExtra(BusDb.STOP_NUMBER);
        
        LTCStop stop = db.findStop(stopNumber);
        if (stop != null) {
        	setTitle(stop.name);
        	db.noteStopUse(stop.number);
        }
        
        refreshButton = (Button)findViewById(R.id.refresh);
        refreshButton.setOnClickListener(refreshListener);
        
        routeViewLayout = (LinearLayout)findViewById(R.id.route_list);        
        routeList = db.findStopRoutes(stopNumber);
        /* now create a list of route views based on that routeList */
        routeViews = new RouteDirTextView[routeList.length];
        int i;
        for (i = 0; i < routeList.length; ++i) {
        	routeViews[i] = new RouteDirTextView(this, routeList[i]);
        	routeViewLayout.addView(routeViews[i]);
        }
        
        predictionList = (ListView)findViewById(R.id.prediction_list);
        predictionList.setEmptyView(findViewById(R.id.empty_prediction_list));
		predictions = new ArrayList<HashMap<String,String>>(3);
		adapter = new SimpleAdapter(StopTimes.this,
       		 predictions,
       		 R.layout.prediction_item,
       		 new String[] { BusDb.CROSSING_TIME, BusDb.ROUTE_NUMBER, BusDb.DIRECTION_IMG_RES, BusDb.ROUTE_NAME, BusDb.DESTINATION, BusDb.RAW_TIME },
       		 new int[] { R.id.crossing_time, R.id.route_number, R.id.route_direction_img, R.id.route_long_name, R.id.destination, R.id.raw_crossing_time });
        predictionList.setAdapter(adapter);
	}
	
	@Override
	protected void onStart () {
		super.onStart();
        getPredictions();
	}
	
	@Override
	protected void onStop() {
		cancelTask();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		adapter = null;
		predictions = null;
		db.close();
		super.onDestroy();
	}
	
	void cancelTask() {
		if (task != null) {
			task.cancel(true); // we don't care if this fails because it has already stopped
			task = null;
		}
	}
	
	void getPredictions() {
		cancelTask();
		task = new PredictionTask();
		task.execute(routeViews);
	}
	
	/* this takes one or more LTCRoute objects and fetches the predictions for each
	 * one, publishing to the UI thread using publishProgress (somewhat counter-intuitively)
	 * and finally returns a void result since the list will have been updated by then
	 */
	class PredictionTask extends AsyncTask<RouteDirTextView, RouteDirTextView, Void> {
		
		Toast toast;
		Timer timer;
		
		class ToastTask extends TimerTask {
			public void run() {
				toast.show();
			}
		}
		
		@SuppressLint("ShowToast")
		protected void onPreExecute() {
			toast = Toast.makeText(StopTimes.this, R.string.website_slow, Toast.LENGTH_LONG);
			toast.setGravity(Gravity.CENTER, 0, 0);
			scheduleTimer();
		}
		
		protected Void doInBackground(RouteDirTextView... routeViews) {
			LTCScraper scraper = new LTCScraper(StopTimes.this);
			for (RouteDirTextView routeView: routeViews) {
				routeView.setStatus(RouteDirTextView.QUERYING, null);
				publishProgress(routeView);
				routeView.scrapePredictions(scraper, stopNumber);
				if (isCancelled()) {
					break;
				}
//				if (routeView.isOkToPost()) {
//					routeView.setState(RouteDirTextView.OK);
//				}
//				else {
//					routeView.setState(RouteDirTextView.FAILED);
//				}
				publishProgress(routeView);
			}
			return null;
		}
		
		protected void onProgressUpdate(RouteDirTextView... routeViews) {
			// update predictions and ping the listview
//			if (isCancelled()) {
//				return;
//			}
			cancelTimer();
			scheduleTimer();
			for (RouteDirTextView routeView: routeViews) {
				if (routeView.isOkToPost()) {
					removeRouteFromPredictions(routeView.route);
					predictions.addAll(routeView.getPredictions());
				}
				else {
					updatePredictionsWithMessageRes(routeView.route, routeView.msgResource());
				}
				Collections.sort(predictions, new PredictionComparator());
				adapter.notifyDataSetChanged();
				routeView.updateDisplay();
			}
		}
		
		@Override
		protected void onPostExecute(Void result) {
			cancelTimer();
		}
		
		@Override
		protected void onCancelled() {
			cancelTimer();
		}
		
		void scheduleTimer() {
			timer = new Timer();
			ToastTask task = new ToastTask();
			timer.schedule(task, WEBSITE_DELAY);
		}
		
		void cancelTimer() {
			timer.cancel();
			toast.cancel();
		}
		
		// removes all references to a particular route from the prediction list
		private void removeRouteFromPredictions(LTCRoute route) {
			int i = 0;
			while (i < predictions.size()) {
				HashMap<String, String> entry = predictions.get(i);
				if (entry.get(BusDb.ROUTE_INTERNAL_NUMBER).equals(route.number) &&
						entry.get(BusDb.DIRECTION_NAME).equals(route.directionName)) {
					predictions.remove(i);
				}
				else {
					++i;
				}
			}
		}
		
		// updates the times on a particular route with a given text resource
		private void updatePredictionsWithMessageRes(LTCRoute route, int strRes) {
			Resources res = getResources();
			String str = res.getString(strRes);
			int i = 0;
			while (i < predictions.size()) {
				HashMap<String, String> entry = predictions.get(i);
				if (entry.get(BusDb.ROUTE_INTERNAL_NUMBER).equals(route.number) &&
						entry.get(BusDb.DIRECTION_NAME).equals(route.directionName)) {
					entry.put(BusDb.CROSSING_TIME, str);
				}
				++i;
			}
		}
		
	}
	
	class PredictionComparator implements Comparator<Map<String, String>> {
		
	    public int compare(Map<String, String> first, Map<String, String> second) {
	    	String firstValue = first.get(BusDb.DATE_VALUE);
	    	String secondValue = second.get(BusDb.DATE_VALUE);
	    	return firstValue.compareTo(secondValue);
	    }

	}

}
