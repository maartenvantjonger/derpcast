package com.mvt.derpcast.media;

import android.support.annotation.NonNull;

public class MediaInfo implements Comparable<MediaInfo> {
    private String mTitle;
    private String mUrl;
    private String mFormat;
    private String mExtension;
    private long mSize;

    public MediaInfo(String url) {
        mUrl = url;
    }

    @Override
    public boolean equals(Object object)
    {
        boolean equal = false;

        if (object instanceof MediaInfo) {
            MediaInfo mediaInfo = (MediaInfo)object;
            equal = mediaInfo.mUrl.equalsIgnoreCase(mUrl);
        }

        return equal;
    }

    @Override
    public int compareTo(@NonNull MediaInfo mediaInfo) {
        return Long.valueOf(mediaInfo.mSize).compareTo(mSize);
    }

        @Override
    public int hashCode() {
            return mUrl.hashCode();
        }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getFormat() {
        return mFormat;
    }

    public void setFormat(String format) {
        mFormat = format;
    }

    public String getExtension() {
        return mExtension;
    }

    public void setExtension(String extension) {
        mExtension = extension;
    }

    public long getSize() {
        return mSize;
    }

    public void setSize(long size) {
        mSize = size;
    }

    public String getUrl() {
        return mUrl;
    }
}
