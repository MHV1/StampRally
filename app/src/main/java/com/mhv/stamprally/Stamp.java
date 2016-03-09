package com.mhv.stamprally;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

/*
*
* Each Stamp represents a Stamp!
*
*/
public class Stamp implements Parcelable {

    private int imageId;
	private int stampId;
	private HashMap<String, Integer> stampData = new HashMap<>(); //Made from AP address and RSSI
	private boolean calibrated;
	
	public Stamp(int imageId, int stampId, HashMap<String, Integer> stampData, boolean calibrated) {
        this.imageId = imageId;
		this.stampId = stampId;
		this.stampData = stampData;
		this.calibrated = calibrated;
	}

    public void setImageId(int imageId) {
        this.imageId = imageId;
    }

    public int getImageId() {
        return imageId;
    }

    public int getStampId() {
	    return stampId;
	}
	
	public void setStampData(HashMap<String, Integer> stampData) {
		this.stampData = stampData;
	}
	
	public HashMap<String, Integer> getStampData() {
		return stampData;
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
        dest.writeInt(this.imageId);
        dest.writeInt(this.stampId);

        //Android cannot write HashMaps to Parcels directly, so we have to work around that.
        dest.writeInt(stampData.size());
        for (Map.Entry<String, Integer> entry : stampData.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeInt(entry.getValue());
        }

        dest.writeByte((byte) (calibrated ? 1 : 0));     //if myBoolean == true, byte == 1
    }

    public Stamp(Parcel source) {
        this.imageId = source.readInt();
        this.stampId = source.readInt();

        //Android cannot read HashMaps to Parcels directly either, so we have to work around that.
        int size = source.readInt();
        for (int i = 0; i < size; i++) {
            String key = source.readString();
            int value = source.readInt();
            this.stampData.put(key, value);
        }

        this.calibrated = source.readByte() != 0;     //myBoolean == true if byte != 0
    }

    public static final Parcelable.Creator<Stamp> CREATOR = new Creator<Stamp>() {

        @Override
        public Stamp createFromParcel(Parcel source) {
            return new Stamp(source);
        }

        @Override
        public Stamp[] newArray(int size) {
            return new Stamp[size];
        }
    };
}
