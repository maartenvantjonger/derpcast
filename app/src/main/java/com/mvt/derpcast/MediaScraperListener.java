package com.mvt.derpcast;

public interface MediaScraperListener {
    void mediaFound(MediaInfo mediaInfo);
    void pageTitleFound(String pageTitle);
    void finished(int mediaFound);
}
