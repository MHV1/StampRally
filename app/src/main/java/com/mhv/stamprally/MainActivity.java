package com.mhv.stamprally;

import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final int STAMP_EDIT = 1;
    //private static final int STAMP_FOUND = 2;

    protected WifiManager wifiManager;
    private WifiScanReceiver wifiScanReceiver;
    private List<ScanResult> wifiScanResults;
    private boolean scanPaused = false;
    public static final int SCAN_INTERVAL = 1000;

    private ArrayList<Stamp> stamps = new ArrayList<>();
    private Stamp stampInRange;
    private ArrayList<Stamp> foundStamps = new ArrayList<>();

    //Activity views.
    private TextView status;
    private TextView inRangeText;
    private ImageButton settingsButton;
    private ImageButton stampButton;
    GridView mainGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.statusText);
        inRangeText = (TextView) findViewById(R.id.inRangeText);

        mainGrid = (GridView) findViewById(R.id.mainGrid);
        mainGrid.setAdapter(new GridAdapter(this));

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
                Bundle stampsToCalibrate = new Bundle();
                stampsToCalibrate.putParcelableArrayList("to_calibrate", stamps);
                intent.putExtras(stampsToCalibrate);
                startActivityForResult(intent, STAMP_EDIT);
            }
        });

        stampButton = (ImageButton) findViewById(R.id.stampButton);
        stampButton.setEnabled(false);
        stampButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (stampInRange != null && !foundStamps.contains(stampInRange)) {
                    foundStamps.add(stampInRange);
                } else {
                    Toast.makeText(MainActivity.this, "The stamp in range has already been found!", Toast.LENGTH_LONG).show();
                }
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
                final ProgressDialog calibrationDialog = ProgressDialog.show(MainActivity.this, "Please wait", "Enabling WI-Fi", true);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wifiManager.setWifiEnabled(true);
                            Thread.sleep(4000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        calibrationDialog.dismiss();
                    }
                }).start();
            }
        }).show();
    }

    @Override
    protected void onResume() {
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        super.onResume();

        if (!stamps.isEmpty()) {
            scan();
            status.setText("Scanning for: " + stamps.size() + " stamps...");

        } else {
            status.setText("No stamps have been calibrated!");
            stamps = new ArrayList<>();
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

            /*A temporary Stamp will be generated each scan (1 sec) and compared with
            the calibrated ones in order to get the closest match*/
            HashMap<String, Integer> tempData = new HashMap<>();
            for (ScanResult sr : wifiScanResults) {
                tempData.put(sr.BSSID, sr.level);
            }
            Stamp runtimeStamp = new Stamp(R.drawable.picture_icon, 0, tempData, true);
            //Log.d(TAG, "Runtime Stamp: " + runtimeStamp.getStampData());
            HashMap<Stamp, Double> stampDistances = new HashMap<>();
            for (Stamp calibratedStamp : stamps) {
                double distanceResult = calculateEuclideanDistance(calibratedStamp, runtimeStamp);
                stampDistances.put(calibratedStamp, distanceResult);
                findStampInRange(stampDistances);
            }
        }
    }

    public void findStampInRange(HashMap<Stamp, Double> stampDistances) {
        Map.Entry<Stamp, Double> minEntry = null;

        for(Map.Entry<Stamp, Double> entry : stampDistances.entrySet()) {
            if (minEntry == null || entry.getValue() < minEntry.getValue()) {
                minEntry = entry;
            }
        }

        if(minEntry != null) {
            Log.d(TAG, "" + minEntry.getValue());
            stampInRange = minEntry.getKey();
            inRangeText.setText("" + minEntry.getKey().getStampId());
        }
    }

    //Euclidean distances are calculated in order to find the closest stamp to the runtime one (shortest distance)
    private double calculateEuclideanDistance(Stamp calibratedStamp, Stamp runtimeStamp) {

        //See Euclidean distance formula for Fingerprinting method
        int difference;
        int totalSum = 0;
        double euclideanDistance;

        HashMap<String, Integer> calibratedStampData = calibratedStamp.getStampData();
        HashMap<String, Integer> runtimeStampData = runtimeStamp.getStampData();

        HashSet<String> BSSIDs = new HashSet<>();
        BSSIDs.addAll(calibratedStampData.keySet());
        BSSIDs.addAll(runtimeStampData.keySet());

        for (String BSSID : BSSIDs) {
            Integer calibratedRSSI = calibratedStampData.get(BSSID);
            Integer runtimeRSSI = runtimeStampData.get(BSSID);

            //Avoid null values at all costs!
            if (calibratedRSSI == null) {
                calibratedRSSI = -95;
            }

            if (runtimeRSSI == null) {
                runtimeRSSI = -95;
            }

            difference = calibratedRSSI - runtimeRSSI;
            totalSum += difference;

            Log.d(TAG, "Stamp: " + calibratedStamp.getStampId()
                    + " " + calibratedRSSI
                    + " " + BSSID
                    + " " + runtimeRSSI
                    + " " + BSSID
                    + " " + difference);
        }

        euclideanDistance = Math.sqrt(totalSum * totalSum);
        Log.d(TAG, "" + totalSum + " " + euclideanDistance);
        return euclideanDistance;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch(requestCode) {
            case STAMP_EDIT:
                if(resultCode == RESULT_OK) {
                    Bundle bundle = data.getExtras();
                    stamps = bundle.getParcelableArrayList("calibrated_stamps");
                    scanPaused = false;
                    stampButton.setEnabled(true);
                    scan();
                }
                break;
        }
    }

    //GridView adapter used to display the found stamps.
    public class GridAdapter extends BaseAdapter {
        private Context context;

        public GridAdapter(Context c) {
            context = c;
        }

        public int getCount() {
            return foundStamps.size();
        }

        public Object getItem(int position) {
            return foundStamps.get(position);
        }

        public long getItemId(int position) {
            return 0;
        }

        public View getView(int position, View view, ViewGroup parent) {
            ImageView iview;
            if (view == null) {
                iview = new ImageView(context);
                iview.setLayoutParams(new GridView.LayoutParams(250,250));
                iview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iview.setPadding(5, 5, 5, 5);
            } else {
                iview = (ImageView) view;
            }

            iview.setImageResource(foundStamps.get(position).getImageId());
            return iview;
        }
    }
}
