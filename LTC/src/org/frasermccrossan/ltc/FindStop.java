package org.frasermccrossan.ltc;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class FindStop extends Activity {

	TextView status;
	LTCScraper scraper;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.find_stop);
        status = (TextView)findViewById(R.id.status);
        scraper = new LTCScraper(this, new FindStatus());
    }
	
	@Override
	public void onStart() {
		super.onStart();
		scraper.loadAll();
		scraper.close();
	}
    
	private class FindStatus implements ScrapingStatus {
		public void update(LoadProgress progress) {
			status.setText(progress.message);
		}
	}
}