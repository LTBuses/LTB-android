package org.frasermccrossan.ltc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class DiagnoseProblems extends Activity {

	Button wirelessSettings;
	Button wifiSettings;
	Button testWebsite;
	
	OnClickListener buttonListener = new OnClickListener() {
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.test_website:
		        Intent intent = getIntent();
		        String url = intent.getStringExtra("testurl");
				Uri webpage = Uri.parse(url);
				Intent webIntent = new Intent(Intent.ACTION_VIEW, webpage);
				startActivity(webIntent);
				break;
			case R.id.wireless_settings:
				startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
				break;
			case R.id.wifi_settings:
				startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
				break;
			}
		}
	};
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.diagnose_problems);
		testWebsite = (Button)findViewById(R.id.test_website);
		testWebsite.setOnClickListener(buttonListener);
		wirelessSettings = (Button)findViewById(R.id.wireless_settings);
		wirelessSettings.setOnClickListener(buttonListener);
		wifiSettings = (Button)findViewById(R.id.wifi_settings);
		wifiSettings.setOnClickListener(buttonListener);
		if (isConnected(this)) {
			View v = findViewById(R.id.network_settings);
			v.setVisibility(View.GONE);
		}
	}
	
	private static boolean isConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connectivityManager != null) {

            networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (!networkInfo.isAvailable()) {
                networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            }
        }
        return networkInfo == null ? false : networkInfo.isConnected();
    }
	
}
