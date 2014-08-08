package com.mvt.urlcaster;

public class VideoInfo implements Comparable<VideoInfo> {
    public String title;
    public String url;
    public String format;
    public long size;

    public VideoInfo(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object object)
    {
        VideoInfo videoInfo = (VideoInfo)object;
        return videoInfo.url.equalsIgnoreCase(this.url);
    }

    @Override
    public int compareTo(VideoInfo videoInfo) {
        return Long.valueOf(videoInfo.size).compareTo(this.size);
    }

        @Override
    public int hashCode() {
        return this.url.hashCode();
    }
}
