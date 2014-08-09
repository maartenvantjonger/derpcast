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

    private final String TAG = "VideoAdapter";
    private List<VideoInfo> _videoInfos = new ArrayList<VideoInfo>();
    private VideoInfo _playingVideoInfo;
    private final Object _syncRoot = new Object();

    public synchronized void addVideoInfo(VideoInfo videoInfo) {
        if (!_videoInfos.contains(videoInfo)) {
            _videoInfos.add(videoInfo);
            Collections.sort(_videoInfos);
            notifyDataSetChanged();
        }
    }

    public void setPlayingVideoInfo(int i) {
        _playingVideoInfo = getVideoInfo(i);
        notifyDataSetChanged();
    }

    public VideoInfo getPlayingVideo() {
        return _playingVideoInfo;
    }

    @Override
    public int getCount() {
        return _videoInfos.size();
    }

    @Override
    public Object getItem(int i) {
        return getVideoInfo(i);
    }

    public VideoInfo getVideoInfo(int i) {
        return _videoInfos.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, final ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            view = inflater.inflate(R.layout.video_list_item, viewGroup, false);
        }

        final VideoInfo videoInfo = getVideoInfo(i);

        TextView titleTextView = (TextView) view.findViewById(R.id.title_text_view);
        titleTextView.setText(videoInfo.title.toLowerCase());

        TextView sizeTextView = (TextView) view.findViewById(R.id.size_text_view);

        int megaBytes = (int)Math.ceil(videoInfo.size / 1048576d);
        sizeTextView.setText(megaBytes + "MB");

        View playingImageView = view.findViewById(R.id.playing_image_view);
        playingImageView.setVisibility(videoInfo == _playingVideoInfo ? View.VISIBLE : View.INVISIBLE);

        return view;
    }
}

