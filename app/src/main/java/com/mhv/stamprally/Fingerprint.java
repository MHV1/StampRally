package com.mhv.stamprally;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

public class Fingerprint implements Parcelable {
	
	private int fingerprintId;
	private HashMap<String, Integer> fingerprintData = new HashMap<>(); //Made from AP address and RSSI
	private boolean calibrated;
	
	public Fingerprint(int fingerprintId, HashMap<String, Integer> fingerprintData, boolean calibrated) {
		this.fingerprintId = fingerprintId;
		this.fingerprintData = fingerprintData;
		this.calibrated = calibrated;
	}
	
	public int getFingerprintId() {
	    return fingerprintId;
	}
	
	public void setFingerprintData(HashMap<String, Integer> fingerprintData) {
		this.fingerprintData = fingerprintData;
	}
	
	public HashMap<String, Integer> getFingerprintData() {
		return fingerprintData;
	}
	
	public void setCalibrated (boolean calibrated) {
		this.calibrated = calibrated;
	}
	
	public boolean getCalibrated() {
		return calibrated;
	}

	@Override
	public int describeContents() {
		return hashCode();
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(this.fingerprintId);
		
		//Android cannot write HashMaps to Parcels directly, so we have to work around that.
		dest.writeInt(fingerprintData.size());
		for (Map.Entry<String, Integer> entry : fingerprintData.entrySet()) {
			dest.writeString(entry.getKey());
			dest.writeInt(entry.getValue());
		}
		
		dest.writeByte((byte) (calibrated ? 1 : 0));     //if myBoolean == true, byte == 1
	}
	
	public Fingerprint(Parcel source) {
		this.fingerprintId = source.readInt();
		
		//Android cannot read HashMaps to Parcels directly either, so we have to work around that.
		int size = source.readInt();
		for (int i = 0; i < size; i++) {
			String key = source.readString();
			int value = source.readInt();
			this.fingerprintData.put(key, value);
		}
		
		this.calibrated = source.readByte() != 0;     //myBoolean == true if byte != 0
	}
	
	public static final Creator<Fingerprint> CREATOR = new Creator<Fingerprint>() {

		@Override
		public Fingerprint createFromParcel(Parcel source) {
			return new Fingerprint(source);
		}

		@Override
		public Fingerprint[] newArray(int size) {
			return new Fingerprint[size];
		}
	};
}
