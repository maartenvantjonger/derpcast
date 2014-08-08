package com.mvt.urlcaster;

public interface VideoScraperListener {
    void videoFound(VideoInfo videoInfo);
    void pageTitleFound(String pageTitle);
    void finished(int videosFound);
}
