package com.mhv.stamprally;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final int STAMP_EDIT = 1;
    private static final int STAMP_FOUND = 3;

    protected WifiManager wifiManager;
    private WifiScanReceiver wifiScanReceiver;
    private List<ScanResult> wifiScanResults;
    private boolean scanPaused = false;
    public static final int SCAN_INTERVAL = 1000;

    private ArrayList<Fingerprint> fingerprints = new ArrayList<>();
    private HashMap<String, Integer> fingerprintData = new HashMap<>();

    private Fingerprint stampInRange;
    private ArrayList<Fingerprint> foundStamps = new ArrayList<>();

    //Activity views.
    private TextView status;
    private TextView inRangeText;
    private ImageButton settingsButton;
    private ImageButton cameraButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.statusText);
        inRangeText = (TextView) findViewById(R.id.inRangeText);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            showExitAlertDialog("Wi-Fi Error", "Wi-Fi not detected on device.");
        } else if (!wifiManager.isWifiEnabled()) {
            showWifiAlertDialog("Wi-Fi Disabled", "Please enable Wi-Fi to continue.");
        }
        wifiScanReceiver = new WifiScanReceiver();

        settingsButton = (ImageButton) findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                scanPaused = true;
                Intent intent = new Intent(MainActivity.this, CalibrationActivity.class);
                Bundle fingerprintsToCalibrate = new Bundle();
                fingerprintsToCalibrate.putParcelableArrayList("to_calibrate", fingerprints);
                intent.putExtras(fingerprintsToCalibrate);
                startActivityForResult(intent, STAMP_EDIT);
            }
        });

        cameraButton = (ImageButton) findViewById(R.id.cameraButton);
        cameraButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //TODO: Camera is supposed to be used to collect the stamps. Act accordingly.
            }
        });
    }

    private void showExitAlertDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                }).show();
    }

    private void showWifiAlertDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                }).setPositiveButton("Enable", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                wifiManager.setWifiEnabled(true);
            }
        }).show();
    }

    @Override
    protected void onResume() {
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        super.onResume();

        if (!fingerprints.isEmpty()) {
            scan();
            status.setText("Scanning for: " + fingerprints.size() + " fingerprints...");

        } else {
            status.setText("No stamps have been calibrated!");
            fingerprints = new ArrayList<>();
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver(wifiScanReceiver);
        super.onPause();
    }

    public void scan() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!scanPaused) {
                    wifiManager.startScan();
                }
            }
        }, SCAN_INTERVAL);
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            wifiScanResults = wifiManager.getScanResults();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch(requestCode) {
            case STAMP_EDIT:
                if(resultCode == RESULT_OK) {
                    Bundle bundle = data.getExtras();
                    fingerprints = bundle.getParcelableArrayList("calibrated_fingerprints");
                    scanPaused = false;
                    scan();
                }
                break;

            case STAMP_FOUND:
                if(resultCode == RESULT_OK) {
                    //TODO: Camera is supposed to be used to collect the stamps. Act accordingly.
                    //N/A
                }
                break;
        }
    }
}
