package org.frasermccrossan.ltc;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.webkit.WebView;

public class UpdateDatabaseHelp extends Activity {

	WebView webView;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.update_database_help);
		webView = (WebView)findViewById(R.id.update_database_help_web_view);
		Resources res = getResources();
		webView.loadData(res.getString(R.string.update_database_help_text), "text/html", null);
	}
	
}
