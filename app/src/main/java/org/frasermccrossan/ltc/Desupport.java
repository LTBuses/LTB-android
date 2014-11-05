package org.frasermccrossan.ltc;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.webkit.WebView;

public class Desupport extends Activity {

	WebView webView;
			
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.desupport);
		webView = (WebView)findViewById(R.id.desupport_view);
		Resources res = getResources();
		webView.loadData(res.getString(R.string.desupport_notice), "text/html", null);
	}
	
}
