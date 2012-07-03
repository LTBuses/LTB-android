package org.frasermccrossan.ltc;

import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class FindStop extends Activity {
	
	EditText searchField;
	ListView stopList;
	SearchTask mySearchTask = null;
	BusDb db;
	
	OnItemClickListener stopListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			TextView stopNumberView = (TextView)view.findViewById(R.id.stop_number);
			String stopNumber = stopNumberView.getText().toString();
	    	Intent stopTimeIntent = new Intent(FindStop.this, StopTimes.class);
	    	stopTimeIntent.putExtra(BusDb.STOP_NUMBER, stopNumber);
	    	startActivity(stopTimeIntent);    	
		}
	};
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.find_stop);
        searchField = (EditText)findViewById(R.id.search);
        searchField.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
        		updateStops(s);
        	}
        	
        	// don't care
        	public void	beforeTextChanged(CharSequence s, int start, int count, int after) {}
        	public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        
        stopList = (ListView)findViewById(R.id.stop_list);
        stopList.setOnItemClickListener(stopListener);
        db = new BusDb(this);
    }
	
	@Override
	protected void onStart() {
		super.onStart();
        if (!db.isValid()) {
	    	Intent updateDatabaseIntent = new Intent(FindStop.this, UpdateDatabase.class);
	    	startActivity(updateDatabaseIntent);    	
        }
        else {
        	updateStops(searchField.getText());
        }
	}
    
	@Override
	protected void onDestroy() {
		db.close();
		super.onDestroy();
	}
	
	public void updateStops(CharSequence searchText) {
		if (mySearchTask != null && ! mySearchTask.isCancelled()) {
			mySearchTask.cancel(true);
		}
		mySearchTask = new SearchTask();
		mySearchTask.execute(searchText);

	}
	
	class SearchTask extends AsyncTask<CharSequence, Void, List<HashMap<String, String>>> {
		
		protected List<HashMap<String, String>> doInBackground(CharSequence... strings) {
			return db.findStops(strings[0]);
	     }

	     protected void onPostExecute(List<HashMap<String, String>> result) {
	    	 
	         SimpleAdapter adapter = new SimpleAdapter(FindStop.this,
	        		 result,
	        		 R.layout.stop_list_item,
	        		 new String[] { BusDb.STOP_NUMBER, BusDb.STOP_NAME },
	        		 new int[] { R.id.stop_number, R.id.stop_name });
	         stopList.setAdapter(adapter);
	     }
	}
}