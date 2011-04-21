package com.jadn.cc.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.util.Log;
import com.jadn.cc.core.CarCastApplication;
import com.jadn.cc.core.Config;
import com.jadn.cc.core.Sayer;
import com.jadn.cc.core.Subscription;
import com.jadn.cc.core.Util;

public class DownloadHelper implements Sayer {
	private String currentSubscription = " ";
	private String currentTitle = " ";
	private int podcastsCurrentBytes;
	private int podcastsDownloaded;
	private int podcastsTotalBytes;
	private int sitesScanned;
	private int totalPodcasts;
	private int totalSites;
	private boolean idle = true;
	private StringBuilder progress = new StringBuilder();
	private SimpleDateFormat sdf = new SimpleDateFormat("MMM-dd hh:mma");

	private final DownloadHistory history;
	private final NotificationHelper notificationHelper;

	public DownloadHelper(DownloadHistory history, NotificationHelper notificationHelper) {
		this.history = history;
		this.notificationHelper = notificationHelper;
	}

	public boolean isIdle() {
		return idle;
	}

	// TODO analyze these and rename to be perfectly clear;
	//  perhaps: getDownloadDetail, getReadableStatus, and getEncodedDownloadStatus
    public String getDownloadProgress() {
        return progress.toString();
    }

	public String getStatus() {
		if (sitesScanned != totalSites)
			return "Scanning Sites " + sitesScanned + "/" + totalSites;
		return "Fetching " + podcastsDownloaded + "/" + totalPodcasts + "\n" + (podcastsCurrentBytes / 1024) + "k/"
				+ (podcastsTotalBytes / 1024) + "k";
	}

	public String encodedDownloadStatus() {
		if (this == null) {
			return "";
		}
		String status = (idle ? "idle" : "busy") + ","
				+ sitesScanned + "," + totalSites
				+ "," + podcastsDownloaded + ","
				+ totalPodcasts + ","
				+ podcastsCurrentBytes + ","
				+ podcastsTotalBytes + ","
				+ currentSubscription + ","
				+ currentTitle;
		return status;
	}

	protected void downloadNewPodCasts(ContentService contentService, List<Subscription> sites,
			int max, String accounts, boolean canCollectData) {
		// reset all state to allow a new download to begin:
		reset();
		say("Starting find/download new podcasts. CarCast ver " + CarCastApplication.getVersion());
		say("Problems? please use Menu / Email Download Report - THANKS!");

		if (canCollectData) {
			postSitesToJadn(accounts, sites);
		}

		say("\nSearching " + sites.size() + " subscriptions. " + sdf.format(new Date()));
		totalSites = sites.size();
		say("History of downloads contains " + history.size() + " podcasts.");

		EnclosureHandler encloseureHandler = new EnclosureHandler(max, history);

		for (Subscription sub : sites) {

			if (sub.enabled) {
				try {
					say("\nScanning subscription/feed: " + sub.url);
					int foundStart = encloseureHandler.metaNets.size();
					if (sub.maxDownloads == -1) {
						encloseureHandler.max = max;
					} else {
						encloseureHandler.max = sub.maxDownloads;
					}

					String name = sub.name;
					encloseureHandler.setFeedName(name);

					Util.downloadPodcast(sub.url, encloseureHandler);

					String message = sitesScanned + "/" + sites.size() + ": " + name + ", "
							+ (encloseureHandler.metaNets.size() - foundStart) + " new";
					say(message);
					notificationHelper.updateNotification(message);

				} catch (Throwable e) {
					/* Display any Error to the GUI. */
					say("Error ex:" + e.getMessage());
					Log.e("BAH", "bad", e);
				}
			} else {
				say("\nSkipping subscription/feed: " + sub.url + " because it is not enabled.");
			}

			sitesScanned++;
		}

		say("\nTotal enclosures " + encloseureHandler.metaNets.size());

		List<MetaNet> newPodcasts = new ArrayList<MetaNet>();
		for (MetaNet metaNet : encloseureHandler.metaNets) {
			if (history.contains(metaNet))
				continue;
			newPodcasts.add(metaNet);
		}
		say(newPodcasts.size() + " podcasts will be downloaded.");
		notificationHelper.updateNotification(newPodcasts.size() + " podcasts will be downloaded.");

		totalPodcasts = newPodcasts.size();
		for (MetaNet metaNet : newPodcasts) {
			podcastsTotalBytes += metaNet.getSize();
		}

		System.setProperty("http.maxRedirects", "50");
		say("\n");

		int got = 0;
		for (int i = 0; i < newPodcasts.size(); i++) {
			String shortName = newPodcasts.get(i).getTitle();
			say((i + 1) + "/" + newPodcasts.size() + " " + shortName);
			notificationHelper.updateNotification((i + 1) + "/" + newPodcasts.size() + " " + shortName);
			podcastsDownloaded = i + 1;

			try {
				File castFile = new File(Config.PodcastsRoot, Long.toString(System.currentTimeMillis()) + ".mp3");

				currentSubscription = newPodcasts.get(i).getSubscription();
				currentTitle = newPodcasts.get(i).getTitle();
				File tempFile = new File(Config.PodcastsRoot, "tempFile");
				say("Subscription: " + currentSubscription);
				say("Title: " + currentTitle);
				say("enclosure url: " + new URL(newPodcasts.get(i).getUrl()));
				InputStream is = getInputStream(new URL(newPodcasts.get(i).getUrl()));
				FileOutputStream fos = new FileOutputStream(tempFile);
				byte[] buf = new byte[16383];
				int amt = 0;
				int expectedSizeKilo = newPodcasts.get(i).getSize() / 1024;
				String preDownload = progress.toString();
				int totalForThisPodcast = 0;
				say(String.format("%dk/%dk 0", totalForThisPodcast / 1024, expectedSizeKilo) + "%\n");
				while ((amt = is.read(buf)) >= 0) {
					fos.write(buf, 0, amt);
					podcastsCurrentBytes += amt;
					totalForThisPodcast += amt;
					progress = new StringBuilder(preDownload
							+ String.format("%dk/%dk  %d", totalForThisPodcast / 1024, expectedSizeKilo,
									(int) ((totalForThisPodcast / 10.24) / expectedSizeKilo)) + "%\n");
				}
				say("download finished.");
				fos.close();
				is.close();
				// add before rename, so if rename fails, we remember
				// that we tried this file and skip it next time.
				history.add(newPodcasts.get(i));

				tempFile.renameTo(castFile);
				new MetaFile(newPodcasts.get(i), castFile).save();

				got++;
				if (totalForThisPodcast != newPodcasts.get(i).getSize()) {
					say("Note: reported size in rss did not match download.");
					// subtract out wrong value
					podcastsTotalBytes -= newPodcasts.get(i).getSize();
					// add in correct value
					podcastsTotalBytes += totalForThisPodcast;

				}
				say("-");
				// update progress for player TODO remove contentService param
				contentService.newContentAdded();

			} catch (Throwable e) {
				say("Problem downloading " + newPodcasts.get(i).getUrl() + " e:" + e);
			}
		}
		say("Finished. Downloaded " + got + " new podcasts. " + sdf.format(new Date()));

		notificationHelper.doDownloadCompletedNotification(got);
		idle = true;
	}

