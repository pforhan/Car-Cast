package com.jadn.cc.services;

import java.io.File;
import java.util.List;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.jadn.cc.core.CarCastApplication;
import com.jadn.cc.core.Config;
import com.jadn.cc.core.Location;
import com.jadn.cc.core.MediaMode;
import com.jadn.cc.core.Subscription;
import com.jadn.cc.trace.ExceptionHandler;
import com.jadn.cc.trace.TraceUtil;

public class ContentService extends Service implements OnCompletionListener {
	/**
	 * Class for clients to access. Because we know this service always runs in the same process as its clients, we
	 * don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public ContentService getService() {
			return ContentService.this;
		}
	}

	private final IBinder binder = new LocalBinder();

	int currentPodcastInPlayer;
	Location location;
	MediaMode mediaMode = MediaMode.UnInitialized;
	MediaPlayer mediaPlayer = new MediaPlayer();
	MetaHolder metaHolder;
	SearchHelper searchHelper;
	boolean wasPausedByPhoneCall;

	private PlayStatusListener playStatusListener;

	private HeadsetReceiver headsetReceiver;

	/*
	 * private boolean _wifiWasDisabledBeforeAutoDownload = false;
	 *
	 * public boolean getWifiWasDisabledBeforeAutoDownload() { return _wifiWasDisabledBeforeAutoDownload; }
	 *
	 * public void setWifiWasDisabledBeforeAutoDownload(boolean value) { _wifiWasDisabledBeforeAutoDownload = value; }
	 */

	public static String getTimeString(int time) {
		StringBuilder sb = new StringBuilder();
		int min = time / (1000 * 60);
		if (min < 10)
			sb.append('0');
		sb.append(min);
		sb.append(':');
		int sec = (time - min * 60 * 1000) / 1000;
		if (sec < 10)
			sb.append('0');
		sb.append(sec);
		return sb.toString();
	}

	public void bump(int bump) {
		if (currentPodcastInPlayer >= metaHolder.getSize())
			return;
		try {
			int npos = mediaPlayer.getCurrentPosition() + bump * 1000;
			if (npos < 0) {
				npos = 0;
			} else if (npos > mediaPlayer.getDuration()) {
				npos = mediaPlayer.getDuration() - 1;
				mediaPlayer.seekTo(npos);
			}
			mediaPlayer.seekTo(npos);
		} catch (Exception e) {
			// do nothing
		}
		if (!mediaPlayer.isPlaying()) {
			saveState();
		}

	}

	private MetaFile cm() {
		return metaHolder.get(currentPodcastInPlayer);
	}

