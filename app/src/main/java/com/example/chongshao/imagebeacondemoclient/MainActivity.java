package com.example.chongshao.imagebeacondemoclient;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import org.opencv.core.Scalar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.Inflater;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.view.View;
import android.widget.TextView;


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
    private Button testButton;
    private Button scanButtonColor;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private HashMap<Integer, String> inputData;
    private boolean doneGettingData;
    private int maxSize;
    private boolean decodeColor;
    private int packetCount;

    private TextView progress;

    ImageView imageView;

    private ScanCallback callBack;

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

        imageView = (ImageView)this.findViewById(R.id.imageView);
        String csvText = this.readFromCSV("foo");
        Mat m = this.csvTextToMat(csvText, 64, 64);
        Mat img = this.imageFromDCTMat(m, 64, 64);
        Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC1));
        Utils.matToBitmap(img, bmp);
        imageView.setImageBitmap(bmp);

        decodeColor = false;

        scanButton = (Button)this.findViewById(R.id.scanButton);
        scanButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDL", "scan button clicked");
                decodeColor = false;
                startScan();
            }
        });

        scanButtonColor = (Button)this.findViewById(R.id.scanButtonColor);
        scanButtonColor.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDL", "color scan button clicked!");
                decodeColor = true;
                startScan();
            }
        });

        testButton = (Button)this.findViewById(R.id.testButton);
     //   Mat res;
        testButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDL", "test button clicked");
                Mat res = decodeByteArray();
                Log.d("DDL", "res: " + res.dump());

                Mat img = imageFromDCTMat(res, 64, 64);
                Log.d("DDL", "bitmap:" + img.dump());
                Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC1));
                Utils.matToBitmap(img, bmp);
                imageView.setImageBitmap(bmp);
            }
        });

        // init hashmap
        inputData = new HashMap<>();
        doneGettingData = false;
        maxSize = 0;

        progress = (TextView)this.findViewById(R.id.progress);
        packetCount = 0;


        callBack = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (callbackType != ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                    // Should not happen.
                    Log.e("DDL", "LE Scan has already started");
                    return;
                }
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord == null) {
                    return;
                }