	private void reset() {
		idle = false;
		progress = new StringBuilder();
		podcastsDownloaded = 0;
		podcastsTotalBytes = 0;
		sitesScanned       = 0;
		totalPodcasts      = 0;
		totalSites         = 0;
	}

	// Deal with servers with "location" instead of "Location" in redirect
	// headers
	private InputStream getInputStream(URL url) throws IOException {
		int redirectLimit = 15;
		while (redirectLimit-- > 0) {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setInstanceFollowRedirects(false);
			con.setConnectTimeout(20 * 1000);
			con.setReadTimeout(30 * 1000);
			con.connect();
			if (con.getResponseCode() == 200) {
				return con.getInputStream();
			}
			if (con.getResponseCode() > 300 && con.getResponseCode() > 399) {
				say(url + " gave resposneCode " + con.getResponseCode());
				throw new IOException();
			}
			url = null;
			for (int i = 0; i < 50; i++) {
				if (con.getHeaderFieldKey(i) == null)
					continue;
				// println
				// "key="+con.getHeaderFieldKey(i)+" field="+con.getHeaderField(i)
				if (con.getHeaderFieldKey(i).toLowerCase().equals("location")) {
					url = new URL(con.getHeaderField(i));
					// say("key=" + con.getHeaderFieldKey(i) + " field="
					// + con.getHeaderField(i));
				}
			}
			if (url == null) {
				say("Got 302 without Location");
				// String x = "";
				// for (int jj = 0; jj < 50; jj++) {
				// x += ", " + con.getHeaderFieldKey(jj);
				// }
				// say("headers " + x);
			}
			// println "next: "+url
		}
		throw new IOException(CarCastApplication.getAppTitle() + " redirect limit reached");
	}

	/**
	 * CarCast sends your list of subscriptions to jadn.com so that the list can be used to make the populate search the
	 * search engine. This information is collected only if the checkbox is set in the settings
	 */
	private void postSitesToJadn(final String accounts, final List<Subscription> sites) {

		// Do this in the background so user doesn't wait for data collection...
		// they hate that. :)
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					// Construct data
					StringBuilder data = new StringBuilder();
					boolean first = true;
					for (Subscription sub : sites) {
						if (first)
							first = false;
						else
							data.append('|');
						data.append(sub.url);
					}

					// Send data
					URL url = new URL("http://jadn.com/carcast/collectSites");
					// URL url = new
					// URL("http://192.168.0.128:9090/carcast/collectSites");
					URLConnection conn = url.openConnection();
					conn.setConnectTimeout(20 * 1000);
					conn.setReadTimeout(20 * 1000);
					conn.setDoOutput(true);
					OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
					wr.write("appVersion=" + URLEncoder.encode(CarCastApplication.getVersion(), "UTF-8"));
					wr.write('&');
					wr.write("accounts=" + URLEncoder.encode(accounts, "UTF-8"));
					wr.write('&');
					wr.write("sites=" + URLEncoder.encode(data.toString(), "UTF-8"));
					wr.flush();

					// Get the response
					BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					// String line = null;
					while ((rd.readLine()) != null) {
						// Process line...
						// Log.d("carcast",line);
					}
					wr.close();
					rd.close();
				} catch (Exception e) {
					Log.e("carcast", "updateSite", e);
				}
			}
		}).start();
	}

	@Override
	public void say(String text) {
		progress.append(text);
		progress.append('\n');
		Log.i("CarCast/Download", text);
	}
}
