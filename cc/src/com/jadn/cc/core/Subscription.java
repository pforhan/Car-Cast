package com.jadn.cc.core;

import android.os.Parcel;
import android.os.Parcelable;

public class Subscription implements Parcelable, Comparable<Subscription> {

    public static final Parcelable.Creator<Subscription> CREATOR = new Parcelable.Creator<Subscription>() {
         @Override
		public Subscription createFromParcel(Parcel in) {
             return new Subscription(in.readString(),   // name
                                     in.readString(),   // URL
                                     in.readInt(),      // max count
                                     OrderingPreference.values()[in.readInt()],// order pref
                                     Boolean.parseBoolean(in.readString())); //enabled
         }

         @Override
		public Subscription[] newArray(int size) {
             return new Subscription[size];
         }
     };
    public final int                maxDownloads;
    public final String             name;
    public final OrderingPreference orderingPreference;
    public final String             url;
    public boolean enabled;

    public Subscription(String name, String url) {
        this(name, url, -1, OrderingPreference.FIFO, true);
    }

    public Subscription(String name, String url, Boolean enabled) {
        this(name, url, -1, OrderingPreference.FIFO, enabled);
    }

    public Subscription(String name, String url, int maxDownloads, OrderingPreference orderingPreference) {
        this(name, url, maxDownloads, orderingPreference, true);
    }

    public Subscription(String name, String url, int maxDownloads, OrderingPreference orderingPreference, boolean enabled) {
        this.name = name;
        this.url = url;
        this.maxDownloads = maxDownloads;
        this.orderingPreference = orderingPreference;
        this.enabled = enabled;
    }

    @Override
    public int compareTo(Subscription another) {
        return name.compareTo(another.name);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "Subscription: url=" + url + " ; name="+ name + "; max=" + maxDownloads + " ; ordering=" + orderingPreference + " ; enabled=" + enabled;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(url);
        dest.writeInt(maxDownloads);
        dest.writeInt(orderingPreference.ordinal());
        dest.writeString(Boolean.toString(enabled));
    }

}
