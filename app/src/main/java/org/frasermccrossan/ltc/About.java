package org.frasermccrossan.ltc;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.webkit.WebView;

public class About extends Activity {

	WebView webView;
			
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		webView = (WebView)findViewById(R.id.about_web_view);
		Resources res = getResources();
		webView.loadData(res.getString(R.string.about_text), "text/html", null);
	}
	
}
