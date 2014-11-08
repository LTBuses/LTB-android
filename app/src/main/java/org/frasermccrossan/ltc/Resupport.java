package org.frasermccrossan.ltc;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.webkit.WebView;

public class Resupport extends Activity {

	WebView webView;
			
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.resupport);
		webView = (WebView)findViewById(R.id.resupport_view);
		Resources res = getResources();
		webView.loadData(res.getString(R.string.resupport_notice), "text/html", null);
	}
	
}
