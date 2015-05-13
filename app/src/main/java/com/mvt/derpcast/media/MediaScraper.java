package com.mvt.derpcast.media;

import android.content.Context;
import android.text.TextUtils;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.HeadersCallback;
import com.koushikdutta.ion.HeadersResponse;
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

    private final String mMediaPattern;
    private final String mMediaAttributePattern;
    private final String mIframePattern;
    private final List<MediaInfo> mFoundMediaInfos;
    private final Map<String, String> mMediaFormats;
    private int mActiveRequestCount;

    public MediaScraper(Map<String, String> mediaFormats) {
        mMediaFormats = mediaFormats;
        mMediaPattern = String.format("([^'\"]+\\.(%1$s)(?:\\?.*?)?)", TextUtils.join("|", mediaFormats.keySet()));
        mMediaAttributePattern = mMediaPattern + "['\"]";
        mIframePattern = "<iframe .*src=['\"](.+?)['\"]";
        mFoundMediaInfos = new ArrayList<>();
    }

    public void scrape(final Context context, final String pageUrl, final int iframeDepth, final MediaScraperListener listener) {
        if (pageUrl != null) {
            String mediaUrl = RegexHelper.getFirstMatch(mMediaPattern, pageUrl);
            if (mediaUrl != null) {
                MediaInfo mediaInfo = new MediaInfo(mediaUrl);
                mFoundMediaInfos.add(mediaInfo);
                addMediaMetaData(context, mediaInfo, listener);
                return;
            }

            mActiveRequestCount++;

            Ion.with(context)
                .load(pageUrl)
                .setTimeout(5000)
                .asString()
                .withResponse()
                .setCallback(new FutureCallback<Response<String>>() {
                    @Override
                    public void onCompleted(Exception e, Response<String> response) {
                        mActiveRequestCount--;

                        if (e != null) {
                            e.printStackTrace();
                        } else if (response != null) {
                            String html = response.getResult();
                            if (html != null) {
                                List<String> mediaUrls = RegexHelper.getMatches(mMediaAttributePattern, html);
                                for (final String mediaUrl : mediaUrls) {
                                    synchronized (MediaScraper.this) {
                                        final MediaInfo mediaInfo = new MediaInfo(getAbsoluteUrl(pageUrl, mediaUrl));

                                        if (!mFoundMediaInfos.contains(mediaInfo)) {
                                            mFoundMediaInfos.add(mediaInfo);
                                            addMediaMetaData(context, mediaInfo, listener);
                                        }
                                    }
                                }

                                if (iframeDepth > 0 && mFoundMediaInfos.size() == 0) {
                                    List<String> iframeUrls = RegexHelper.getMatches(mIframePattern, html);
                                    for (String iframeUrl : iframeUrls) {
                                        scrape(context, getAbsoluteUrl(pageUrl, iframeUrl), iframeDepth - 1, listener);
                                    }
                                }
                            }
                        }

                        if (mActiveRequestCount <= 0) {
                            listener.finished(mFoundMediaInfos.size());
                        }
                    }
                });
        }
    }

    private void addMediaMetaData(final Context context, final MediaInfo mediaInfo, final MediaScraperListener listener) {
        mActiveRequestCount++;

        final String url = mediaInfo.getUrl();

        Ion.with(context)
                .load("HEAD", url)
            .setTimeout(5000)
            .onHeaders(new HeadersCallback() {
                @Override
                public void onHeaders(HeadersResponse headersResponse) {
                    if (headersResponse.code() < 400) {
                        int fileNameIndex = url.lastIndexOf('/') + 1;
                        int queryStringIndex = url.lastIndexOf('?');
                        queryStringIndex = queryStringIndex != -1 ? queryStringIndex : url.length();
                        String fileName = url.substring(fileNameIndex, queryStringIndex);
                        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

                        mediaInfo.setTitle(fileName);
                        mediaInfo.setExtension(extension);
                        mediaInfo.setFormat(mMediaFormats.get(extension));

                        String contentLength = headersResponse.getHeaders().get("Content-Length");
                        if (contentLength != null && !contentLength.isEmpty()) {
                            mediaInfo.setSize(Long.parseLong(contentLength));
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
                    if (--mActiveRequestCount <= 0) {
                        listener.finished(mFoundMediaInfos.size());
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
                absoluteUrl = baseUrl.getProtocol() + "://" + baseUrl.getAuthority() +
                        (url.startsWith("/") ? "" : baseUrl.getPath()) + url;
            }
            catch (MalformedURLException e) { e.printStackTrace(); }
        }

        return absoluteUrl;
    }
}
