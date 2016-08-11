package org.frasermccrossan.ltc;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class DownloadService extends Service {

	static final int NOTIF_ID = 12345;

	LTCScraper scraper = null;
	NotificationCompat.Builder notifBuilder = null;
	NotificationManager notifManager = null;
	ScrapingStatus remoteScrapingStatus = null;
	LoadProgress lastProgress = null;
	Resources resources;
	Boolean manuallyStopped;

	private final IBinder mBinder = new DownloadBinder();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		resources = getResources();
		notifManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notifBuilder = new NotificationCompat.Builder(DownloadService.this);
		UpdateStatus updateStatus = new UpdateStatus();
		scraper = new LTCScraper(DownloadService.this, updateStatus);
		scraper.loadAll();
		manuallyStopped = false;
		return START_NOT_STICKY;
	}
	
	public void cancel() {
		manuallyStopped = true;
		if (scraper != null) {
			scraper.close();
			scraper = null;
		}
		stopSelf();
	}
	
	class UpdateStatus implements ScrapingStatus {
		
		public void update(LoadProgress progress) {
			if (notifBuilder != null) {
				lastProgress = progress;
				if (remoteScrapingStatus != null) {
					remoteScrapingStatus.update(progress);
				}
				String msgMaybePct;
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					// show percentage on platforms that don't support progress bars in notifications
					msgMaybePct = String.format(resources.getString(R.string.notification_progress_format_pct),
							progress.percent, progress.message);
				}
				else {
					// rely on the progress bar
					msgMaybePct = progress.message;
				}
				notifBuilder.setContentText(msgMaybePct);
				notifBuilder.setProgress(100, progress.percent, false);
				notifBuilder.setContentTitle(progress.title);
				notifBuilder.setTicker(progress.title);
				Intent notifIntent;
				if (progress.isComplete()) {
					notifManager.cancelAll();
					notifBuilder.setOngoing(false);
					notifBuilder.setAutoCancel(true);
					notifBuilder.setSmallIcon(R.drawable.ic_stat_notification);
					Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); 
					notifBuilder.setSound(alert);
					if (progress.isFailed()) {
						notifIntent = new Intent(DownloadService.this, DiagnoseProblems.class);
				    	notifIntent.putExtra("testurl", LTCScraper.ROUTE_URL);
						PendingIntent notifPendingIntent = PendingIntent.getActivity(DownloadService.this, 0,
								notifIntent, PendingIntent.FLAG_CANCEL_CURRENT);
						notifBuilder.setContentIntent(notifPendingIntent);
					}
					else {
						notifIntent = new Intent(DownloadService.this, FindStop.class);
						notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						PendingIntent notifPendingIntent = PendingIntent.getActivity(DownloadService.this, 0,
								notifIntent, PendingIntent.FLAG_CANCEL_CURRENT);
						notifBuilder.setContentIntent(notifPendingIntent);
					}
				}
				else {
					notifBuilder.setOngoing(true);
					notifBuilder.setAutoCancel(false);
					notifBuilder.setSmallIcon(R.drawable.ic_stat_download);
					if (progress.completeEnough) {
						notifIntent = new Intent(DownloadService.this, FindStop.class);
						notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					}
					else {
						notifIntent = new Intent(DownloadService.this, UpdateDatabase.class);
						notifIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
					}
					PendingIntent notifPendingIntent = PendingIntent.getActivity(DownloadService.this, 0,
							notifIntent, PendingIntent.FLAG_CANCEL_CURRENT);
					notifBuilder.setContentIntent(notifPendingIntent);
				}
				notifManager.notify(NOTIF_ID, notifBuilder.build());
				if (progress.isComplete()) {
					stopSelf();
				}
			}
		}
	}	

	public void setRemoteScrapeStatus(ScrapingStatus r) {
		remoteScrapingStatus = r;
		if (remoteScrapingStatus != null && lastProgress != null) {
			remoteScrapingStatus.update(lastProgress);
		}
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
			scraper = null;
		}
		if (notifManager != null && manuallyStopped) {
			notifManager.cancelAll();
		}
	}

}
