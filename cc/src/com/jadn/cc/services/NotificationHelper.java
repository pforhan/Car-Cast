package com.jadn.cc.services;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import com.jadn.cc.R;
import com.jadn.cc.core.CarCastApplication;
import com.jadn.cc.ui.CarCast;

public class NotificationHelper {

	private final CarCastApplication application;

	public NotificationHelper(CarCastApplication application) {
		this.application = application;
	}

	void doDownloadCompletedNotification(int got) {
		// Get the notification manager service.
		NotificationManager mNotificationManager = getNotificationManager();

		mNotificationManager.cancel(23);

		// Allow UI to update download text (only when in debug mode) this seems
		// suboptimal
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Notification notification = new Notification(R.drawable.icon2,
				"Download complete", System.currentTimeMillis());

		PendingIntent contentIntent = PendingIntent.getActivity(application, 0,
				new Intent(application, CarCast.class), 0);

		notification.setLatestEventInfo(application, "Downloads Finished",
				"Downloaded " + got + " podcasts.", contentIntent);
		notification.flags = Notification.FLAG_AUTO_CANCEL;

		mNotificationManager.notify(22, notification);
	}

	void updateNotification(String update) {
		NotificationManager mNotificationManager = getNotificationManager();

		Notification notification = new Notification(R.drawable.iconbusy,
				"Downloading started", System.currentTimeMillis());

		PendingIntent contentIntent = PendingIntent.getActivity(application, 0,
				new Intent(application, CarCast.class), 0);

		notification.setLatestEventInfo(application,
				"Downloading Started", update, contentIntent);
		notification.flags = Notification.FLAG_ONGOING_EVENT;

		mNotificationManager.notify(23, notification);

	}

	private NotificationManager getNotificationManager() {
		return (NotificationManager) application.getSystemService(Activity.NOTIFICATION_SERVICE);
	}

}
