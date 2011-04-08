package com.jadn.cc.core;

import java.io.File;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Config {

	public static final File CarCastRoot = new File(android.os.Environment.getExternalStorageDirectory(),"carcast");
	public static final File PodcastsRoot = new File(CarCastRoot,"podcasts");

	public static int getMax(Context context){
		SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(context);
		return Integer.parseInt(app_preferences.getString("listmax", "2"));
	}


}
