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
import android.opengl.Visibility;
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
	static final long WARNING_DELAY = 5000;
	static final long BUTTON_DELAY = 100;
	
	String stopNumber;
	LinearLayout routeViewLayout;
	Button refreshButton;
	Button notWorkingButton;
	ArrayList<LTCRoute> routeList;
	ArrayList<RouteDirTextView> routeViews;
	SimpleAdapter adapter;
	ListView predictionList;
	PredictionTask task = null;
	ArrayList<HashMap<String, String>> predictions;
	
	OnClickListener buttonListener = new OnClickListener() {
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.refresh:
				getPredictions();
				break;
			case R.id.not_working:
		    	Intent diagnoseIntent = new Intent(StopTimes.this, DiagnoseProblems.class);
		    	LTCScraper scraper = new LTCScraper(StopTimes.this);
		    	diagnoseIntent.putExtra("testurl", routeViews.get(0).getPredictionUrl(scraper, stopNumber));
		    	startActivity(diagnoseIntent);
		    	break;
				// no default
			}
		}
	};
		
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stop_times);
        BusDb db = new BusDb(this);
                
        Intent intent = getIntent();
        stopNumber = intent.getStringExtra(BusDb.STOP_NUMBER);
        
        LTCStop stop = db.findStop(stopNumber);
        if (stop != null) {
        	setTitle(stop.name);
        	db.noteStopUse(stop.number);
        }
        
        refreshButton = (Button)findViewById(R.id.refresh);
        refreshButton.setOnClickListener(buttonListener);
        notWorkingButton = (Button)findViewById(R.id.not_working);
        notWorkingButton.setOnClickListener(buttonListener);
        
        routeViewLayout = (LinearLayout)findViewById(R.id.route_list);        

        String routeNumberOnly = intent.getStringExtra(BusDb.ROUTE_NUMBER);
        int routeDirectionOnly = intent.getIntExtra(BusDb.DIRECTION_NUMBER, 0);
        routeList = db.findStopRoutes(stopNumber, routeNumberOnly, routeDirectionOnly);
        db.close();

        if (routeList.size() == 0) {
        	Toast.makeText(StopTimes.this, R.string.none_stop_today, Toast.LENGTH_SHORT).show();
        	finish();
        }
        else {
        	/* now create a list of route views based on that routeList */
        	routeViews = new ArrayList<RouteDirTextView>(routeList.size());
        	for (LTCRoute route : routeList) {
        		RouteDirTextView routeView = new RouteDirTextView(this, route);
        		routeViews.add(routeView);
        		routeViewLayout.addView(routeView);
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
		notWorkingButton.setVisibility(Button.GONE);
		task = new PredictionTask();
		RouteDirTextView[] routeViewAry = new RouteDirTextView[routeViews.size()];
		task.execute((RouteDirTextView[])(routeViews.toArray(routeViewAry)));
	}
	
	/* this takes one or more LTCRoute objects and fetches the predictions for each
	 * one, publishing to the UI thread using publishProgress (somewhat counter-intuitively)
	 * and finally returns a void result since the list will have been updated by then
	 */
	class PredictionTask extends AsyncTask<RouteDirTextView, RouteDirTextView, Void> {
		
		Toast toast;
		Timer toastTimer;
		int probCount = 0;
		
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
				publishProgress(routeView);
			}
			return null;
		}
		
		protected void onProgressUpdate(RouteDirTextView... routeViews) {
			// update predictions and ping the listview
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
				switch (routeView.problemType) {
				case ScrapeStatus.PROBLEM_IMMEDIATELY:
					notWorkingButton.setVisibility(Button.VISIBLE);
					break;
				case ScrapeStatus.PROBLEM_IF_ALL:
					++probCount;
					break;
				}
				Collections.sort(predictions, new PredictionComparator());
				adapter.notifyDataSetChanged();
				routeView.updateDisplay();
			}
		}
		
		@Override
		protected void onPostExecute(Void result) {
			if (probCount == routeViews.size()) {
				notWorkingButton.setVisibility(Button.VISIBLE);
			}
			cancelTimer();
		}
		
		@Override
		protected void onCancelled() {
			cancelTimer();
		}
		
		void scheduleTimer() {
			toastTimer = new Timer();
			ToastTask toastTask = new ToastTask();
			toastTimer.schedule(toastTask, WARNING_DELAY);
		}
		
		void cancelTimer() {
			toastTimer.cancel();
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
