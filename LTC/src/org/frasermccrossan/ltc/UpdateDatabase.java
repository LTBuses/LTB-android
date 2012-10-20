package org.frasermccrossan.ltc;

import java.util.Calendar;
import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class UpdateDatabase extends Activity {
	
	ProgressBar progressBar;
	TextView freshnessText;
	TextView weekdayStatus;
	TextView saturdayStatus;
	TextView sundayStatus;
	TextView ageLimit;
	TextView status;
	Button updateButton;
	BusDb db;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.update_database);

        Resources res = getResources();

        db = new BusDb(this);

        progressBar = (ProgressBar)findViewById(R.id.progress);
        
        weekdayStatus = (TextView)findViewById(R.id.weekday_status);
        saturdayStatus = (TextView)findViewById(R.id.saturday_status);
        sundayStatus = (TextView)findViewById(R.id.sunday_status);
        ageLimit = (TextView)findViewById(R.id.age_limit);
        status = (TextView)findViewById(R.id.status_text);

        Calendar now = Calendar.getInstance();
        HashMap<Integer, Long> freshnesses = db.getFreshnesses(now.getTimeInMillis());
        int updateStatus = db.updateStatus(freshnesses, now);
        
        String statusFormat = res.getString(R.string.status_format);
        weekdayStatus.setText(String.format(statusFormat,
        		res.getString(R.string.weekday),
        		freshnessDays(freshnesses.get(BusDb.WEEKDAY_FRESHNESS), res)));
        saturdayStatus.setText(String.format(statusFormat,
        		res.getString(R.string.saturday),
        		freshnessDays(freshnesses.get(BusDb.SATURDAY_FRESHNESS), res)));
        sundayStatus.setText(String.format(statusFormat,
        		res.getString(R.string.sunday),
        		freshnessDays(freshnesses.get(BusDb.SUNDAY_FRESHNESS), res)));
        ageLimit.setText(String.format(res.getString(R.string.age_limit),
        		freshnessDays(BusDb.UPDATE_DATABASE_AGE_LIMIT, res)));
        status.setText(res.getString(db.updateStrRes(updateStatus)));
        
        updateButton = (Button)findViewById(R.id.update_button);
        updateButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				updateButton.setVisibility(ProgressBar.INVISIBLE);
				progressBar.setVisibility(ProgressBar.VISIBLE);
				LTCScraper scraper = new LTCScraper(UpdateDatabase.this, new UpdateStatus());
				scraper.loadAll();
			}
		});
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.update_database_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.update_database_help:
        	startActivity(new Intent(this, UpdateDatabaseHelp.class));
    		return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }


	private String freshnessDays(long freshnessMillis, Resources res) {
		long days = freshnessMillis / (1000L * 60L * 60L * 24L);
		if (days > 10000) {
			return res.getString(R.string.never);
		}
		if (days == 1) {
			return String.format(res.getString(R.string.day_ago), days);
		}
		return String.format(res.getString(R.string.days_ago), days);
	}

	class UpdateStatus implements ScrapingStatus {
		public void update(LoadProgress progress) {
			status.setText(progress.message);
			progressBar.setProgress(progress.percent);
			if (progress.percent >= 100) {
				finish();
			}
		}
	}
}
