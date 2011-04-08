package com.jadn.cc.services;

import java.io.Serializable;

public class HistoryEntry implements Serializable {
	String subscription;
	String podcastURL;

	public HistoryEntry(String subscription, String podcastURL) {
		this.subscription = subscription;
		this.podcastURL = podcastURL;
	}
}
