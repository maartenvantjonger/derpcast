package com.mvt.derpcast.device;

import com.connectsdk.device.ConnectableDevice;

public interface DeviceAddedListener {
    void onDeviceAdded(ConnectableDevice device);
}