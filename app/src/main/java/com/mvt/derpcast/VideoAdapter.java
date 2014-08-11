package com.mvt.derpcast;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoAdapter extends BaseAdapter {

    private List<MediaInfo> _mediaInfos = new ArrayList<MediaInfo>();
    private MediaInfo _playingVideoInfo;

    public synchronized void addVideoInfo(MediaInfo mediaInfo) {
        if (!_mediaInfos.contains(mediaInfo)) {
            _mediaInfos.add(mediaInfo);
            Collections.sort(_mediaInfos);
            notifyDataSetChanged();
        }
    }

    public void setPlayingVideoInfo(int i) {
        _playingVideoInfo = getVideoInfo(i);
        notifyDataSetChanged();
    }

    public MediaInfo getPlayingVideo() {
        return _playingVideoInfo;
    }

    @Override
    public int getCount() {
        return _mediaInfos.size();
    }

    @Override
    public Object getItem(int i) {
        return getVideoInfo(i);
    }

    public MediaInfo getVideoInfo(int i) {
        return _mediaInfos.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, final ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            view = inflater.inflate(R.layout.media_list_item, viewGroup, false);
        }

        final MediaInfo mediaInfo = getVideoInfo(i);

        TextView titleTextView = (TextView) view.findViewById(R.id.title_text_view);
        titleTextView.setText(mediaInfo.title.toLowerCase());

        TextView sizeTextView = (TextView) view.findViewById(R.id.size_text_view);

        int megaBytes = (int)Math.ceil(mediaInfo.size / 1048576d);
        sizeTextView.setText(megaBytes + "MB");

        View playingImageView = view.findViewById(R.id.playing_image_view);
        playingImageView.setVisibility(mediaInfo == _playingVideoInfo ? View.VISIBLE : View.INVISIBLE);

        return view;
    }
}

