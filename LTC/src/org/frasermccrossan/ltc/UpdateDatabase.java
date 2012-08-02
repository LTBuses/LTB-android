package org.frasermccrossan.ltc;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class UpdateDatabase extends Activity {
	
	ProgressBar progressBar;
	TextView statusText;
	Button updateButton;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.update_database);

        progressBar = (ProgressBar)findViewById(R.id.progress);
        
        statusText = (TextView)findViewById(R.id.status_text);
        
        updateButton = (Button)findViewById(R.id.update_button);
        updateButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				updateButton.setEnabled(false);
				LTCScraper scraper = new LTCScraper(UpdateDatabase.this, new UpdateStatus());
				scraper.loadAll();
			}
		});
    }


	class UpdateStatus implements ScrapingStatus {
		public void update(LoadProgress progress) {
			statusText.setText(progress.message);
			progressBar.setProgress(progress.percent);
			if (progress.percent >= 100) {
				finish();
			}
		}
	}
}