//                if (serviceUuids != null) {
//                    List<ParcelUuid> uuids = new ArrayList<ParcelUuid>();
//                    for (UUID uuid : serviceUuids) {
//                        uuids.add(new ParcelUuid(uuid));
//                    }
//                    List<ParcelUuid> scanServiceUuids = scanRecord.getServiceUuids();
//                    if (scanServiceUuids == null || !scanServiceUuids.containsAll(uuids)) {
//                        if (DBG) Log.d("DDL", "uuids does not match");
//                        return;
//                    }
//                }
                onLeScan(result.getDevice(), result.getRssi(),
                        scanRecord.getBytes());
            }
        };

        init();
    }

    public int maxInMat(Mat mat, int w, int h) {
        int max = 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (mat.get(j, i)[0] > max) {
                    max = (int)mat.get(j, i)[0];
                }
            }
        }
        return max;
    }
    public int minInMat(Mat mat, int w, int h) {
        int min = 255;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (mat.get(j, i)[0] < min) {
                    min = (int)mat.get(j, i)[0];
                }
            }
        }
        return min;
    }


    public Mat enhance(Mat mat, int max, int w, int h) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (mat.get(j, i)[0] > max) {
                    mat.put(j, i, (int)(mat.get(j,i)[0] * 255.0 / max));
                }
            }
        }
        return mat;
    }


    public Mat imageFromDCTMat(Mat dctMat, int w, int h) {
        Mat img2 = new Mat();
        if (!decodeColor) {
            Mat img = new Mat(w, h, CvType.CV_8UC1);
            Core.idct(dctMat, img);
            Scalar alpha = new Scalar(255); // the factor
            Core.multiply(img, alpha, img);
            int maxValue = maxInMat(img, w, h);
            int minValue = minInMat(img, w, h);
            Scalar minS = new Scalar(-1 * minValue);
            Core.add(img, minS, img);
            Log.d("DDL", "img1" + img.dump());
            img.convertTo(img2, CvType.CV_8UC1);
        } else {
            Mat imgr = new Mat(w, h, CvType.CV_8UC1);
            Mat imgg = new Mat(w, h, CvType.CV_8UC1);
            Mat imgb = new Mat(w, h, CvType.CV_8UC1);

            List<Mat> lRgb = new ArrayList<>(3);
            Core.split(dctMat, lRgb);
            Core.idct(lRgb.get(0), imgr);
            Core.idct(lRgb.get(1), imgg);
            Core.idct(lRgb.get(2), imgb);

            Scalar alpha = new Scalar(255); // the factor
            Core.multiply(imgr, alpha, imgr);
            Core.multiply(imgg, alpha, imgg);
            Core.multiply(imgb, alpha, imgb);

            int minValuer = minInMat(imgr, w, h);
            int minValueg = minInMat(imgg, w, h);
            int minValueb = minInMat(imgb, w, h);

            Scalar minSr = new Scalar(-1 * minValuer);
            Core.add(imgr, minSr, imgr);

            Scalar minSg = new Scalar(-1 * minValueg);
            Core.add(imgg, minSg, imgg);

            Scalar minSb = new Scalar(-1 * minValueb);
            Core.add(imgb, minSb, imgb);

            Log.d("DDL", "img1r" + imgr.dump());
            Mat imgr2 = new Mat();
            Mat imgg2 = new Mat();
            Mat imgb2 = new Mat();

            imgr.convertTo(imgr2, CvType.CV_8UC1);
            imgg.convertTo(imgg2, CvType.CV_8UC1);
            imgb.convertTo(imgb2, CvType.CV_8UC1);
            lRgb.set(0, imgb2);
            lRgb.set(1, imgg2);
            lRgb.set(2, imgr2);
            Core.merge(lRgb, img2);
        }
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
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build();

            mBTAdapter.getBluetoothLeScanner().startScan(null, settings, callBack);

//            mBTAdapter.startLeScan(this);

            mIsScanning = true;
            setProgressBarIndeterminateVisibility(true);
            invalidateOptionsMenu();
        }
    }

    private void stopScan() {
        packetCount = 0;
        progress.setText("");
        if (mBTAdapter != null) {
        //    mBTAdapter.stopLeScan(this);
            mBTAdapter.getBluetoothLeScanner().stopScan(callBack);
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
        if (rssi > -60 && device.getName() == null) {
            String scanRecordString = ByteArrayToString(scanRecord);
            if (scanRecordString.substring(19,21).equals("1a") && scanRecordString.substring(16, 18).equals("1a")) {
            Log.d("Ddl", scanRecordString + Integer.toString(rssi) + "name:" +device.getName());
            retrieveByteArray(scanRecordString.split(" = ")[1]);
               if (doneGettingData) {
                   String resultString = getInputStringFromHashMap();
                   Log.d("ddl", "done getting data: " + resultString);
                   Mat resultDCTMap = decodeByteArray(resultString);
                   Log.d("DDL", "res: " + resultDCTMap.dump());

                   Mat img = imageFromDCTMat(resultDCTMap, 64, 64);
                   Log.d("DDL", "bitmap:" + img.dump());
                   Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                   Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC1));
                   Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC3));
                   Utils.matToBitmap(img, bmp);
                   imageView.setImageBitmap(bmp);
                   stopScan();
                   reset();
                }
            }
        }
    }

    private void reset() {
        doneGettingData = false;
        progress.setText("");
        packetCount = 0;
        inputData = new HashMap<>();
    }
    public static String ByteArrayToString(byte[] ba) {
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

    public void retrieveByteArray(String byteArray) {
        String[] bytes = byteArray.split(" ");
        int currIndex = (int)((byte)(Byte.valueOf(bytes[9], 16)) & 0xff);
        if (currIndex > maxSize) {
            maxSize = currIndex;
        }
        if (inputData.containsKey(new Integer(currIndex))) {
            if (containsAll()) {
                doneGettingData = true;
            }
        } else {
            packetCount = packetCount + 1;
            if (!decodeColor) {
                progress.setText(String.format("%d%%", (int)((double)packetCount * 100.0 / 18)));
            }
            else {
                progress.setText(String.format("%d%%", (int)((double)packetCount * 100.0 / 52)));
            }
        }
        inputData.put(new Integer(currIndex), byteArray.substring(30,90));
    }

    private boolean containsAll() {
        Set<Integer> keySet = inputData.keySet();
        for (int i = 1; i <= maxSize; i++) {
            if (! keySet.contains(new Integer(i))) {
                return false;
            }
        }
        return true;
    }

    public String getInputStringFromHashMap() {
        String resultString = "";
        for (int i = 1; i <= maxSize; i++) {
            String currString = inputData.get(new Integer(i));
            resultString += currString;
        }
        return resultString;
    }

    // decoding
    public Mat decodeByteArray() {
        byte[] result = new byte[4096];

        try {
            // Encode a String into bytes
            //  String inputString = "x\\x9c\\xed\\x93\\xd9N\\xc30\\x10Eg\\xb1\\x93\\xb6,\\x12\\x9b\\xe0K\\xf8\\xff\\xbf\\xe1\\t\\xa9T\\x14R/3\\x8c\\xdbJ-\\xf0P;E\\xe2%G\\x91\\x95\\x87\\\\\\xdf;K\\x9e_\\xc2\\xe3\\xc3Z\\x16i\\xbd|}\\x8fC\\x02b\\xd7\\xb9\\xf2\\xa0\\xa4(p\\x8a\\x9b\\xbb\\xb7\\x18{\\x8e\\x17\\xb4`\\x10 EH\\n\\xc8D\\x08tRm\\xf4\\xab\\xfb\\xd5\\x13$G\\x91\\xbb`'\\xa0 \\x05\\x91p\\xda\\xbb\\x90\\xd2\\xed\\x8a\\xe7\\xcb\\x19\\x0c\\x0e\\xc8\\x9b\\xc6lQA\\x19\\xd8\\xd5\\xe8\\xa3\\xf7\\x1eXis\\ry\\x16\\xbado\\xbd\\x00\\xe7\\x0cu\\xfe \\x97\\xf8\\x91gWC\\xf7\\xe93\\x11\\xd8\\x01\\xd6\\x03!\\xad\\x93\\xf7\\xb1K\\x19\\xe6\\x83\\xcb\\xda\\rV:PR\\xc9u\\xda\\xc2,d\\xaf\\xec6\\x82V?G\\x04u\\xd6\\xc1\\xba\\xd6\\x17JV\\x96$\\x9d8\\xd9\\xf8\\xcc\\xd1\\x82S\\x83?fs\\x0c\\xd6\\xf3\\x80\\xc9\\x81\\x82U`\\xe3\\xaf\\xd7\\x8b\\x99q`\\xce\\x89\\xadq\\xb9\\\\\\xa1\\rr\\xc8\\xc8\\xd1F\\xa5e\\xe6\\x9a<\\xa4\\xe2\\xdf\\x84}NVE\\xd1\\x91\\n`\\xd5\\xda\\x1c\\xc9\\x85M*d\\x95\\x97VbKx\\xc3o\\xd7L\\x95\\xf7\\t*\\xb7\\xee`O\\xdbn\\xe3\\xee\\x92F\\xf3\\xed\\x05(\\x16\\xdef\\x86X\\xbd\\xf3G\\x14G\\x82\\xed\\xb6g?\\xc2\\x1e!\\xef\\xc27\\xcfmGi\\xd9\\x08\\xdb\\xefW\\xec\\x93\\x8cad\\xec\\x83\\xfe<\\xb9\\xf5\\rGL\\xedH\\xdf\\xf4\\xb7\\xfd\\xc6\\xd4\\xad+\\xff3\\xc1\\x99\\xea3\\xa7\\xff7\\xcb\\xf3_\\xf6\\x13\\x13\\x13\\x13\\x13\\x13\\x13\\x13\\xcd|\\x01+8\\xa8a";
            String inputString = "120 156 237 147 217 78 195 48 16 69 103 177 147 182 44 18 155 224 75 248 255 191 225 9 169 84 20 82 47 51 140 219 74 45 240 80 59 69 226 37 71 145 149 135 92 223 59 75 158 95 194 227 195 90 22 105 189 124 125 143 67 2 98 215 185 242 160 164 40 112 138 155 187 183 24 123 142 23 180 96 16 32 69 72 10 200 68 8 116 82 109 244 171 251 213 19 36 71 145 187 96 39 160 32 5 145 112 218 187 144 210 237 138 231 203 25 12 14 200 155 198 108 81 65 25 216 213 232 163 247 30 88 105 115 13 121 22 186 100 111 189 0 231 12 117 254 32 151 248 145 103 87 67 247 233 51 17 216 1 214 3 33 173 147 247 177 75 25 230 131 203 218 13 86 58 80 82 201 117 218 194 44 100 175 236 54 130 86 63 71 4 117 214 193 186 214 23 74 86 150 36 157 56 217 248 204 209 130 83 131 63 102 115 12 214 243 128 201 129 130 85 96 227 175 215 139 153 113 96 206 137 173 113 185 92 161 13 114 200 200 209 70 165 101 230 154 60 164 226 223 132 125 78 86 69 209 145 10 96 213 218 28 201 133 77 42 100 149 151 86 98 75 120 195 111 215 76 149 247 9 42 183 238 96 79 219 110 227 238 146 70 243 237 5 40 22 222 102 134 88 189 243 71 20 71 130 237 182 103 63 194 30 33 239 194 55 207 109 71 105 217 8 219 239 87 236 147 140 97 100 236 131 254 60 185 245 13 71 76 237 72 223 244 183 253 198 212 173 43 255 51 193 153 234 51 167 255 55 203 243 95 246 19 19 19 19 19 19 19 19 205 124 1 43 56 168 97";
            String[] inputStirngArray = inputString.split(" ");
            byte[] input = new byte[inputStirngArray.length];
            for (int i = 0; i < inputStirngArray.length; i++) {
                input[i] = (byte) ((int) Integer.valueOf(inputStirngArray[i]));

            }
            Log.d("ddl", "length" + String.valueOf(input.length));
            // Compress the bytes
            //     byte[] output = new byte[100];
            //     Deflater compresser = new Deflater();
            //     compresser.setInput(input);
            //     compresser.finish();
            //     int compressedDataLength = compresser.deflate(output);
            //     compresser.end();

            // Decompress the bytes
            Inflater decompresser = new Inflater();
            decompresser.setInput(input);
            int resultLength = decompresser.inflate(result);
            decompresser.end();

        } catch (java.util.zip.DataFormatException ex) {
            // handle
            Log.d("DDL", "Some problem2");
        }

        Mat res = new Mat(64, 64, CvType.CV_64FC1);
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                res.put(i, j, Float.valueOf((result[i * 64 + j])) / 10.0);
            }
        }
        return res;
    }

    public Mat decodeByteArray(String inputString) {

        byte[] result;
        if (!decodeColor) {
            result = new byte[4096];
        }  else {
            result = new byte[4096*3];
        }

        try {
            // Encode a String into bytes
            //  String inputString = "x\\x9c\\xed\\x93\\xd9N\\xc30\\x10Eg\\xb1\\x93\\xb6,\\x12\\x9b\\xe0K\\xf8\\xff\\xbf\\xe1\\t\\xa9T\\x14R/3\\x8c\\xdbJ-\\xf0P;E\\xe2%G\\x91\\x95\\x87\\\\\\xdf;K\\x9e_\\xc2\\xe3\\xc3Z\\x16i\\xbd|}\\x8fC\\x02b\\xd7\\xb9\\xf2\\xa0\\xa4(p\\x8a\\x9b\\xbb\\xb7\\x18{\\x8e\\x17\\xb4`\\x10 EH\\n\\xc8D\\x08tRm\\xf4\\xab\\xfb\\xd5\\x13$G\\x91\\xbb`'\\xa0 \\x05\\x91p\\xda\\xbb\\x90\\xd2\\xed\\x8a\\xe7\\xcb\\x19\\x0c\\x0e\\xc8\\x9b\\xc6lQA\\x19\\xd8\\xd5\\xe8\\xa3\\xf7\\x1eXis\\ry\\x16\\xbado\\xbd\\x00\\xe7\\x0cu\\xfe \\x97\\xf8\\x91gWC\\xf7\\xe93\\x11\\xd8\\x01\\xd6\\x03!\\xad\\x93\\xf7\\xb1K\\x19\\xe6\\x83\\xcb\\xda\\rV:PR\\xc9u\\xda\\xc2,d\\xaf\\xec6\\x82V?G\\x04u\\xd6\\xc1\\xba\\xd6\\x17JV\\x96$\\x9d8\\xd9\\xf8\\xcc\\xd1\\x82S\\x83?fs\\x0c\\xd6\\xf3\\x80\\xc9\\x81\\x82U`\\xe3\\xaf\\xd7\\x8b\\x99q`\\xce\\x89\\xadq\\xb9\\\\\\xa1\\rr\\xc8\\xc8\\xd1F\\xa5e\\xe6\\x9a<\\xa4\\xe2\\xdf\\x84}NVE\\xd1\\x91\\n`\\xd5\\xda\\x1c\\xc9\\x85M*d\\x95\\x97VbKx\\xc3o\\xd7L\\x95\\xf7\\t*\\xb7\\xee`O\\xdbn\\xe3\\xee\\x92F\\xf3\\xed\\x05(\\x16\\xdef\\x86X\\xbd\\xf3G\\x14G\\x82\\xed\\xb6g?\\xc2\\x1e!\\xef\\xc27\\xcfmGi\\xd9\\x08\\xdb\\xefW\\xec\\x93\\x8cad\\xec\\x83\\xfe<\\xb9\\xf5\\rGL\\xedH\\xdf\\xf4\\xb7\\xfd\\xc6\\xd4\\xad+\\xff3\\xc1\\x99\\xea3\\xa7\\xff7\\xcb\\xf3_\\xf6\\x13\\x13\\x13\\x13\\x13\\x13\\x13\\x13\\xcd|\\x01+8\\xa8a";
            String[] inputStirngArray = inputString.split(" ");
            byte[] input = new byte[inputStirngArray.length];
            for (int i = 0; i < inputStirngArray.length; i++) {
                Log.d("DDL", "curr byte: " + inputStirngArray[i] + " len " + inputStirngArray[i].length());
                input[i] = (byte) ((int) Integer.valueOf(Integer.parseInt(inputStirngArray[i],16)));

            }

            Log.d("ddl", "length" + String.valueOf(input.length));

            // Decompress the bytes
            Inflater decompresser = new Inflater();
            decompresser.setInput(input);
            int resultLength = decompresser.inflate(result);
            decompresser.end();

        } catch (java.util.zip.DataFormatException ex) {
            // handle
            Log.d("DDL", "Some problem2");
        }

        Mat res;
        if (!decodeColor) {
            res = new Mat(64, 64, CvType.CV_64FC1);
            for (int i = 0; i < 64; i++) {
                for (int j = 0; j < 64; j++) {
                    res.put(i, j, Float.valueOf((result[i * 64 + j])) / 10.0);
                }
            }
        }
        else {
            res = new Mat(64, 64, CvType.CV_64FC3);
            for (int i = 0; i < 64; i++) {
                for (int j = 0; j < 64; j++) {
                    res.put(i, j, new double[]{Float.valueOf((result[i * 64 + j])) / 10.0, Float.valueOf((result[4096 + (i * 64 + j)])) / 10.0, Float.valueOf((result[(4096 * 2) + (i * 64 + j)])) / 10.0});
                }
            }
        }
        return res;
    }
}