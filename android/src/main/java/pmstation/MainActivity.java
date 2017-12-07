package pmstation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import pmstation.core.plantower.IPlanTowerObserver;
import pmstation.core.plantower.ParticulateMatterSample;
import pmstation.plantower.Sensor;

public class MainActivity extends AppCompatActivity implements IPlanTowerObserver {
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_PERMISSION = "pmstation.USB_PERMISSION";
    private static final String TAG = "MainActivity";
    private Menu menu;
    private ImageView smog;

    private List<ParticulateMatterSample> values = new ArrayList<>();

    private boolean running = false;
    private Sensor sensor;
    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     * About BroadcastReceiver: http://developer.android.com/reference/android/content/BroadcastReceiver.html
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (intent.getAction()) {
                case ACTION_USB_PERMISSION:
                    sensor.clearPermissionReqestFlag();
                    Bundle extras = intent.getExtras();
                    if (extras == null) {
                        return;
                    }
                    boolean granted = extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                    if (granted) { // User accepted our USB connection. Try to open the device as a serial port
                        if (sensor.connectDevice()) {
                            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            setStatus(true);
                        }
                    } else { // User not accepted our USB connection.
                        setStatus(false);
                    }
                    break;
                case ACTION_USB_ATTACHED:
                    if (!sensor.isConnected()) {
                        sensor.findSerialPortDevice(); // A USB device has been attached. Try to open it as a Serial port
                    }
                    break;
                case ACTION_USB_DETACHED:
                    // Usb device was disconnected.
                    setStatus(false);
                    if (sensor.isConnected()) {
                        sensor.disconnectDevice();
                    }
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
            }
        }
    };

    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    public static void tintMenuItem(MenuItem item) {
        Drawable icon = item.getIcon();
        icon.mutate();
        icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setContentView(R.layout.activity_main);

        smog = findViewById(R.id.smog);
        smog.setAlpha(0f);

        sensor = new Sensor(this);
        sensor.addValueObserver(this);

        if (savedInstanceState != null) {
            return;
        }

        // Create a new Fragment to be placed in the activity layout
        Fragment valuesFragment = new ValuesFragment();

        // In case this activity was started with special instructions from an
        // Intent, pass the Intent's extras to the fragment as arguments
        valuesFragment.setArguments(getIntent().getExtras());

        // Add the fragment to the 'fragment_container' FrameLayout
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, valuesFragment).commit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isEmulator()) {
            sensor.startFakeDataThread();
        }
    }

    @Override
    protected void onStop() {
        if (isEmulator()) {
            sensor.stopFakeDataThread();
        }
        sensor.sleep();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Registering receiver");
        registerReceiver();
        sensor.findSerialPortDevice();
        if (isRunning()) {
            sensor.wakeUp();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Unregistering receiver");
        unregisterReceiver(usbReceiver);
    }

    public List<ParticulateMatterSample> getValues() {
        return values;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_status, menu);
        this.menu = menu;
        setStatus(isRunning());

        int[] arr = {R.id.action_chart, R.id.action_connected, R.id.action_disconnected};
        for (int i : arr) {
            MenuItem item = menu.findItem(i);
            if (item != null) {
                MainActivity.tintMenuItem(item);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_chart:
                showChart();
                return true;
            case R.id.action_connected:
                Log.d(TAG, "Trying to disconnect");
                if (sensor.sleep()) {
                    setStatus(false);
                }
                return true;
            case R.id.action_disconnected:
                Log.d(TAG, "Trying to connect");
                if (sensor.wakeUp()) {
                    setStatus(true);
                }
                return true;
            case R.id.action_settings:
                getFragmentManager().beginTransaction()
                                    .replace(android.R.id.content, new SettingsFragment()).addToBackStack(null)
                                    .commit();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showChart() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("chartFragment");
        if (fragment != null && !fragment.isDetached()) {
            return;
        }
        ChartFragment chartFragment = new ChartFragment();
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, chartFragment, "chartFragment").addToBackStack(null)
                           .commit();
    }

    void setStatus(boolean connected) {
        this.running = connected;
        if (menu == null) {
            return;
        }
        menu.getItem(0).setVisible(connected);
        menu.getItem(1).setVisible(!connected);
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }

    @Override
    public void update(ParticulateMatterSample sample) {
        values.add(sample);
        AQIColor pm25Color = AQIColor.fromPM25Level(sample.getPm2_5());
        smog.animate().alpha(pm25Color.getAlpha());
    }

    public Sensor getSensor() {
        return sensor;
    }
}
