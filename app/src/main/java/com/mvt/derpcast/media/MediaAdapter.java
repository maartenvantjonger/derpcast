package com.mvt.derpcast.media;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.mvt.derpcast.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MediaAdapter extends BaseAdapter {

    private List<MediaInfo> _mediaInfos = new ArrayList<MediaInfo>();
    private MediaInfo _playingMediaInfo;

    public synchronized void addMediaInfo(MediaInfo mediaInfo) {
        if (!_mediaInfos.contains(mediaInfo)) {
            _mediaInfos.add(mediaInfo);
            Collections.sort(_mediaInfos);
            notifyDataSetChanged();
        }
    }

    public void setPlayingMediaInfo(MediaInfo mediaInfo) {
        _playingMediaInfo = mediaInfo;
        notifyDataSetChanged();
    }

    public MediaInfo getPlayingMedia() {
        return _playingMediaInfo;
    }

    public void clear() {
        _mediaInfos.clear();
    }

    @Override
    public int getCount() {
        return _mediaInfos.size();
    }

    @Override
    public Object getItem(int i) {
        return getMediaInfo(i);
    }

    public MediaInfo getMediaInfo(int i) {
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

        final MediaInfo mediaInfo = getMediaInfo(i);

        TextView mediaTitleTextView = (TextView) view.findViewById(R.id.media_title_text_view);
        mediaTitleTextView.setText(mediaInfo.title.toLowerCase());

        TextView sizeTextView = (TextView) view.findViewById(R.id.size_text_view);

        int megaBytes = (int)Math.ceil(mediaInfo.size / 1048576d);
        sizeTextView.setText(megaBytes + "MB");

        View playingImageView = view.findViewById(R.id.playing_image_view);
        playingImageView.setVisibility(mediaInfo == _playingMediaInfo ? View.VISIBLE : View.INVISIBLE);

        return view;
    }
}