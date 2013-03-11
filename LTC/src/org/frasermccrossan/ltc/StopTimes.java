package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Toast;

public class StopTimes extends Activity {

	// how long before popping up an Toast about how long the LTC site is taking
	static final long WARNING_DELAY = 10000;
	
	String stopNumber;
	LinearLayout routeViewLayout;
	ScrollView vertRouteViewScrollview;
	HorizontalScrollView horizRouteViewScrollview;
	Button refreshButton;
	Button notWorkingButton;
	Button tweakButton;
	ArrayList<LTCRoute> routeList;
	ArrayList<RouteDirTextView> routeViews;
	PredictionAdapter adapter;
	ListView predictionList;
	PredictionTask task = null;
	ArrayList<Prediction> predictions;
	
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
        vertRouteViewScrollview = (ScrollView)findViewById(R.id.vert_route_list_scrollview);        
        horizRouteViewScrollview = (HorizontalScrollView)findViewById(R.id.horiz_route_list_scrollview);        

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
        	predictions = new ArrayList<Prediction>(3);
//        	adapter = new SimpleAdapter(StopTimes.this,
//        			predictions,
//        			R.layout.prediction_item,
//        			new String[] { BusDb.CROSSING_TIME, BusDb.ROUTE_NUMBER, BusDb.DIRECTION_IMG_RES, BusDb.ROUTE_NAME, BusDb.DESTINATION, BusDb.TEXT_TIME },
//        			new int[] { R.id.crossing_time, R.id.route_number, R.id.route_direction_img, R.id.route_long_name, R.id.destination, R.id.raw_crossing_time });
        	adapter = new PredictionAdapter(this, R.layout.prediction_item, predictions);
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			/* on Honeycomb and later, ASyncTasks run on a serial executor, and since
			 * we might have another asynctask running in an activity (e.g. fetching stop lists),
			 * we don't really want them all to block
			 */
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					(RouteDirTextView[])(routeViews.toArray(routeViewAry)));
		}
		else {
			task.execute((RouteDirTextView[])(routeViews.toArray(routeViewAry)));
		}
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
			for (RouteDirTextView routeView: routeViews) {
				routeView.setStatus(RouteDirTextView.IDLE, null);
				routeView.updateDisplay();
			}
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
			Calendar now = Calendar.getInstance();
			for (RouteDirTextView routeView: routeViews) {
				if (isCancelled()) {
					break;
				}
				if (routeView.isOkToPost()) {
					removeRouteFromPredictions(routeView.route);
					for (Prediction p: routeView.getPredictions()) {
						// find the position where this Prediction should be inserted
						int insertPosition = Collections.binarySearch(predictions, p);
						// we don't care if we get a direct hit or just an insert position, we do the same thing
						if (insertPosition < 0) {
							insertPosition = -(insertPosition + 1);
						}
						// insert the Prediction at that location
						adapter.insert(p, insertPosition);
//						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//							if ((insertPosition >= predictionList.getFirstVisiblePosition() &&
//									insertPosition <= predictionList.getLastVisiblePosition()) ||
//									predictionList.getCount() == 0) {
//								Animation appear = AnimationUtils.loadAnimation(StopTimes.this, R.anim.prediction_in);
//								View row = (View)predictionList.getChildAt(insertPosition);
//								row.setAnimation(appear);
//							}
//						}
					}
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
				for (Prediction p: predictions) {
					p.updateFields(StopTimes.this, now);
				}
				//adapter.sort(new PredictionComparator());
				adapter.notifyDataSetChanged();
				routeView.updateDisplay();
				if (isCancelled()) {
					break;
				}
				if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT &&
						horizRouteViewScrollview != null) {
					int right = routeView.getRight();
					int svWidth = horizRouteViewScrollview.getWidth();
					if (right > svWidth) {
						horizRouteViewScrollview.smoothScrollTo(right - svWidth, 0);
					}
					else {
						int left = routeView.getLeft();
						int scrollPos = horizRouteViewScrollview.getScrollX();
						if (left < scrollPos) {
							horizRouteViewScrollview.smoothScrollTo(left, 0);						
						}
					}
				}
				else if (vertRouteViewScrollview != null) {
					int bottom = routeView.getBottom();
					int svHeight = vertRouteViewScrollview.getHeight();
					if (bottom > svHeight) {
						vertRouteViewScrollview.smoothScrollTo(0, bottom - svHeight);
					}
					else {
						int top = routeView.getTop();
						int scrollPos = vertRouteViewScrollview.getScrollY();
						if (top < scrollPos) {
							vertRouteViewScrollview.smoothScrollTo(top, 0);
						}
					}
				}
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
			while (i < adapter.getCount()) {
				Prediction entry = predictions.get(i);
				if (entry.isOnRoute(route)) {
					adapter.remove(entry);
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
				Prediction entry = predictions.get(i);
				if (entry.isOnRoute(route)) {
					entry.setQuerying();
				}
				++i;
			}
		}
		
	}

}
