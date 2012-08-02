package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class StopTimes extends Activity {

	BusDb db;
	String stopNumber;
	TextView routeListView;
	LTCRoute[] routeList;
	SimpleAdapter adapter;
	ListView predictionList;
	PredictionTask task = null;
	ArrayList<HashMap<String, String>> predictions;
	
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
        }
        
        routeListView = (TextView)findViewById(R.id.route_list);        
        routeList = db.findStopRoutes(stopNumber);
        String atThisStop = getString(R.string.at_this_stop);
        routeListView.setText(String.format(atThisStop, TextUtils.join(" ", routeList)));
        
        predictionList = (ListView)findViewById(R.id.prediction_list);
	}
	
	@Override
	protected void onStart () {
		super.onStart();
		predictions = new ArrayList<HashMap<String,String>>(3);
		adapter = new SimpleAdapter(StopTimes.this,
       		 predictions,
       		 R.layout.prediction_item,
       		 new String[] { BusDb.CROSSING_TIME, BusDb.ROUTE_NUMBER, BusDb.DESTINATION },
       		 new int[] { R.id.crossing_time, R.id.route_number, R.id.destination });
        predictionList.setAdapter(adapter);
        getPredictions();
	}
	
	@Override
	protected void onStop() {
		if (task != null) {
			task.cancel(true); // we don't care if this fails because it has already stopped
			task = null;
		}
		adapter = null;
		predictions = null;
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		db.close();
		super.onDestroy();
	}
	
	void getPredictions() {
		predictions.clear();
		task = new PredictionTask();
		task.execute(routeList);
	}
	
	/* so this takes one or more LTCRoute objects and fetches the predictions for each
	 * one, publishing to the UI thread using publishProgress (somewhat counter-intuitively)
	 * and finally returns a void result since the list will have been updated by then
	 */
	class PredictionTask extends AsyncTask<LTCRoute, ArrayList<HashMap<String,String>>, Void> {
		
		protected Void doInBackground(LTCRoute... routes) {
			LTCScraper scraper = new LTCScraper();
			for (LTCRoute route: routes) {
				ArrayList<HashMap<String, String>> routePreds = scraper.getPredictions(route, stopNumber);
				publishProgress(routePreds);
			}
			return null;
		}
		
		protected void onProgressUpdate(ArrayList<HashMap<String,String>>... predictionMaps) {
			// update predictions and ping the listview
			for (ArrayList<HashMap<String,String>> predMap: predictionMaps) {
				predictions.addAll(predMap);
				Collections.sort(predictions, new PredictionComparator());
			}
			adapter.notifyDataSetChanged();
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
