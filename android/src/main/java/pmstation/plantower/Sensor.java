/*
 * pm-home-station
 * 2017 (C) Copyright - https://github.com/rjaros87/pm-home-station
 * License: GPL 3.0
 */

package pmstation.plantower;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pmstation.MainActivity;
import pmstation.core.plantower.IPlanTowerObserver;
import pmstation.core.plantower.ParticulateMatterSample;
import pmstation.core.plantower.PlanTowerDevice;

public class Sensor {
    private static final int BAUD_RATE = 9600;
    private static final String TAG = "Sensor";
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;
    private boolean serialPortConnected;
    private boolean running = true;
    private boolean requested = false;
    private Context context;
    private SharedPreferences preferences;
    private List<IPlanTowerObserver> valueObservers = new ArrayList<>();
    private int sampleCounter = 0;

    private UsbSerialInterface.UsbReadCallback readCallback = bytes -> {
        final ParticulateMatterSample sample = PlanTowerDevice.parse(bytes);
        if (sample != null) {
            notifyAllObservers(sample);
        }
        try {
            sampleCounter = ++sampleCounter % 10;
            String intervalPref = preferences.getString("sampling_interval", "1000");
            int interval = Integer.parseInt(intervalPref);
            if (interval > 3000) {
                if (sampleCounter == 0) {
                    sleep();
                    Thread.sleep(interval);
                    wakeUp();
                } else {
                    Thread.sleep(1000);
                }
            } else {
                Thread.sleep(interval);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    };
    private Thread fakeDataThread;

    public Sensor(Context context) {
        this.context = context;
        this.serialPortConnected = false;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void findSerialPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003)) {
                    // There is a device connected to our Android device. Try to open it as a Serial Port.
                    requestUserPermission();
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep) {
                    break;
                }
            }
        }
    }

    private void notifyAllObservers(final ParticulateMatterSample sample) {
        for (IPlanTowerObserver valueObserver : valueObservers) {
            valueObserver.update(sample);
        }
    }

    public void clearPermissionReqestFlag() {
        requested = false;
    }

    public void addValueObserver(IPlanTowerObserver observer) {
        valueObservers.add(observer);
    }

    public void removeValueObserver(IPlanTowerObserver observer) {
        valueObservers.remove(observer);
    }

    public boolean connectDevice() {
        connection = usbManager.openDevice(device);

        serialPortConnected = connection != null;
        if (serialPortConnected) {
            new ConnectionThread().start();
        }

        return serialPortConnected;
    }

    public void disconnectDevice() {
        serialPort.close();
        serialPortConnected = false;
    }

    public void write(byte[] data) {
        if (serialPort != null) {
            serialPort.write(data);
        }
    }

    /**
     * Attempts to put the sensor in sleep mode.
     *
     * @return whether the sensor is connected or not
     */
    public boolean sleep() {
        if (running) {
            write(PlanTowerDevice.MODE_SLEEP);
            running = false;
        }
        return isConnected();
    }

    /**
     * Attempts to wake the sensor from sleep mode.
     *
     * @return whether the sensor is connected or not
     */
    public boolean wakeUp() {
        if (!running) {
            write(PlanTowerDevice.MODE_WAKEUP);
            running = true;
        }
        return isConnected();
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private void requestUserPermission() {
        if (!requested) {
            requested = true;
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(device, pendingIntent);
        }
    }

    public boolean isConnected() {
        return serialPortConnected;
    }

    public void startFakeDataThread() {
        fakeDataThread = new Thread(() -> {
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                i = (i + 1) % 14;
                try {
                    notifyAllObservers(new ParticulateMatterSample(20, i * 10, 100));
                    String intervalPref = preferences.getString("sampling_interval", "1000");
                    int interval = Integer.parseInt(intervalPref);
                    Thread.sleep(sampleCounter == 0 ? interval : 1000);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Thread interrupted");
                    Thread.currentThread().interrupt();
                }
            }
        });
        fakeDataThread.start();
    }

    public void stopFakeDataThread() {
        fakeDataThread.interrupt();
    }

    /*
    * A simple thread to open a serial port.
    * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
    */
    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
            if (serialPort != null) {
                if (serialPort.open()) {
                    serialPortConnected = true;
                    serialPort.setBaudRate(BAUD_RATE);
                    serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                    serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    serialPort.read(readCallback);
                    return;
                }
                Log.e(TAG, "USB not working");
            }
        }
    }
}
