package com.example.chongshao.imagebeacondemoclient;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    private DeviceAdapter mDeviceAdapter;
    private boolean mIsScanning;
    private BluetoothAdapter mBTAdapter;
    private Button scanButton;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ask permissions
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect beacons.");
            builder.setPositiveButton("OK", null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            // Android M permission check
//            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                final AlertDialog.Builder builder = new AlertDialog.Builder(this); 
//                builder.setTitle("This app needs location access");
//                builder.setMessage("Please grant location access so this app can detect beacons.");
//                builder.setPositiveButton(android.R.string.ok, null); 
//                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {  
//                    @Override 
//                    public void onDismiss(DialogInterface dialog) {
//                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION); 
//                    }  
//                }); 
//                builder.show(); 
//            }
//        }

        setContentView(R.layout.activity_main);

        ImageView imageView = (ImageView)this.findViewById(R.id.imageView);

        //imageView.setImageResource(R.drawable.images);
        String csvText = this.readFromCSV("foo");
        Mat m = this.csvTextToMat(csvText, 64, 64);
        Mat img = this.imageFromDCTMat(m, 64, 64);
        Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC1));
        Utils.matToBitmap(img, bmp);
        imageView.setImageBitmap(bmp);

        scanButton = (Button)this.findViewById(R.id.scanButton);
        scanButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Log.d("DDL", "scan button clicked");
                startScan();
            }
        });

        init();
    }

    public Mat imageFromDCTMat(Mat dctMat, int w, int h) {
        Mat img = new Mat(w, h, CvType.CV_8UC1);
        Core.idct(dctMat, img);
        Mat img2=  new Mat();
        img.convertTo(img2, CvType.CV_8UC1);
        return img2;
    }

    public String readFromCSV(String filename) {
        InputStream inputStream = getApplicationContext().getResources().openRawResource(R.raw.android_icon);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = null;
        String text = "";

        try {
            line = reader.readLine();
            while (line != null) {
                Log.d("DDL", line);
                if (line != null) {
                    text = text.concat(line);
                    text = text.concat("\n");
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text;
    }

    public Mat csvTextToMat(String csvText, int h, int w) {
        Mat m = new Mat(64, 64, CvType.CV_64FC1);
        // split the csvtext
        String[] lines = csvText.split("\n");
        Log.d("DDDL", Integer.toString(lines.length));
        int row = 0;
        for (String line : lines) {
            String[] elements = line.split(",");
            for (int i = 0; i < w; i++) {
                m.put(row, i, Float.parseFloat(elements[i]));
            }
            row = row + 1;
        }
        Log.d("DDL:", m.dump());
        Log.d("DDL:", Float.toString((float)m.get(63, 0)[0]));
        return m;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
    }

    private void init() {
        if (!BleUtil.isBLESupported(this)) {
      //      Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            Log.d("DDDL:", "BLE not supported");
      //      finish();
     //       return;
        }

        // BT check
        BluetoothManager manager = BleUtil.getManager(this);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
         //   Toast.makeText(this, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
            Log.d("DDDL:", "BT unavailable");
    //        finish();
     //       return;
        }
    }

    private void startScan() {
        if ((mBTAdapter != null) && (!mIsScanning)) {
            mBTAdapter.startLeScan(this);
            mIsScanning = true;
            setProgressBarIndeterminateVisibility(true);
            invalidateOptionsMenu();
        }
    }

    private void stopScan() {
        if (mBTAdapter != null) {
            mBTAdapter.stopLeScan(this);
        }
        mIsScanning = false;
        setProgressBarIndeterminateVisibility(false);
        invalidateOptionsMenu();
    }

    // scanning methods
    @Override
    public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//               mDeviceAdapter.update(device, rssi, scanRecord);
//            }
//        });
        Log.d("Ddl:", ByteArrayToString(scanRecord) + Integer.toString(rssi));
    }



    public static String ByteArrayToString(byte[] ba)
    {
//        StringBuilder hex = new StringBuilder(ba.length * 2);
//        for (byte b : ba)
//            hex.append(b + " ");
//
//        return hex.toString();
        String msg = "payload = ";
        for (byte b : ba)
            msg += String.format("%02x ", b);

         return msg;
    }


    // permission
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("DDL", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("SInce location access has not been granted,this app will not be able to discover beacons when in the background;");
                    builder.setPositiveButton("OK", null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }
}