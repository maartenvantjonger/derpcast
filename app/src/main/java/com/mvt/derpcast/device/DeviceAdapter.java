package com.mvt.derpcast.device;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.discovery.CapabilityFilter;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.discovery.provider.CastDiscoveryProvider;
import com.connectsdk.discovery.provider.SSDPDiscoveryProvider;
import com.connectsdk.discovery.provider.ZeroconfDiscoveryProvider;
import com.connectsdk.service.AirPlayService;
import com.connectsdk.service.CastService;
import com.connectsdk.service.DLNAService;
import com.connectsdk.service.RokuService;
import com.connectsdk.service.WebOSTVService;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.command.ServiceCommandError;
import com.mvt.derpcast.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends BaseAdapter implements DiscoveryManagerListener {

    private List<ConnectableDevice> mDevices = new ArrayList<>();
    private DeviceAddedListener mDeviceAddedListener;

    public DeviceAdapter(Context context) {
        DiscoveryManager.init(context);
        DiscoveryManager discoveryManager = DiscoveryManager.getInstance();
        discoveryManager.addListener(DeviceAdapter.this);
        discoveryManager.setCapabilityFilters(new CapabilityFilter(MediaPlayer.Display_Video));
        discoveryManager.setPairingLevel(DiscoveryManager.PairingLevel.ON);
        discoveryManager.registerDeviceService(WebOSTVService.class, SSDPDiscoveryProvider.class);
        discoveryManager.registerDeviceService(DLNAService.class, SSDPDiscoveryProvider.class);
        discoveryManager.registerDeviceService(RokuService.class, SSDPDiscoveryProvider.class);
        discoveryManager.registerDeviceService(CastService.class, CastDiscoveryProvider.class);
        discoveryManager.registerDeviceService(AirPlayService.class, ZeroconfDiscoveryProvider.class);
        discoveryManager.start();
    }

    @Override
    public int getCount() {
        return mDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return getDevice(i);
    }

    public ConnectableDevice getDevice(int i) {
        return mDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, final ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            view = inflater.inflate(R.layout.view_device_item, viewGroup, false);
        }

        final ConnectableDevice device = getDevice(i);

        TextView titleTextView = (TextView) view.findViewById(R.id.device_title_text_view);
        titleTextView.setText(device.getFriendlyName());

        TextView protocolTextView = (TextView) view.findViewById(R.id.protocol_text_view);
        String serviceNames = device.getConnectedServiceNames();
        protocolTextView.setText(serviceNames);

        View connectedImageView = view.findViewById(R.id.connected_image_view);
        connectedImageView.setVisibility(device.isConnected() ? View.VISIBLE : View.INVISIBLE);

        return view;
    }

    @Override
    public synchronized void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
        String deviceId = device.getId();

        for (ConnectableDevice addedDevice : mDevices) {
            if (deviceId.equals(addedDevice.getId())) return;
        }

        mDevices.add(device);
        notifyDataSetChanged();

        if (mDeviceAddedListener != null)
            mDeviceAddedListener.onDeviceAdded(device);
    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {
        notifyDataSetChanged();
    }

    @Override
    public synchronized void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {
        String deviceId = device.getId();
        ConnectableDevice deviceToRemove = null;

        for (ConnectableDevice currentDevice : mDevices) {
            if (deviceId.equals(currentDevice.getId())) {
                deviceToRemove = currentDevice;
            }
        }

        if (deviceToRemove != null) {
            mDevices.remove(deviceToRemove);
            notifyDataSetChanged();
        }
    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {

    }

    public void setDeviceAddedListener(DeviceAddedListener deviceAddedListener) {
        mDeviceAddedListener = deviceAddedListener;
    }
}

