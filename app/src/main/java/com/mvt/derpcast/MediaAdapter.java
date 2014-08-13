package com.mvt.derpcast;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaAdapter extends BaseExpandableListAdapter {

    private Map<String, List<MediaInfo>> _mediaInfos;
    private MediaInfo _playingMediaInfo;

    public MediaAdapter() {
        _mediaInfos = new HashMap<String, List<MediaInfo>>();
    }

    @Override
    public int getGroupCount() {
        return _mediaInfos.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        Object group = getGroup(groupPosition);
        return _mediaInfos.get(group).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        Object[] groups = _mediaInfos.keySet().toArray();
        return (String)groups[groupPosition];
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        Object group = getGroup(groupPosition);
        return _mediaInfos.get(group).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return 0;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            convertView = inflater.inflate(R.layout.media_list_group_item, parent, false);

            ExpandableListView listView = (ExpandableListView) parent;
            listView.expandGroup(groupPosition);
        }

        TextView groupTextView = (TextView)convertView.findViewById(R.id.group_text_view);
        groupTextView.setText((String)getGroup(groupPosition));

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            convertView = inflater.inflate(R.layout.media_list_item, parent, false);
        }

        MediaInfo mediaInfo = getMediaInfo(groupPosition, childPosition);

        TextView titleTextView = (TextView) convertView.findViewById(R.id.title_text_view);
        titleTextView.setText(mediaInfo.title.toLowerCase());

        TextView sizeTextView = (TextView) convertView.findViewById(R.id.size_text_view);

        int megaBytes = (int)Math.ceil(mediaInfo.size / 1048576d);
        sizeTextView.setText(megaBytes + "MB");

        View playingImageView = convertView.findViewById(R.id.playing_image_view);
        playingImageView.setVisibility(mediaInfo == _playingMediaInfo ? View.VISIBLE : View.INVISIBLE);

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public synchronized void addMediaInfo(MediaInfo mediaInfo) {

        String mediaType = mediaInfo.format.substring(0, mediaInfo.format.indexOf('/'));
        if (!_mediaInfos.containsKey(mediaType)) {
            _mediaInfos.put(mediaType, new ArrayList<MediaInfo>());
        }

        List<MediaInfo> group = _mediaInfos.get(mediaType);
        if (!group.contains(mediaInfo)) {
            group.add(mediaInfo);
            Collections.sort(group);
            notifyDataSetChanged();
        }
    }

    public MediaInfo getMediaInfo(int groupPosition, int childPosition) {
        MediaInfo mediaInfo = (MediaInfo)getChild(groupPosition, childPosition);
        return mediaInfo;
    }

    public void setPlayingMediaInfo(MediaInfo mediaInfo) {
        _playingMediaInfo = mediaInfo;
        notifyDataSetChanged();
    }

    public MediaInfo getPlayingMedia() {
        return _playingMediaInfo;
    }
}

