package com.jadn.cc.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import com.jadn.cc.R;
import com.jadn.cc.core.CarCastApplication;
import com.jadn.cc.core.Util;
import com.jadn.cc.services.ContentService;
import com.jadn.cc.services.DownloadHistory;
import com.jadn.cc.services.MetaFile;
import com.jadn.cc.services.MetaHolder;
import java.util.ArrayList;
import java.util.HashMap;

public class PodcastList extends BaseActivity {

	SimpleAdapter podcastsAdapter;
	ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			showPodcasts();
		}
	}

	@Override
	protected void onContentService() {
		showPodcasts();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		if (item.getTitle().equals("Delete")) {
			contentService.deletePodcast(info.position);
			list.remove(info.position);
			podcastsAdapter.notifyDataSetChanged();
			return false;
		}
		if (item.getTitle().equals("Delete All Before")) {
			contentService.setCurrentPaused(info.position);
			contentService.purgeToCurrent();
			showPodcasts();
			return false;
		}
		if (item.getTitle().equals("Play")) {
			contentService.play(info.position);
			finish();
			return false;
		}
		return true;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.podcast_list);

		setTitle(CarCastApplication.getAppTitle() + ": Downloaded podcasts");

		ListView listView = (ListView) findViewById(R.id.podcastList);
		registerForContextMenu(listView);

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				MetaHolder metaHolder = new MetaHolder();
				MetaFile mfile = metaHolder.get(position);

				if (mfile.getTitle().equals(
						contentService.currentTitle())) {
					contentService.pauseOrPlay();
				} else {
					// This saves our position
					if (contentService.isPlaying())
						contentService.pauseNow();
					contentService.play(position);
				}
				showPodcasts();
			}
		});
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add("Play");
		menu.add("Delete");
		menu.add("Delete All Before");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.podcasts_menu, menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {

		if (item.getItemId() == R.id.deleteListenedTo) {
			String currTitle = "";
			currTitle = contentService.currentTitle();
			MetaHolder metaHolder = new MetaHolder();
			for (int i = metaHolder.getSize() - 1; i >= 0; i--) {
				MetaFile metaFile = metaHolder.get(i);
				if (currTitle.equals(metaFile.getTitle())) {
					continue;
				}
				// if either are 1/2 baked, move on...
				if (metaFile.getDuration()<=0 || metaFile.getCurrentPos()<=0 ){
					continue;
				}
				if (metaFile.getCurrentPos() > metaHolder.get(i)
						.getDuration() * .9) {
					contentService.deletePodcast(i);
					list.remove(i);
				}
			}
			podcastsAdapter.notifyDataSetChanged();

		} else if (item.getItemId() == R.id.deleteAllPodcasts) {

			// Ask the user if they want to really delete all
			new AlertDialog.Builder(this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle("Delete All?")
					.setMessage(
							"Do you really want to delete all downloaded podcasts?")
					.setPositiveButton("Confirm Delete All",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									contentService.purgeAll();
									list.clear();
									podcastsAdapter.notifyDataSetChanged();
									finish();
								}

							}).setNegativeButton("Cancel", null).show();

			return true;
		}
		if (item.getItemId() == R.id.eraseDownloadHistory) {
			int historyDeleted = DownloadHistory.getInstance().eraseHistory();
			Util.toast(this, "Erased " + historyDeleted
					+ " podcast from dowload history.");
			return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	protected void showPodcasts() {

		ListView listView = (ListView) findViewById(R.id.podcastList);

		MetaHolder metaHolder = new MetaHolder();
		list.clear();

		for (int i = 0; i < metaHolder.getSize(); i++) {
			MetaFile metaFile = metaHolder.get(i);
			HashMap<String, String> item = new HashMap<String, String>();
			if (contentService.currentTitle()
					.equals(metaFile.getTitle())) {
				if (contentService.isPlaying()) {
					item.put("line1", "> " + metaFile.getFeedName());
				} else {
					item.put("line1", "|| " + metaFile.getFeedName());
				}
			} else {
				item.put("line1", metaFile.getFeedName());
			}
			String time = ContentService
					.getTimeString(metaFile.getCurrentPos())
					+ "-"
					+ ContentService.getTimeString(metaFile.getDuration());
			if (metaFile.getCurrentPos() == 0 && metaFile.getDuration() == -1) {
				time = "";
			}
			item.put("xx:xx-xx:xx", time);
			item.put("line2", metaFile.getTitle());
			list.add(item);

		}

		// When doing a delete before, we rebuild the list, but the adapter is
		// ok.
		if (podcastsAdapter == null) {
			podcastsAdapter = new SimpleAdapter(this, list,
			// R.layout.main_item_two_line_row, new String[] { "line1",
					// "line2" }, new int[] { R.id.text1, R.id.text2 });
					R.layout.podcast_items, new String[] { "line1",
							"xx:xx-xx:xx", "line2" }, new int[] {
							R.id.firstLine, R.id.amountHeard, R.id.secondLine });

			listView.setAdapter(podcastsAdapter);
		} else {
			podcastsAdapter.notifyDataSetChanged();
		}

	}

}
