package org.frasermccrossan.ltc;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.webkit.WebView;

public class FindStopHelp extends Activity {

	WebView webView;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.find_stop_help);
		webView = (WebView)findViewById(R.id.find_stop_help_web_view);
		Resources res = getResources();
		webView.loadData(res.getString(R.string.stop_list_help_text), "text/html", null);
	}
	
}
