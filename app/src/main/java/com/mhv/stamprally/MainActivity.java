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
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    //Fingerprint data is mapped using BSSID as key and level (or RSSI) as mapped value
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
                    scan();
                }
            }
        }, SCAN_INTERVAL);
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            wifiScanResults = wifiManager.getScanResults();

            /*A temporary Fingerprint will be generated each scan (1 sec) and compared with
            the calibrated ones in order to get the closest match*/
            HashMap<String, Integer> tempData = new HashMap<>();
            for (ScanResult sr : wifiScanResults) {
                tempData.put(sr.BSSID, sr.level);
            }
            Fingerprint runtimeFingerprint = new Fingerprint(R.drawable.picture_icon, 0, tempData, true);
            //Log.d(TAG, "Runtime Fingerprint: " + runtimeFingerprint.getFingerprintData());
            //TODO: Logic to compare and find closest Fingerprint
            for (Fingerprint calibratedFingerprint : fingerprints) {
                calculateEuclideanDistance(calibratedFingerprint, runtimeFingerprint);
            }
            //TODO: Got the distances. Time to use them to get the fingerprint in range
        }
    }

    //Euclidean distances are calculated in order to find the closest fingerprint to the runtime one (shortest distance)
    private double calculateEuclideanDistance(Fingerprint calibratedFingerprint, Fingerprint runtimeFingerprint) {

        //See Euclidean distance formula for Fingerprinting method
        int difference;
        int totalSum = 0;
        double euclideanDistance;

        HashMap<String, Integer> calibratedFingerprintData = calibratedFingerprint.getFingerprintData();
        HashMap<String, Integer> runtimeFingerprintData = runtimeFingerprint.getFingerprintData();

        HashSet<String> BSSIDs = new HashSet<>();
        BSSIDs.addAll(calibratedFingerprintData.keySet());
        BSSIDs.addAll(runtimeFingerprintData.keySet());

        for (String BSSID : BSSIDs) {
            Integer calibratedRSSI = calibratedFingerprintData.get(BSSID);
            Integer runtimeRSSI = runtimeFingerprintData.get(BSSID);

            //Avoid null values at all costs!
            if (calibratedRSSI == null) {
                calibratedRSSI = -95;
            }

            if (runtimeRSSI == null) {
                runtimeRSSI = -95;
            }

            difference = calibratedRSSI - runtimeRSSI;
            totalSum += difference;

            /*Log.d(TAG, "Fingerprint: " + calibratedFingerprint.getFingerprintId()
                    + " " + calibratedRSSI
                    + " " + BSSID
                    + " " + runtimeRSSI
                    + " " + BSSID
                    + " " + difference);*/
        }

        euclideanDistance = Math.sqrt(totalSum * totalSum);
        //Log.d(TAG, "" + totalSum + " " + euclideanDistance);
        return euclideanDistance;
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
