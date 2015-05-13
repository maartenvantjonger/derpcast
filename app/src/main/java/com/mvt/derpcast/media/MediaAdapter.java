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

    private final List<MediaInfo> mMediaInfos = new ArrayList<>();
    private MediaInfo mPlayingMediaInfo;

    public synchronized void addMediaInfo(MediaInfo mediaInfo) {
        if (!mMediaInfos.contains(mediaInfo)) {
            mMediaInfos.add(mediaInfo);
            Collections.sort(mMediaInfos);
            notifyDataSetChanged();
        }
    }

    public void setPlayingMediaInfo(MediaInfo mediaInfo) {
        mPlayingMediaInfo = mediaInfo;
        notifyDataSetChanged();
    }

    public MediaInfo getPlayingMedia() {
        return mPlayingMediaInfo;
    }

    public void clear() {
        mMediaInfos.clear();
    }

    @Override
    public int getCount() {
        return mMediaInfos.size();
    }

    @Override
    public Object getItem(int i) {
        return getMediaInfo(i);
    }

    public MediaInfo getMediaInfo(int i) {
        return mMediaInfos.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, final ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            view = inflater.inflate(R.layout.view_media_item, viewGroup, false);
        }

        final MediaInfo mediaInfo = getMediaInfo(i);

        TextView mediaTitleTextView = (TextView) view.findViewById(R.id.media_title_text_view);
        mediaTitleTextView.setText(mediaInfo.getTitle().toLowerCase());

        TextView sizeTextView = (TextView) view.findViewById(R.id.size_text_view);

        int megaBytes = (int) Math.ceil(mediaInfo.getSize() / 1048576d);
        sizeTextView.setText(megaBytes + "MB");

        View playingImageView = view.findViewById(R.id.playing_image_view);
        playingImageView.setVisibility(mediaInfo.equals(mPlayingMediaInfo) ? View.VISIBLE : View.INVISIBLE);

        return view;
    }
}