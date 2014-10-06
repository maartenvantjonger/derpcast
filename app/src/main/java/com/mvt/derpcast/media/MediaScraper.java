package com.mvt.derpcast.media;

import android.content.Context;
import android.text.TextUtils;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.ion.HeadersCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;
import com.mvt.derpcast.helpers.RegexHelper;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MediaScraper {

    private String _mediaPattern;
    private String _mediaAttributePattern;
    private final String _iframePattern = "<iframe .*src=['\"](.+?)['\"]";
    private List<MediaInfo> _foundMediaInfos = new ArrayList<MediaInfo>();
    private Map<String, String> _mediaFormats;
    private int _activeRequestCount = 0;

    public MediaScraper(Map<String, String> mediaFormats) {
        _mediaFormats = mediaFormats;
        _mediaPattern = String.format("([^'\"]+\\.(%1$s)(?:\\?.*?)?)", TextUtils.join("|", mediaFormats.keySet()));
        _mediaAttributePattern = _mediaPattern + "['\"]";
    }

    public void scrape(final Context context, final String pageUrl, final int iframeDepth, final MediaScraperListener listener) {
        if (pageUrl != null) {
            String mediaUrl = RegexHelper.getFirstMatch(_mediaPattern, pageUrl);
            if (mediaUrl != null) {
                MediaInfo mediaInfo = new MediaInfo(mediaUrl);
                _foundMediaInfos.add(mediaInfo);
                addMediaMetaData(context, mediaInfo, listener);
                return;
            }

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
                                List<String> mediaUrls = RegexHelper.getMatches(_mediaAttributePattern, html);
                                for (final String mediaUrl : mediaUrls) {
                                    synchronized (MediaScraper.this) {
                                        final MediaInfo mediaInfo = new MediaInfo(getAbsoluteUrl(pageUrl, mediaUrl));

                                        if (!_foundMediaInfos.contains(mediaInfo)) {
                                            _foundMediaInfos.add(mediaInfo);
                                            addMediaMetaData(context, mediaInfo, listener);
                                        }
                                    }
                                }

                                if (iframeDepth > 0 && _foundMediaInfos.size() == 0) {
                                    List<String> iframeUrls = RegexHelper.getMatches(_iframePattern, html);
                                    for (String iframeUrl : iframeUrls) {
                                        scrape(context, getAbsoluteUrl(pageUrl, iframeUrl), iframeDepth - 1, listener);
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
                        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

                        mediaInfo.title = fileName;
                        mediaInfo.extension = extension;
                        mediaInfo.format = _mediaFormats.get(extension);

                        String contentLength = rawHeaders.get("Content-Length");
                        if (contentLength != null && contentLength != "") {
                            mediaInfo.size = Long.parseLong(contentLength);
                        }
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

    private String getAbsoluteUrl(String pageUrl, String url) {
        // Fixes Dropbox URLs
        String absoluteUrl = url.replace("?dl=0", "?dl=1");

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            try {
                URL baseUrl = new URL(pageUrl);
                if (url.startsWith("/")) {
                    absoluteUrl = baseUrl.getProtocol() +
                        "://" + baseUrl.getAuthority() + url;
                }
                else {
                    absoluteUrl = baseUrl.getProtocol() +
                        "://" + baseUrl.getAuthority() + baseUrl.getPath() + url;
                }
            }
            catch (MalformedURLException e) { e.printStackTrace(); }
        }

        return absoluteUrl;
    }
}
