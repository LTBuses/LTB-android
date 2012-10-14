package org.frasermccrossan.ltc;

import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class FindStop extends Activity {
	
	EditText searchField;
	ListView stopList;
	ImageView locationImage;
	LocationManager myLocationManager;
	String locProvider = null;
	Location lastLocation;
	SearchTask mySearchTask = null;
	BusDb db;
	int downloadTry;
	
	OnItemClickListener stopListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			TextView stopNumberView = (TextView)view.findViewById(R.id.stop_number);
			String stopNumber = stopNumberView.getText().toString();
	    	Intent stopTimeIntent = new Intent(FindStop.this, StopTimes.class);
	    	stopTimeIntent.putExtra(BusDb.STOP_NUMBER, stopNumber);
	    	startActivity(stopTimeIntent);    	
		}
	};
	
	LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			lastLocation= location;
			String provider = lastLocation.getProvider();
			if (provider.equals(LocationManager.GPS_PROVIDER)) {
				locationImage.setImageResource(R.drawable.ic_action_satellite_location);
			}
			else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
				locationImage.setImageResource(R.drawable.ic_action_antenna_location);
			}
			else {
				locationImage.setImageResource(R.drawable.ic_action_no_location);
			}
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
        stopList.setOnItemClickListener(stopListener);
        locationImage = (ImageView) findViewById(R.id.location_status);
        db = new BusDb(this);
        downloadTry = 0;
    }
	
	@Override
	protected void onStart() {
		super.onStart();
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		locProvider = myLocationManager.getBestProvider(criteria, true);
		if (locProvider != null) {
			myLocationManager.requestLocationUpdates(locProvider, 10 * 1000, 0, locationListener);
			//myLocationManager.requestSingleUpdate(locProvider, locationListener, null);
		}
		int updateStatus = db.updateStatus();
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
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    
	public void updateStops() {
		if (mySearchTask != null && ! mySearchTask.isCancelled()) {
			mySearchTask.cancel(true);
		}
		mySearchTask = new SearchTask();
		mySearchTask.execute(searchField.getText());

	}
	
	class SearchTask extends AsyncTask<CharSequence, Void, List<HashMap<String, String>>> {
		
		protected List<HashMap<String, String>> doInBackground(CharSequence... strings) {
			return db.findStops(strings[0], lastLocation);
	     }

	     protected void onPostExecute(List<HashMap<String, String>> result) {
	    	 
	         SimpleAdapter adapter = new SimpleAdapter(FindStop.this,
	        		 result,
	        		 R.layout.stop_list_item,
	        		 new String[] { BusDb.STOP_NUMBER, BusDb.STOP_NAME },
	        		 new int[] { R.id.stop_number, R.id.stop_name });
	         if (!isCancelled() && stopList != null) {
	        	 stopList.setAdapter(adapter);
	         }
	     }
	}
}