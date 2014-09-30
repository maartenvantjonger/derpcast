package com.mvt.derpcast.device;

import android.os.Build;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.config.ServiceDescription;

import java.util.List;

public class LocalDevice extends ConnectableDevice {

    private boolean _connected;

    public LocalDevice() {
        super();
        setId("local");
        setIpAddress("127.0.0.1");
        setFriendlyName(Build.MODEL);

        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setServiceID("Local Media Player");
        addService(new DeviceService(serviceDescription, null));
    }

    @Override
    public void connect() {
        _connected = true;

        List<ConnectableDeviceListener> listeners = getListeners();
        for (ConnectableDeviceListener listener : listeners)
            listener.onDeviceReady(LocalDevice.this);
    }

    @Override
    public void disconnect() {
        _connected = false;

        List<ConnectableDeviceListener> listeners = getListeners();
        for (ConnectableDeviceListener listener : listeners)
            listener.onDeviceDisconnected(LocalDevice.this);
    }

    @Override
    public boolean isConnected() {
        return _connected;
    }
}
