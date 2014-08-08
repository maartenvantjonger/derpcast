package com.mvt.urlcaster;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.command.ServiceCommandError;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends BaseAdapter implements DiscoveryManagerListener {

    private final String TAG = "DeviceAdapter";
    private List<ConnectableDevice > _devices = new ArrayList<ConnectableDevice>();

    public DeviceAdapter(Context context) {
        ConnectableDevice localDevice = new ConnectableDevice();
        localDevice.setId("local");
        localDevice.setIpAddress("127.0.0.1");
        localDevice.setFriendlyName(Build.MODEL);
        _devices.add(localDevice);

        DiscoveryManager.init(context);
        DiscoveryManager.getInstance().addListener(this);
    }

    @Override
    public int getCount() {
        return _devices.size();
    }

    @Override
    public Object getItem(int i) {
        return getDevice(i);
    }

    public ConnectableDevice getDevice(int i) {
        return _devices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, final ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            view = inflater.inflate(R.layout.device_list_item, viewGroup, false);
        }

        final ConnectableDevice device = getDevice(i);

        TextView titleTextView = (TextView) view.findViewById(R.id.title_text_view);
        titleTextView.setText(device.getFriendlyName());

        TextView protocolTextView = (TextView) view.findViewById(R.id.protocol_text_view);
        if (device.getId().equals("local")) {
            protocolTextView.setText("Local video player");
        }
        else {
            String serviceNames = device.getConnectedServiceNames();
            protocolTextView.setText(serviceNames);

            View connectedImageView = view.findViewById(R.id.connected_image_view);
            connectedImageView.setVisibility(device.isConnected() ? View.VISIBLE : View.INVISIBLE);
        }

        return view;
    }

    @Override
    public void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
        if (!_devices.contains(device)) {
            _devices.add(device);
            notifyDataSetChanged();
        }
    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {

    }

    @Override
    public void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {
        if (_devices.contains(device)) {
            _devices.remove(device);
            notifyDataSetChanged();
        }
    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {

    }
}

