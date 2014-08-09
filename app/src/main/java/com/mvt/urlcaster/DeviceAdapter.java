package com.mvt.derpcast;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.discovery.CapabilityFilter;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.command.ServiceCommandError;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends BaseAdapter implements DiscoveryManagerListener {

    interface DeviceAddedListener {
        public void onDeviceAdded(ConnectableDevice device);
    }

    private final String TAG = "DeviceAdapter";
    private List<ConnectableDevice > _devices = new ArrayList<ConnectableDevice>();
    private DeviceAddedListener _deviceAddedListener;
    private final Object _syncRoot = new Object();

    public DeviceAdapter(Context context) {
        ConnectableDevice localDevice = new ConnectableDevice();
        localDevice.setId("local");
        localDevice.setIpAddress("127.0.0.1");
        localDevice.setFriendlyName(Build.MODEL);
        _devices.add(localDevice);

        DiscoveryManager.init(context);
        DiscoveryManager discoveryManager = DiscoveryManager.getInstance();
        discoveryManager.setCapabilityFilters(new CapabilityFilter(MediaPlayer.Display_Video));
        discoveryManager.setPairingLevel(DiscoveryManager.PairingLevel.ON);
        discoveryManager.addListener(DeviceAdapter.this);
        discoveryManager.start();
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
        View connectedImageView = view.findViewById(R.id.connected_image_view);

        if (device.getId().equals("local")) {
            protocolTextView.setText("Local video player");
            connectedImageView.setVisibility(View.INVISIBLE);
        }
        else {
            String serviceNames = device.getConnectedServiceNames();
            protocolTextView.setText(serviceNames);
            connectedImageView.setVisibility(device.isConnected() ? View.VISIBLE : View.INVISIBLE);
        }

        return view;
    }

    @Override
    public void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
        synchronized (_syncRoot) {
            String deviceId = device.getId();

            for (ConnectableDevice addedDevice: _devices) {
              if (deviceId.equals(addedDevice.getId())) return;
            }

            _devices.add(device);
            notifyDataSetChanged();

            if (_deviceAddedListener != null)
                _deviceAddedListener.onDeviceAdded(device);
        }
    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {
        notifyDataSetChanged();
    }

    @Override
    public void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {
        synchronized (_syncRoot) {
            String deviceId = device.getId();

            for (ConnectableDevice addedDevice: _devices) {
                if (deviceId.equals(addedDevice.getId())) {
                    _devices.remove(device);
                    notifyDataSetChanged();
                }
            }
        }
    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {

    }

    public void setDeviceAddedListener(DeviceAddedListener deviceAddedListener) {
        _deviceAddedListener = deviceAddedListener;
    }
}

