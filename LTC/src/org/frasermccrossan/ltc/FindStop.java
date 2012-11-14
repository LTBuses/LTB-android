package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class FindStop extends Activity {

	EditText searchField;
	ListView stopList;
	TextView emptyStopListText;
	SimpleAdapter stopListAdapter;
	List<HashMap<String, String>> stops;
	Spinner routeSpinner;
	LTCRoute[] routes;
	Spinner searchTypeSpinner;
	LocationManager myLocationManager;
	String locProvider = null;
	Location lastLocation;
	SearchTask mySearchTask = null;
	BusDb db;
	int downloadTry;

	// entries in R.array.search_types
	static final int RECENT_STOPS = 0;
	static final int CLOSEST_STOPS = 1;

	static final long LOCATION_TIME_UPDATE = 30; // seconds between GPS update
	static final float LOCATION_DISTANCE_UPDATE = 100; // minimum metres between GPS updates

	// context menu items
	static final int SHOW_MAP = 0;
	static final int FORGET_FAVOURITE = 1;

	OnItemClickListener stopListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			TextView stopNumberView = (TextView)view.findViewById(R.id.stop_number);
			String stopNumber = stopNumberView.getText().toString();
			Intent stopTimeIntent = new Intent(FindStop.this, StopTimes.class);
			stopTimeIntent.putExtra(BusDb.STOP_NUMBER, stopNumber);
			startActivity(stopTimeIntent);
		}
	};

	OnItemSelectedListener searchTypeListener = new OnItemSelectedListener() {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			setLocationUpdates();
			updateStops();
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// nothing

		}
	};

	public class RouteAdapter extends ArrayAdapter<LTCRoute> {

		Context c;

		RouteAdapter(Context context, int textViewResource, LTCRoute[] routes) {
			super(context, textViewResource, routes);
			c = context;
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			return getCustomView(position, false, android.R.layout.simple_spinner_dropdown_item, parent);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return getCustomView(position, true, android.R.layout.simple_spinner_item, parent);
		}

		public View getCustomView(int position, boolean shortForm, int layoutId, ViewGroup parent) {
			LayoutInflater inflater = getLayoutInflater();
			View row=inflater.inflate(layoutId, parent, false);
			TextView label=(TextView)row.findViewById(android.R.id.text1);
			if (routes[position] == null) {
				label.setText(shortForm? R.string.all_routes_short : R.string.all_routes_long);
			}
			else {
				label.setText(shortForm? routes[position].getRouteNumber() : routes[position].getNameWithNumber());
			}
			return row;
		}

	}

	LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			lastLocation = location;
			updateStops();
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {}

		public void onProviderEnabled(String provider) {}

		public void onProviderDisabled(String provider) {}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.find_stop);
		db = new BusDb(this);
		searchField = (EditText)findViewById(R.id.search);
		searchField.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				updateStops();
			}

			// don't care
			public void	beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
		myLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		stopList = (ListView)findViewById(R.id.stop_list);
		emptyStopListText = (TextView)findViewById(R.id.empty_stop_list);
		emptyStopListText.setText(R.string.searching_for_stops);
		stopList.setEmptyView(findViewById(R.id.empty_stop_list));
		stopList.setOnItemClickListener(stopListener);
		stops = new ArrayList<HashMap<String, String>>();
		stopListAdapter = new SimpleAdapter(FindStop.this,
				stops,
				R.layout.stop_list_item,
				new String[] { BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.ROUTE_LIST/*, BusDb.DISTANCE_TEXT */},
				new int[] { R.id.stop_number, R.id.stop_name, R.id.route_list/*, R.id.distance */});
		stopList.setAdapter(stopListAdapter);
		searchTypeSpinner = (Spinner)findViewById(R.id.search_type_spinner);
		searchTypeSpinner.setOnItemSelectedListener(searchTypeListener);
		routes = db.getAllRoutes(true);
		routeSpinner = (Spinner)findViewById(R.id.route_spinner);
		RouteAdapter routeAdapter = new RouteAdapter(this, R.layout.route_view, routes);
		routeSpinner.setAdapter(routeAdapter);
		registerForContextMenu(stopList);
		downloadTry = 0;
	}

	@Override
	protected void onStart() {
		super.onStart();
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		locProvider = LocationManager.GPS_PROVIDER;
		if (locProvider != null) {
			if (myLocationManager.isProviderEnabled(locProvider) && db.getLocationUpdateStatus() != BusDb.UPDATE_REQUIRED) {
				searchTypeSpinner.setEnabled(true);
				//myLocationManager.requestSingleUpdate(locProvider, locationListener, null);
			}
			else {
				searchTypeSpinner.setSelection(RECENT_STOPS);
				searchTypeSpinner.setEnabled(false);
			}
		}
		setLocationUpdates();
		int updateStatus = db.getUpdateStatus();
		if (updateStatus != BusDb.UPDATE_NOT_REQUIRED) {
			++downloadTry;
			if (downloadTry <= 1) {
				Intent updateDatabaseIntent = new Intent(FindStop.this, UpdateDatabase.class);
				startActivity(updateDatabaseIntent);
			}
			else if (updateStatus == BusDb.UPDATE_REQUIRED) {
				finish();
			}
			updateStops();
		}
		else {
			updateStops();
		}
	}

	@Override
	protected void onStop() {
		myLocationManager.removeUpdates(locationListener);
		if (mySearchTask != null && ! mySearchTask.isCancelled()) {
			mySearchTask.cancel(true);
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		db.close();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.about:
			startActivity(new Intent(this, About.class));
			return true;
		case R.id.update_database:
			Intent updateDatabaseIntent = new Intent(FindStop.this, UpdateDatabase.class);
			startActivity(updateDatabaseIntent);
			return true;
		case R.id.find_stop_help:
			startActivity(new Intent(this, FindStopHelp.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterView.AdapterContextMenuInfo listItemInfo = (AdapterView.AdapterContextMenuInfo)menuInfo;
		int item = listItemInfo.position;
		menu.add(ContextMenu.NONE, SHOW_MAP, 1, R.string.show_map);
		if (Integer.valueOf(stops.get(item).get(BusDb.STOP_USES_COUNT)) > 0) {
			menu.add(ContextMenu.NONE, FORGET_FAVOURITE, 2, R.string.forget_favourite);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case SHOW_MAP:
			HashMap<String, String> stop = stops.get(info.position);
			String query = Uri.encode(String.format("%s@%s,%s",
					stop.get(BusDb.STOP_NAME), 
					stop.get(BusDb.LATITUDE), stop.get(BusDb.LONGITUDE)
					));
			String geoUri = String.format("geo:%s,%s?q=%s",
					stop.get(BusDb.LATITUDE), stop.get(BusDb.LONGITUDE),
					query);
			Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
			startActivity(mapIntent);
			return true;
		case FORGET_FAVOURITE:
			db.forgetStopUse(Integer.valueOf(stops.get(info.position).get(BusDb.STOP_NUMBER)));
			updateStops();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	public void setLocationUpdates()
	{
		switch (searchTypeSpinner.getSelectedItemPosition()) {
		case RECENT_STOPS:
			myLocationManager.removeUpdates(locationListener);
			lastLocation = null;
			break;
		case CLOSEST_STOPS:
			myLocationManager.requestLocationUpdates(locProvider, LOCATION_TIME_UPDATE * 1000, LOCATION_DISTANCE_UPDATE, locationListener);
			lastLocation = myLocationManager.getLastKnownLocation(locProvider);
			break;
		default:
			// nothing
			break;
		}
	}

	public void updateStops() {
		if (mySearchTask != null && ! mySearchTask.isCancelled()) {
			mySearchTask.cancel(true);
		}
		mySearchTask = new SearchTask();
		mySearchTask.execute(searchField.getText());

	}

	class SearchTask extends AsyncTask<CharSequence, List<HashMap<String, String>>, Void> {

		protected void onPreExecute() {
			emptyStopListText.setText(R.string.searching_for_stops);
		}

		@SuppressWarnings("unchecked")
		protected Void doInBackground(CharSequence... strings) {
			List<HashMap<String, String>> newStops = db.findStops(strings[0], lastLocation);
			if (!isCancelled()) {
				publishProgress(newStops);
			}
			db.addRoutesToStopList(newStops);
			if (!isCancelled()) {
				// can publish a null since the above update updates all the same objects
				publishProgress((List<HashMap<String, String>>) null);
			}
			return null;
		}

		protected void onProgressUpdate(List<HashMap<String, String>>... newStops) {
			if (!isCancelled() && stopListAdapter != null) {
				for (List<HashMap<String, String>> newStop: newStops) {
					if (newStop != null) {
						stops.clear();
						stops.addAll(newStop);
					}
				}
				stopListAdapter.notifyDataSetChanged();
				emptyStopListText.setText(R.string.no_stops_found);
			}
		}
	}
}
