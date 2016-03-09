package com.mhv.stamprally;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CalibrationActivity extends Activity {

    private static final String TAG = "CalibrationActivity";

    private static final Handler handler = new Handler(Looper.getMainLooper());

	private WifiManager wifiManager;
    private WifiScanReceiver wifiScanReceiver;
    private static final int SCAN_INTERVAL = 1000;
    List<ScanResult> wifiScanResults = new ArrayList<>();
	
	public ArrayList<Stamp> availableStamps = new ArrayList<>();
    public ArrayList<Stamp> calibratedStamps = new ArrayList<>();
	private HashMap<String, Integer> stampData = new HashMap<>();
	public Stamp selectedStamp;

    private GridAdapter gridAdapter;

	private Intent intent;
    private TextView calibratedText;


    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            showWifiAlertDialog("Wi-Fi Disabled", "Please enable Wi-Fi to continue.");
        }
        wifiScanReceiver = new WifiScanReceiver();
		
		//If no previously calibrated stamps are found, set all values to default
        //TODO: User should be able to calibrate/recalibrate stamps anytime
		if (availableStamps.isEmpty()) {
			availableStamps = new ArrayList<>();
			Stamp stamp = new Stamp(R.drawable.wifi_empty, 1, stampData, false);
			availableStamps.add(stamp);
			stamp = new Stamp(R.drawable.wifi_low, 2, stampData, false);
			availableStamps.add(stamp);
			stamp = new Stamp(R.drawable.wifi_mid, 3, stampData, false);
			availableStamps.add(stamp);
			stamp = new Stamp(R.drawable.wifi_full, 4, stampData, false);
			availableStamps.add(stamp);
		}

        GridView calibrationGrid = (GridView) findViewById(R.id.calibrationGrid);
        gridAdapter = new GridAdapter(this);
        calibrationGrid.setAdapter(gridAdapter);
        calibrationGrid.setChoiceMode(GridView.CHOICE_MODE_SINGLE);

        calibrationGrid.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedStamp = (Stamp) gridAdapter.getItem(position);
                //Toast.makeText(CalibrationActivity.this, "Selected stamp: " + selectedStamp.getStampId(), Toast.LENGTH_SHORT).show();
                if(!calibratedStamps.contains(selectedStamp)) {
                    calibrate();
                } else {
                    Toast.makeText(CalibrationActivity.this, "The stamp selected has already been calibrated!", Toast.LENGTH_LONG).show();
                }
            }
        });

        ImageButton okButton = (ImageButton) findViewById(R.id.okButton);
		okButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                intent = new Intent(CalibrationActivity.this, MainActivity.class);
                Bundle savedStampsBundle = new Bundle();
                savedStampsBundle.putParcelableArrayList("calibrated_stamps", calibratedStamps);
                intent.putExtras(savedStampsBundle);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        ImageButton backButton = (ImageButton) findViewById(R.id.backButton);
		backButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED, intent);
                finish();
            }
        });

        calibratedText = (TextView) findViewById(R.id.calibratedText);
        calibratedText.setText("Calibrated stamps: " + calibratedStamps.size());
		
		//Administrator password necessary for calibration
		LayoutInflater layoutInflater = LayoutInflater.from(this);
		View promptView = layoutInflater.inflate(R.layout.password_prompt, null, false);
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setView(promptView);

		final EditText input = (EditText) promptView.findViewById(R.id.passwordField);
		alertDialogBuilder
				.setTitle("Enter administrator password:")
				.setCancelable(false)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								String entered = input.getText().toString();
								String password = "admin";
								
								if (entered.equals(password)) {
									dialog.cancel();
								} else {
									setResult(RESULT_CANCELED, intent);
									finish();
									dialog.cancel();
								}
							}
						})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,	int id) {
								setResult(RESULT_CANCELED, intent);
								finish();
								dialog.cancel();
							}
						});

		AlertDialog alertD = alertDialogBuilder.create();
        alertD.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		alertD.show();
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
    }

    @Override
    protected void onPause() {
        unregisterReceiver(wifiScanReceiver);
        super.onPause();
    }

	public void calibrate() {
        wifiManager.startScan();

        //TODO: Work in process: Multiple scans could be preformed to decrease inaccuracies caused by RSSI fluctuation?
        final ProgressDialog calibrationDialog = ProgressDialog.show(CalibrationActivity.this, "Please wait", "Calibrating Stamp...", true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stampData = new HashMap<>();
                    wifiManager.startScan();
                    Thread.sleep(4000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                calibrationDialog.dismiss();
            }
        }).start();
	}

    private class WifiScanReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
             wifiScanResults = wifiManager.getScanResults();

            for (ScanResult sr : wifiScanResults) {
                stampData.put(sr.BSSID, sr.level);
            }

            selectedStamp.setStampData(stampData);
            selectedStamp.setCalibrated(true);
            calibratedStamps.add(selectedStamp);
            calibratedText.setText("Calibrated stamps: " + calibratedStamps.size());
            Log.d(TAG, "Stamp: " + selectedStamp.getStampData());
        }
    }

    public class GridAdapter extends BaseAdapter {
        private Context context;

        public GridAdapter(Context context) {
            this.context = context;
        }

        public int getCount() {
            return availableStamps.size();
        }

        public Object getItem(int position) {
            return availableStamps.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View view, ViewGroup parent) {

            ImageView imageView;

            if (view == null) {
                imageView = new ImageView(context);
                imageView.setLayoutParams(new GridView.LayoutParams(350,350));
                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                imageView.setPadding(5, 5, 5, 5);
            } else {
                imageView = (ImageView) view;
            }

            imageView.setImageResource(availableStamps.get(position).getImageId());
            return imageView;
        }
    }
}
