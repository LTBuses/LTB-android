package org.frasermccrossan.ltc;

import java.util.Calendar;
import java.util.HashMap;

import org.frasermccrossan.ltc.DownloadService.DownloadBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class UpdateDatabase extends Activity {
	
	LTCScraper scraper = null;
	ProgressBar progressBar;
	TextView freshnessText;
	TextView weekdayStops;
	TextView saturdayStops;
	TextView sundayStops;
	TextView ageLimit;
	TextView title;
	TextView message;
	Button updateButton;
	Button cancelButton;
	Button notWorkingButton;
	UpdateScrapingStatus scrapingStatus = null;
	DownloadService boundService = null;
	
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            DownloadBinder binder = (DownloadBinder) service;
            boundService = binder.getService();
            scrapingStatus = new UpdateScrapingStatus();
            boundService.setRemoteScrapeStatus(scrapingStatus);
            disableUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            boundService = null;
            if (scrapingStatus != null) {
            	scrapingStatus.update(null);
            }
            enableUI();
        }
    };

	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.update_database);

        progressBar = (ProgressBar)findViewById(R.id.progress);
        
        weekdayStops = (TextView)findViewById(R.id.weekday_stops);
        saturdayStops = (TextView)findViewById(R.id.saturday_stops);
        sundayStops = (TextView)findViewById(R.id.sunday_stops);
        ageLimit = (TextView)findViewById(R.id.age_limit);
        title = (TextView)findViewById(R.id.title);
        message = (TextView)findViewById(R.id.message);
        
        updateButton = (Button)findViewById(R.id.update_button);
        updateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent serviceIntent = new Intent(UpdateDatabase.this, DownloadService.class);
				startService(serviceIntent);
		        bindService(serviceIntent, connection, 0);
			}
		});

        cancelButton = (Button)findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (boundService != null) {
					boundService.cancel();
				}
			}
		});

        notWorkingButton = (Button)findViewById(R.id.not_working_button);
        notWorkingButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
		    	Intent diagnoseIntent = new Intent(UpdateDatabase.this, DiagnoseProblems.class);
		    	//LTCScraper scraper = new LTCScraper(UpdateDatabase.this, false);
		    	diagnoseIntent.putExtra("testurl", LTCScraper.ROUTE_URL);
		    	startActivity(diagnoseIntent);
			}
        });
          
    }
	
	@Override
	protected void onResume() {
		super.onResume();
        Intent intent = new Intent(this, DownloadService.class);
        bindService(intent, connection, 0);
        setFreshnesses();
	}
	
	@Override
	protected void onPause() {
		if (boundService != null) {
			unbindService(connection);
		}
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		if (scraper != null) {
			scraper.close();
		}
		connection = null;
		super.onDestroy();
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
	
	void disableUI() {
		updateButton.setVisibility(ProgressBar.GONE);
		cancelButton.setVisibility(ProgressBar.VISIBLE);
		progressBar.setVisibility(ProgressBar.VISIBLE);
	}
	
	void enableUI() {
		updateButton.setVisibility(ProgressBar.VISIBLE);
		cancelButton.setVisibility(ProgressBar.GONE);
		progressBar.setVisibility(ProgressBar.GONE);
	}
	
	void setFreshnesses() {
        BusDb db = new BusDb(this);
        Resources res = getResources();

        Calendar now = Calendar.getInstance();
        HashMap<String, Long> freshnesses = db.getFreshnesses(now.getTimeInMillis());
        int updateStatus = db.updateStatus(freshnesses, now);
        
        weekdayStops.setText(freshnessDays(freshnesses.get(BusDb.WEEKDAY_FRESHNESS_COLUMN), res));
        saturdayStops.setText(freshnessDays(freshnesses.get(BusDb.SATURDAY_FRESHNESS_COLUMN), res));
        sundayStops.setText(freshnessDays(freshnesses.get(BusDb.SUNDAY_FRESHNESS_COLUMN), res));
        ageLimit.setText(String.format(res.getString(R.string.age_limit),
        		freshnessDays(BusDb.UPDATE_DATABASE_AGE_LIMIT_HARD, res)));
        title.setText(res.getString(db.updateStrRes(updateStatus)));
        db.close();
	}

	class UpdateScrapingStatus implements ScrapingStatus {
		
		public void update(LoadProgress progress) {
			if (progress == null) {
				title.setText("");
				message.setText("");
				progressBar.setProgress(0);
			}
			else {
				title.setText(progress.title);
				message.setText(progress.message);
				progressBar.setProgress(progress.percent);
				if (progress.percent >= 100) {
					finish();
				}
				if (progress.completeEnough) {
					setFreshnesses();
				}
				if (progress.percent < 0) {
					notWorkingButton.setVisibility(Button.VISIBLE);
				}
			}
		}
				
	}
}
