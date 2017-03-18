package com.example.chongshao.imagebeacondemoclient;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import java.util.List;

/**
 * Created by chongshao on 3/18/17.
 */

public class MyScanCallBack extends ScanCallback {

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        Log.d("DDLCB", "find some beacons");
    }

}
