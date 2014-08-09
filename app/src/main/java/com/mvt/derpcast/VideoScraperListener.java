package com.mvt.derpcast;

public interface VideoScraperListener {
    void videoFound(VideoInfo videoInfo);
    void pageTitleFound(String pageTitle);
    void finished(int videosFound);
}
