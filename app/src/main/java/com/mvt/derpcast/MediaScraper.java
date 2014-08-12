package com.mvt.derpcast;

import android.content.Context;
import android.text.TextUtils;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.ion.HeadersCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;
import com.mvt.derpcast.helpers.RegexHelper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MediaScraper {

    private String _mediaPattern = "(https?://[^'^\"]+\\.(%1$s)(?:\\?.+)?)['\"]";
    private final String _titlePattern = "<title>(.+)</title>";
    private final String _iframePattern = "<iframe .*src=['\"](https?://.+?)['\"]";
    private List<MediaInfo> _foundMediaInfos = new ArrayList<MediaInfo>();
    private int _activeRequestCount = 0;

    public MediaScraper(List<String> mediaFormats) {
        _mediaPattern = String.format(_mediaPattern, TextUtils.join("|", mediaFormats));
    }

    public void scrape(final Context context, final String pageUrl, final int iframeDepth, final MediaScraperListener listener) {
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
                                String pageTitle = RegexHelper.getFirstMatch(_titlePattern, html);
                                if (pageTitle != null) {
                                    listener.pageTitleFound(pageTitle);
                                }

                                List<String> mediaUrls = RegexHelper.getMatches(_mediaPattern, html);
                                for (final String mediaUrl : mediaUrls) {
                                    synchronized (MediaScraper.this) {
                                        final MediaInfo mediaInfo = new MediaInfo(mediaUrl);

                                        if (!_foundMediaInfos.contains(mediaInfo)) {
                                            _foundMediaInfos.add(mediaInfo);
                                            addMediaMetaData(context, mediaInfo, listener);
                                        }
                                    }
                                }

                                if (iframeDepth > 0 && _foundMediaInfos.size() == 0) {
                                    List<String> iframeUrls = RegexHelper.getMatches(_iframePattern, html);
                                    for (String iframeUrl : iframeUrls) {
                                        scrape(context, iframeUrl, iframeDepth - 1, listener);
                                    }
                                }
                            }
                        }

                        if (_activeRequestCount <= 0) {
                            listener.finished(_foundMediaInfos.size());
                        }
                    }
                });
        }
    }

    private void addMediaMetaData(final Context context, final MediaInfo mediaInfo, final MediaScraperListener listener) {
        _activeRequestCount++;

        Ion.with(context)
            .load("HEAD", mediaInfo.url)
            .setTimeout(5000)
            .onHeaders(new HeadersCallback() {
                @Override
                public void onHeaders(RawHeaders rawHeaders) {
                    if (rawHeaders.getResponseCode() < 400) {

                        int fileNameIndex = mediaInfo.url.lastIndexOf('/') + 1;
                        int queryStringIndex = mediaInfo.url.lastIndexOf('?');
                        queryStringIndex = queryStringIndex != -1 ? queryStringIndex : mediaInfo.url.length();
                        String fileName = mediaInfo.url.substring(fileNameIndex, queryStringIndex);
                        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

                        mediaInfo.title = fileName;
                        mediaInfo.extension = extension;
                        mediaInfo.format = rawHeaders.get("Content-Type");

                        try {
                            mediaInfo.size = Long.parseLong(rawHeaders.get("Content-Length"));
                        } catch (NumberFormatException e) {}

                        listener.mediaFound(mediaInfo);
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
                        listener.finished(_foundMediaInfos.size());
                    }
                }
            });
    }
}
