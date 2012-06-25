package org.frasermccrossan.ltc;

import java.net.URL;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;

public class FindStop extends Activity {

	TextView status;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.find_stop);
        status = (TextView)findViewById(R.id.status);
        new LoadTask().execute();
    }
    
    private class LoadTask extends AsyncTask<Void, Void, String> {

    	protected String doInBackground(Void... thing) {
            LTCLoader loader = new LTCLoader(FindStop.this);
            return loader.loadAll();
        }

        protected void onPostExecute(String result) {
            status.setText(result);
        }
    }
}