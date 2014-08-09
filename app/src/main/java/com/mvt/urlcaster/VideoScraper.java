package com.mvt.derpcast;

import android.content.Context;
import android.util.Log;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.ion.HeadersCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;
import com.mvt.derpcast.helpers.RegexHelper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class VideoScraper {

    private static final String TAG = "VideoScraper";
    private List<VideoInfo> _foundVideoInfos = new ArrayList<VideoInfo>();
    private int _activeRequestCount = 0;

    public void scrape(final Context context, final String pageUrl, final int iframeDepth, final VideoScraperListener listener) {
        Log.i(TAG, "getVideoUrls depth: " + iframeDepth + " page: " + pageUrl);

        if (pageUrl != null) {
            _activeRequestCount++;

            Ion.with(context)
                .load(pageUrl)
                .setTimeout(5000)
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> response) {
                        _activeRequestCount--;

                        if (e != null) {
                            e.printStackTrace();
                        } else if (response != null) {
                            String html = response.getResult();
                            if (html != null) {
                                String pageTitle = RegexHelper.getFirstMatch("<title>(.+)</title>", html);
                                if (pageTitle != null) {
                                    listener.pageTitleFound(pageTitle);
                                }

                                List<String> videoUrls = RegexHelper.getMatches("(https?://[^'^\"]+\\.mp4(?:\\?.+)?)['\"]", html);
                                for (final String videoUrl : videoUrls) {
                                    System.out.println("Found video at depth: " + iframeDepth + " in page: " + pageUrl + " video url: " + videoUrl);

                                    synchronized (VideoScraper.this) {
                                        final VideoInfo videoInfo = new VideoInfo(videoUrl);

                                        if (!_foundVideoInfos.contains(videoUrl)) {
                                            _foundVideoInfos.add(videoInfo);
                                            addVideoMetaData(context, videoInfo, listener);
                                        }
                                    }
                                }

                                if (iframeDepth > 0 && _foundVideoInfos.size() == 0) {
                                    List<String> iframeUrls = RegexHelper.getMatches("<iframe .*src=['\"](https?://.+?)['\"]", html);
                                    for (String iframeUrl : iframeUrls) {
                                        scrape(context, iframeUrl, iframeDepth - 1, listener);
                                    }
                                }
                            }
                        }

                        if (_activeRequestCount <= 0) {
                            listener.finished(_foundVideoInfos.size());
                        }
                    }
                });
        }
    }

    private void addVideoMetaData(final Context context, final VideoInfo videoInfo, final VideoScraperListener listener) {
        _activeRequestCount++;

        Ion.with(context)
            .load("HEAD", videoInfo.url)
            .setTimeout(5000)
            .onHeaders(new HeadersCallback() {
                @Override
                public void onHeaders(RawHeaders rawHeaders) {
                    if (rawHeaders.getResponseCode() < 400) {
                        videoInfo.format = rawHeaders.get("Content-Type");
                        videoInfo.title = RegexHelper.getFirstMatch("([^/^=]+\\.mp4)", videoInfo.url);

                        try {
                            videoInfo.size = Long.parseLong(rawHeaders.get("Content-Length"));
                        } catch (NumberFormatException e) {}

                        listener.videoFound(videoInfo);
                    } else {
                        Log.i(TAG, videoInfo.url + " returned status code " + rawHeaders.getResponseCode());
                    }
                }
            })
            .asInputStream()
            .withResponse()
            .setCallback(new FutureCallback<Response<InputStream>>() {
                @Override
                public void onCompleted(Exception e, Response<InputStream> inputStreamResponse) {
                    _activeRequestCount--;
                    if (_activeRequestCount <= 0) {
                        listener.finished(_foundVideoInfos.size());
                    }
                }
            });
    }
}