	private int currentDuration() {
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			return 0;
		}
		int dur = cm().getDuration();
		if (dur != -1)
			return dur;
		if (mediaMode == MediaMode.UnInitialized) {
			cm().computeDuration();
			return cm().getDuration();
		}
		return cm().getDuration();
	}

	public File currentFile() {
		return cm().file;
	}

	int currentPostion() {
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			return 0;
		}
		return metaHolder.get(currentPodcastInPlayer).getCurrentPos();
	}

	public int currentProgress() {
		if (mediaMode == MediaMode.UnInitialized) {
			int duration = currentDuration();
			if (duration == 0)
				return 0;
			return currentPostion() * 100 / duration;
		}
		return mediaPlayer.getCurrentPosition() * 100 / mediaPlayer.getDuration();
	}

	public CharSequence currentSummary() {
		StringBuilder sb = new StringBuilder();
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			if (!getDownloadHelper().isIdle())
				return "\nDownloading podcasts";
			return "\nNo Podcasts have been downloaded.";
		}
		sb.append(cm().getFeedName());
		sb.append('\n');
		sb.append(cm().getTitle());
		return sb.toString();
	}

	public String currentTitle() {
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			DownloadHelper downloadHelper = getDownloadHelper();
			if (!downloadHelper.isIdle()) {
				return "\nDownloading podcasts\n" + downloadHelper.getStatus();
			}
			return "No podcasts loaded.\nUse 'Menu' and 'Download Podcasts'";
		}
		return cm().getTitle();
	}

	// used by status when mediaplayer is not started.

	public void deleteCurrentPodcast() {
		if (mediaMode == MediaMode.Playing) {
			pauseNow();
		}
		metaHolder.get(currentPodcastInPlayer).delete();
		metaHolder = new MetaHolder();
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			currentPodcastInPlayer = 0;
		}
	}

	public void deletePodcast(int position) {
		if (mediaPlayer.isPlaying() && currentPodcastInPlayer == position) {
			pauseNow();
		}

		metaHolder.delete(position);
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			if (currentPodcastInPlayer > 0)
				currentPodcastInPlayer--;
		}
		// If we are playing something after what's deleted, adjust the current
		if (currentPodcastInPlayer > position)
			currentPodcastInPlayer--;

		try {
			fullReset();
		} catch (Throwable e) {
			// bummer.
		}
	}

	void deleteUpTo(int upTo) {
		if (mediaPlayer.isPlaying()) {
			pauseNow();
			mediaPlayer.stop();
			mediaPlayer.reset();
		}
		mediaMode = MediaMode.UnInitialized;
		if (upTo == -1)
			upTo = metaHolder.getSize();
		for (int i = 0; i < upTo; i++) {
			metaHolder.delete(0);
		}
		metaHolder = new MetaHolder();
		tryToRestoreLocation();
		if (location == null)
			currentPodcastInPlayer = 0;
	}

	private boolean fullReset() throws Exception {
		mediaPlayer.reset();

		if (currentPodcastInPlayer >= metaHolder.getSize())
			return false;

		mediaPlayer.setDataSource(currentFile().toString());
		mediaPlayer.prepare();
		mediaPlayer.seekTo(metaHolder.get(currentPodcastInPlayer).getCurrentPos());
		return true;
	}

	public int getCount() {
		return metaHolder.getSize();
	}

	public String getCurrentSubscriptionName() {
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			return "";
		}
		return cm().getFeedName();
	}

	public String getDurationString() {
		return getTimeString(currentDuration());
	}

	public Location getLocation() {
		if (mediaMode == MediaMode.UnInitialized) {
			return new Location(currentTitle(), currentPostion());
		}
		return new Location(currentTitle(), mediaPlayer.getCurrentPosition());
	}

	public String getLocationString() {
		Location useLocation = getLocation();
		return getTimeString(useLocation.pos);
	}

	public MediaMode getMediaMode() {
		return mediaMode;
	}

	public String getPodcastEmailSummary() {
		StringBuilder sb = new StringBuilder();
		if (currentPodcastInPlayer < metaHolder.getSize()) {
			MetaFile mf = cm();
			if (mf != null) {
				sb.append("\nWanted to let you know about this podcast:\n\n");
				sb.append("\nTitle: " + mf.getTitle());
				String searchName = mf.getFeedName();
				sb.append("\nFeed Title: " + searchName);
				List<Subscription> subs = getSubscriptionHelper().getSubscriptions();
				for (Subscription sub : subs) {
					if (sub.name.equals(searchName)) {
						sb.append("\nFeed URL: " + sub.url);
						break;
					}
				}
				if (mf.getUrl() != null) {
					sb.append("\nPodcast URL: " + mf.getUrl());
				}
			}
		}
		sb.append("\n\n\n");
		sb.append("This email sent from " + CarCastApplication.getAppTitle() + ".");
		return sb.toString();
	}

	private CarCastApplication getCarCastApplication() {
		return (CarCastApplication) getApplication();
	}

	public String getWhereString() {
		StringBuilder sb = new StringBuilder();
		if (metaHolder.getSize() == 0)
			sb.append('0');
		else
			sb.append(currentPodcastInPlayer + 1);
		sb.append('/');
		sb.append(metaHolder.getSize());
		return sb.toString();
	}

	public void moveTo(double d) {
		if (mediaMode == MediaMode.UnInitialized) {
			if (currentDuration() == 0)
				return;
			metaHolder.get(currentPodcastInPlayer).setCurrentPos((int) (d * currentDuration()));
			mediaPlayer.reset();
			try {
				mediaPlayer.setDataSource(currentFile().toString());
				mediaPlayer.prepare();
			} catch (Exception e) {
				TraceUtil.report(e);
			}
			mediaMode = MediaMode.Paused;
			return;
		}
		mediaPlayer.seekTo((int) (d * mediaPlayer.getDuration()));
	}

	public void next() {
		boolean wasPlaying = mediaPlayer.isPlaying();
		if (wasPlaying) {
			mediaPlayer.stop();
			cm().setCurrentPos(mediaPlayer.getCurrentPosition());
		}
		next(wasPlaying);
	}

	void next(boolean playing) {
		mediaMode = MediaMode.UnInitialized;

		// if we are at end.
		if (currentPodcastInPlayer + 1 >= metaHolder.getSize()) {
			saveState();
			// activity.disableJumpButtons();
			mediaPlayer.reset();
			// say(activity, "That's all folks");
			if (location != null) {
				location.pos = 0;
			}
			return;
		}

		currentPodcastInPlayer++;
		if (playing)
			play();
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i("CarCast", "ContentService binding " + intent);
		return binder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i("CarCast", "ContentService unbinding " + intent);
		return super.onUnbind(intent);
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		cm().setCurrentPos(0);
		cm().setListenedTo();
		cm().save();
		if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoPlayNext", true)) {
			next(true);
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		ExceptionHandler.register(this);

		PhoneStateListener phoneStateListener = new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				super.onCallStateChanged(state, incomingNumber);

				if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING) {
					if (mediaPlayer.isPlaying()) {
						pauseNow();
						wasPausedByPhoneCall = true;
					}
				}

				if (state == TelephonyManager.CALL_STATE_IDLE && wasPausedByPhoneCall) {
					wasPausedByPhoneCall = false;
					pauseOrPlay();
				}
			}
		};

		final TelephonyManager telMgr = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
		telMgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		Config.PodcastsRoot.mkdirs();
		metaHolder = new MetaHolder();
		mediaPlayer.setOnCompletionListener(this);

		// restore state;
		currentPodcastInPlayer = 0;

		restoreState();

		// Due to some crazy quirks in Android, this cannot be done in the
		// manifest and must be done manually like this. See
		// http://groups.google.com/group/android-developers/browse_thread/thread/6d0dda99b4f42c8f/d7de082acdb0da25
		headsetReceiver = new HeadsetReceiver(this);
		registerReceiver(headsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
	}

	public void headsetStatusChanged(boolean headsetPresent) {
		Log.i("CarCast", "ContentService got intent that headset prsent is " + headsetPresent);
		if (!headsetPresent && isPlaying()) {
			pauseNow();
			bump(-2);
		}
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(headsetReceiver);
		super.onDestroy();
		Log.i("CarCast", "ContentService destroyed");
		// Toast.makeText(getApplication(), "Service Destroyed", 1000).show();
	}

	public void pauseNow() {
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
			mediaMode = MediaMode.Paused;
			cm().setCurrentPos(mediaPlayer.getCurrentPosition());
			cm().save();
			// say(activity, "paused " + currentTitle());
			saveState();
			notifyPlayStatusChanged(false);
		}
		// activity.disableJumpButtons();
	}

	/** @returns playing or not */
	public boolean pauseOrPlay() {
		try {
			if (mediaPlayer.isPlaying()) {
				pauseNow();
				return false;
			} else {
				if (mediaMode == MediaMode.Paused) {
					mediaPlayer.start();
					mediaMode = MediaMode.Playing;
					// activity.enableJumpButtons();
					notifyPlayStatusChanged(true);
				} else {
					play();
				}
				return true;
			}
		} catch (Exception e) {
			return false;
		}
	}

	private void play() {
		try {
			if (!fullReset())
				return;

			// say(activity, "started " + currentTitle());
			mediaPlayer.start();
			mediaMode = MediaMode.Playing;
			saveState();
			notifyPlayStatusChanged(true);

		} catch (Exception e) {
			TraceUtil.report(e);
		}
	}

	public void play(int position) {
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.stop();
		}
		currentPodcastInPlayer = position;
		play();
	}

	public void previous() {

		boolean playing = false;
		if (mediaPlayer.isPlaying()) {
			playing = true;
			mediaPlayer.stop();
			// say(activity, "stopped " + currentTitle());
		}
		mediaMode = MediaMode.UnInitialized;
		if (currentPodcastInPlayer > 0) {
			currentPodcastInPlayer--;
		}
		if (currentPodcastInPlayer >= metaHolder.getSize())
			return;
		if (playing)
			play();

		// final TextView textView = (TextView) activity
		// .findViewById(R.id.summary);
		// textView.setText(currentTitle());
	}

	public void purgeHeard() {
		deleteUpTo(currentPodcastInPlayer);
	}

	public void restoreState() {
		final File stateFile = new File(Config.PodcastsRoot, "state.dat");
		if (!stateFile.exists()) {
			location = null;
			return;
		}
		try {
			if (location == null) {
				location = Location.load(stateFile);
			}
			tryToRestoreLocation();
		} catch (Throwable e) {
			// bummer.
		}

	}

	public void saveState() {
		try {
			final File stateFile = new File(Config.PodcastsRoot, "state.dat");
			location = Location.save(stateFile, currentTitle(), mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
		} catch (Throwable e) {
			// bummer.
		}
	}

	public void setCurrentPaused(int position) {
		boolean wasPlaying = mediaPlayer.isPlaying();
		if (wasPlaying) {
			cm().setCurrentPos(mediaPlayer.getCurrentPosition());
			mediaPlayer.stop();
		}
		mediaMode = MediaMode.Paused;
		currentPodcastInPlayer = position;
	}

	public void setMediaMode(MediaMode mediaMode) {
		this.mediaMode = mediaMode;
	}

	public void startDownloadingNewPodCasts() {

		final DownloadHelper downloadHelper = getDownloadHelper();
		if (downloadHelper.isIdle()) {
			// cause display to reflect that we are getting ready to do a
			// download
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
			mNotificationManager.cancel(22);

			new Thread() {
				@Override
				public void run() {
					try {
						Log.i("CarCast", "starting download thread.");
						// Lets not the phone go to sleep while doing
						// downloads....
						PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
						PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ContentService download thread");

						WifiManager.WifiLock wifiLock = null;

						try {
							// The intent here is keep the phone from shutting
							// down during a download.
							ContentService.this.setForeground(true);
							wl.acquire();

							// If we have wifi now, lets hold on to it.
							WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
							if (wifi.isWifiEnabled()) {
								wifiLock = wifi.createWifiLock("CarCast");
								if (wifiLock != null)
									wifiLock.acquire();
								Log.i("CarCast", "Locked Wifi.");
							}

							String accounts = PreferenceManager
									.getDefaultSharedPreferences(
											getApplicationContext()).getString(
											"accounts", "none");
							boolean canCollectData = PreferenceManager
									.getDefaultSharedPreferences(
											getApplicationContext())
									.getBoolean("canCollectData", true);

							downloadHelper.downloadNewPodCasts(
									getSubscriptionHelper().getSubscriptions(),
									Config.getMax(ContentService.this),
									accounts,
									canCollectData);
						} finally {
							downloadCompleted();
							if (wifiLock != null) {
								try {
									wifiLock.release();
									Log.i("CarCast", "released Wifi.");
								} catch (Throwable t) {
									Log.i("CarCast", "Yikes, issue releasing Wifi.");
								}
							}

							ContentService.this.setForeground(false);
							wl.release();
						}
					} catch (Throwable t) {
						Log.i("CarCast", "Unpleasentness during download: " + t.getMessage());
					} finally {
						Log.i("CarCast", "finished download thread.");
					}
				}
			}.start();
		}
	}

	public String startSearch(String search) {
		if (search.equals("-status-")) {
			if (searchHelper.done)
				return "done";
			return "";
		}
		if (search.equals("-results-")) {
			return searchHelper.results;
		}

		searchHelper = new SearchHelper(search);
		searchHelper.start();
		return "";
	}

	private void tryToRestoreLocation() {
		try {
			if (location == null) {
				return;
			}
			boolean found = false;
			for (int i = 0; i < metaHolder.getSize(); i++) {
				if (metaHolder.get(i).getTitle().equals(location.title)) {
					currentPodcastInPlayer = i;
					found = true;
					break;
				}
			}
			if (!found) {
				location = null;
				return;
			}
			mediaPlayer.reset();
			mediaPlayer.setDataSource(currentFile().toString());
			mediaPlayer.prepare();
			mediaPlayer.seekTo(location.pos);
			mediaMode = MediaMode.Paused;
		} catch (Throwable e) {
			// bummer.
		}

	}

	public boolean isPlaying() {
		return mediaPlayer.isPlaying();
	}

	public boolean isIdle() {
<<<<<<< HEAD
		return !isPlaying() && (downloadHelper == null || downloadHelper.idle);
=======
	    return !isPlaying() && getDownloadHelper().isIdle();
>>>>>>> 1838ddf... Pull downloadHelper creation out to CCApp
	}

	public void purgeAll() {
		deleteUpTo(-1);
	}

<<<<<<< HEAD
	public String getDownloadProgress() {
		return downloadHelper.sb.toString();
	}

	public void purgeToCurrent() {
		deleteUpTo(currentPodcastInPlayer);
	}
=======
    public void purgeToCurrent() {
        deleteUpTo(currentPodcastInPlayer);
    }
>>>>>>> 1838ddf... Pull downloadHelper creation out to CCApp

	public void setPlayStatusListener(PlayStatusListener playStatusListener) {
		this.playStatusListener = playStatusListener;
	}

	public void newContentAdded() {
		metaHolder = new MetaHolder();
	}

	public void downloadCompleted() {
		metaHolder = new MetaHolder();
		if (currentPodcastInPlayer >= metaHolder.getSize()) {
			currentPodcastInPlayer = 0;
		}
	}

	private void notifyPlayStatusChanged(boolean playing) {
		if (playStatusListener != null) {
			playStatusListener.playStateUpdated(playing);
		}
	}

	private SubscriptionHelper getSubscriptionHelper() {
		return getCarCastApplication().getSubscriptionHelper();
	}

	private DownloadHelper getDownloadHelper() {
		return getCarCastApplication().getDownloadHelper();
	}
}
