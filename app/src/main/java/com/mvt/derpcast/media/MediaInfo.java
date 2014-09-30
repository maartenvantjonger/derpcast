package com.mvt.derpcast.media;

public class MediaInfo implements Comparable<MediaInfo> {
    public String title;
    public String url;
    public String format;
    public String extension;
    public long size;

    public MediaInfo(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object object)
    {
        boolean equal = false;

        if (object instanceof MediaInfo)
        {
            MediaInfo mediaInfo = (MediaInfo)object;
            equal = mediaInfo.url.equalsIgnoreCase(this.url);
        }

        return equal;
    }

    @Override
    public int compareTo(MediaInfo mediaInfo) {
        return Long.valueOf(mediaInfo.size).compareTo(this.size);
    }

        @Override
    public int hashCode() {
        return this.url.hashCode();
    }
}
