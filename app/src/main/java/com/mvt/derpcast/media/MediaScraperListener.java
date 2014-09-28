package com.mvt.derpcast.media;

public interface MediaScraperListener {
    void mediaFound(MediaInfo mediaInfo);
    void finished(int mediaFound);
}
