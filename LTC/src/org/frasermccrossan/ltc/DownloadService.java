package org.frasermccrossan.ltc;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class DownloadService extends Service {

	static final int NOTIF_ID = 12345;
	static final String FETCH_POSITIONS = "getpos";

	LTCScraper scraper = null;
	NotificationCompat.Builder notifBuilder = null;
	String notifTitle = null;
	NotificationManager notifManager = null;
	ScrapingStatus remoteScrapingStatus = null;

	private final IBinder mBinder = new DownloadBinder();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		notifManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notifBuilder = new NotificationCompat.Builder(DownloadService.this);
		notifBuilder.setTicker("Downloading");
		notifBuilder.setContentTitle("Downloading");
		notifBuilder.setContentText("foo");
		notifBuilder.setSmallIcon(R.drawable.ic_stat_notification);
		notifBuilder.setOngoing(true);
		Intent notifIntent = new Intent(DownloadService.this, UpdateDatabase.class);
		notifIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent notifPendingIntent = PendingIntent.getActivity(DownloadService.this, 0,
				notifIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		notifBuilder.setContentIntent(notifPendingIntent);
		notifManager.notify(NOTIF_ID, notifBuilder.build());
		boolean fetchLocations = intent.getBooleanExtra(FETCH_POSITIONS, false);
		UpdateStatus updateStatus = new UpdateStatus();
		scraper = new LTCScraper(DownloadService.this, updateStatus);
		scraper.loadAll(fetchLocations);
		return START_NOT_STICKY;
	}

	class UpdateStatus implements ScrapingStatus {
		
		public void update(LoadProgress progress) {
			if (notifBuilder != null) {
				notifBuilder.setContentText(String.format("%d%%: %s", progress.percent, progress.message));
				notifBuilder.setProgress(100, progress.percent, false);
				notifManager.notify(NOTIF_ID, notifBuilder.build());
				//startForeground(NOTIF_ID, notifBuilder.build());
				if (remoteScrapingStatus != null) {
					remoteScrapingStatus.update(progress);
				}
				if (progress.isComplete()) {
					//stopForeground(true);
					notifBuilder.setTicker("Done");
					notifBuilder.setContentTitle("Done");
					notifBuilder.setContentText("Tap to go to app");
					notifBuilder.setOngoing(false);
					notifBuilder.setAutoCancel(true);
					Intent notifIntent = new Intent(DownloadService.this, FindStop.class);
					notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					PendingIntent notifPendingIntent = PendingIntent.getActivity(DownloadService.this, 0,
							notifIntent, PendingIntent.FLAG_CANCEL_CURRENT);
					notifBuilder.setContentIntent(notifPendingIntent);
					notifManager.notify(NOTIF_ID, notifBuilder.build());
					stopSelf();
				}
			}
		}
	}

	public void setRemoteScrapeStatus(ScrapingStatus r) {
		remoteScrapingStatus = r;
	}
	
	public class DownloadBinder extends Binder {
        DownloadService getService() {
            return DownloadService.this;
        }
    }
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public void onDestroy() {
		if (scraper != null) {
			scraper.close();
		}
	}

}
