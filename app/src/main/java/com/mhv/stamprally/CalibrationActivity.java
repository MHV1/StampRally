package com.mhv.stamprally;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/*
*
* Each Fingerprint represents a Stamp!
*
*/
public class CalibrationActivity extends Activity {

    private static final String TAG = "CalibrationActivity";

    private static final Handler handler = new Handler(Looper.getMainLooper());

	private WifiManager wifiManager;
    private WifiScanReceiver wifiScanReceiver;
    private List<ScanResult> wifiScanResults;
    private static final int SCAN_INTERVAL = 1000;
	
	public ArrayList<Fingerprint> availableFingerprints = new ArrayList<>();
    public ArrayList<Fingerprint> calibratedFingerprints = new ArrayList<>();
	private HashMap<String, Integer> fingerprintData = new HashMap<>(); //Made from AP address and RSSI
	public Fingerprint selectedFingerprint;

    private GridView calibrationGrid;
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
		
		//If no previously calibrated fingerprints are found, set all values to default
        //TODO: User should be able to calibrate/recalibrate fingerprints anytime
		if (availableFingerprints.isEmpty()) {
			availableFingerprints = new ArrayList<>();
			Fingerprint fingerprint = new Fingerprint(R.drawable.picture_icon, 1, fingerprintData, false);
			availableFingerprints.add(fingerprint);
			fingerprint = new Fingerprint(R.drawable.picture_icon, 2, fingerprintData, false);
			availableFingerprints.add(fingerprint);
			fingerprint = new Fingerprint(R.drawable.picture_icon, 3, fingerprintData, false);
			availableFingerprints.add(fingerprint);
			fingerprint = new Fingerprint(R.drawable.picture_icon, 4, fingerprintData, false);
			availableFingerprints.add(fingerprint);
		}

        calibrationGrid = (GridView) findViewById(R.id.calibrationGrid);
        gridAdapter = new GridAdapter(this);
        calibrationGrid.setAdapter(gridAdapter);
        calibrationGrid.setChoiceMode(GridView.CHOICE_MODE_SINGLE);

        calibrationGrid.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedFingerprint = (Fingerprint) gridAdapter.getItem(position);
                Toast.makeText(CalibrationActivity.this, "Selected fingerprint: " + selectedFingerprint.getFingerprintId(), Toast.LENGTH_SHORT).show();
                calibrate();
            }
        });

        ImageButton okButton = (ImageButton) findViewById(R.id.okButton);
		okButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                intent = new Intent(CalibrationActivity.this, MainActivity.class);
                Bundle savedFingerprintsBundle = new Bundle();
                savedFingerprintsBundle.putParcelableArrayList("calibrated_fingerprints", calibratedFingerprints);
                intent.putExtras(savedFingerprintsBundle);
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
        calibratedText.setText("Calibrated fingerprints: " + calibratedFingerprints.size());
		
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
        fingerprintData = new HashMap<>();
        //TODO: Multiple scans could be preformed to decrease inaccuracies caused by RSSI fluctuation.
        wifiManager.startScan();
	}

    private class WifiScanReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            wifiScanResults = wifiManager.getScanResults();

            for (ScanResult sr : wifiScanResults) {
                fingerprintData.put(sr.BSSID, sr.level);
            }

            selectedFingerprint.setFingerprintData(fingerprintData);
            selectedFingerprint.setCalibrated(true);
            calibratedFingerprints.add(selectedFingerprint);
            calibratedText.setText("Calibrated fingerprints: " + calibratedFingerprints.size());
            Log.d(TAG,"Fingerprint: " + selectedFingerprint.getFingerprintData());
        }
    }

    public class GridAdapter extends BaseAdapter {
        private Context context;

        public GridAdapter(Context context) {
            this.context = context;
        }

        public int getCount() {
            return availableFingerprints.size();
        }

        public Object getItem(int position) {
            return availableFingerprints.get(position);
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

            imageView.setImageResource(availableFingerprints.get(position).getStampId());
            return imageView;
        }
    }
}
