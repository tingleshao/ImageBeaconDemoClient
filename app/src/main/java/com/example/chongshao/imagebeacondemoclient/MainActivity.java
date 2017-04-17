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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
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
    private Button scanButtonTri;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private HashMap<Integer, String> inputData;
    private boolean doneGettingData;
    private int maxSize;
    private boolean decodeColor;
    private boolean decodeTri;
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
        decodeTri = false;

        scanButton = (Button)this.findViewById(R.id.scanButton);
        scanButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDL", "scan button clicked");
                decodeColor = false;
                decodeTri = false;
                startScan();
            }
        });

        scanButtonColor = (Button)this.findViewById(R.id.scanButtonColor);
        scanButtonColor.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDL", "color scan button clicked!");
                decodeColor = true;
                decodeTri = false;
                startScan();
            }
        });

        scanButtonTri = (Button)this.findViewById(R.id.scanButtonTri);
        scanButtonTri.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDL", "tri scan button clicked");
                decodeColor = false;
                decodeTri = true;
                doneGettingData = true;
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

                   Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                   if (!decodeTri) {
                       Mat resultDCTMap = decodeByteArray(resultString);
                       Log.d("ddl", "done getting data: " + resultString);
                       Log.d("DDL", "res: " + resultDCTMap.dump());
                       Mat img = imageFromDCTMat(resultDCTMap, 64, 64);
                       Log.d("DDL", "bitmap:" + img.dump());
                       Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC1));
                       Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC3));
                       Utils.matToBitmap(img, bmp);
                   } else {
                       // decode triangle
                       this.decodeByteArrayTri("");
                       Canvas canvas = new Canvas(bmp);
                       Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                       paint.setColor(Color.BLACK);
                       canvas.drawCircle(5, 5, 10, paint);
                   }
                   imageView.setImageBitmap(bmp);
                   stopScan();
                   reset();
                }
            }
        }

        if (doneGettingData) {
            Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);

            if (decodeTri) {
                // decode triangle
                this.decodeByteArrayTri("");
                Canvas canvas = new Canvas(bmp);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        //        paint.setColor(Color.YELLOW);
         //       canvas.drawCircle(5, 5, 10, paint);
                this.drawMesh(canvas, paint);
            }
            imageView.setImageBitmap(bmp);
            stopScan();
            reset();
        }
    }

    private void drawMesh(Canvas canvas, Paint paint) {

        paint.setStrokeWidth(2);
        paint.setColor(android.graphics.Color.RED);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);

        Point point1_draw = new Point(5, 5);
        Point point2_draw = new Point(5, 45);
        Point point3_draw = new Point(40,30);

     //   mapView.getProjection().toPixels(point1, point1_draw);
        //  mapView.getProjection().toPixels(point2, point2_draw);
     //   mapView.getProjection().toPixels(point3, point3_draw);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(point1_draw.x,point1_draw.y);
        path.lineTo(point2_draw.x,point2_draw.y);
        path.lineTo(point3_draw.x,point3_draw.y);
        path.lineTo(point1_draw.x,point1_draw.y);
        path.close();

        canvas.drawPath(path, paint);

//canvas.drawLine(point1_draw.x,point1_draw.y,point2_draw.x,point2_draw.y, paint);
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

    public void decodeByteArrayTri(String inputString) {
        byte[] result;
        if (!decodeColor) {
            if (!decodeTri) {
                result = new byte[4096];
            }
            else {
                result = new byte[20000];
            }
        }  else {
            result = new byte[4096*3];
        }

        try {
            // Encode a String into bytes
            String inputString2 = "120 156 29 143 75 72 35 105 16 199 51 30 212 211 44 116 28 21 100 218 139 7 7 103 80 176 157 72 100 78 163 68 54 72 2 185 121 204 121 12 4 137 105 77 108 230 123 245 51 239 24 77 171 189 39 25 5 131 56 136 179 24 208 13 132 8 171 140 135 192 98 98 64 4 211 62 147 131 12 226 30 183 123 161 248 81 223 191 234 171 127 85 133 47 131 127 208 142 144 5 101 208 158 248 13 188 133 19 153 81 248 217 224 103 232 6 47 218 175 88 123 194 167 133 82 97 213 167 77 100 220 192 167 185 65 40 197 125 243 105 97 245 124 187 194 255 138 157 111 255 138 189 104 133 220 142 80 6 133 92 25 84 248 66 174 194 159 111 203 187 59 66 33 39 239 130 100 22 200 187 89 176 35 8 251 240 187 188 139 242 240 187 176 31 60 244 105 220 55 120 132 242 194 126 163 120 190 253 162 181 22 27 69 147 47 90 123 130 41 181 22 77 182 39 222 66 166 100 238 196 148 38 50 62 141 41 249 180 224 225 214 49 60 18 246 183 142 133 125 121 119 235 88 222 45 228 54 79 224 223 240 200 224 209 214 113 219 105 163 216 90 92 252 25 60 12 253 69 87 219 78 91 139 116 181 181 200 148 186 47 218 78 233 106 253 178 144 59 223 174 95 158 111 55 138 245 203 70 177 237 84 191 174 95 154 108 59 237 190 168 233 245 75 253 186 166 111 158 108 29 215 244 173 227 66 174 166 23 114 245 75 231 13 83 10 30 58 111 130 135 139 63 247 238 55 79 106 186 253 129 174 50 37 251 3 83 114 222 12 60 210 85 251 67 79 83 191 238 190 232 105 118 95 208 213 158 38 93 29 120 220 120 194 77 120 181 241 4 175 54 79 54 158 54 79 246 238 203 207 123 247 53 189 252 92 211 245 235 51 203 198 211 222 253 153 197 84 3 22 231 205 226 191 42 133 155 27 79 42 181 241 116 102 161 172 250 117 79 147 178 158 89 202 207 148 181 252 172 95 83 214 158 230 192 35 233 192 77 149 2 111 224 21 110 130 55 184 73 58 166 59 237 15 206 155 233 78 231 77 192 50 76 219 31 166 59 135 105 202 58 240 56 76 15 60 218 31 198 251 135 233 233 206 192 224 120 191 201 233 78 182 47 48 200 246 133 222 5 6 67 239 194 31 230 134 2 131 225 15 189 12 101 29 166 123 153 97 122 188 159 251 56 55 20 254 112 59 106 110 208 24 187 29 165 172 175 63 53 198 76 82 214 94 198 239 152 27 226 62 250 29 227 253 129 65 191 35 48 56 55 132 39 193 27 210 145 118 226 73 147 164 67 165 108 174 94 102 188 223 230 26 239 247 59 186 220 141 177 215 159 186 220 175 63 245 50 93 238 94 198 230 194 30 60 153 118 30 120 210 78 149 58 240 168 212 153 229 192 115 102 185 29 93 244 248 29 220 199 188 215 172 85 188 121 175 201 3 207 237 104 197 123 59 218 24 91 248 226 119 44 122 192 12 54 254 129 153 180 51 239 5 51 198 245 147 134 50 137 61 45 179 21 111 99 172 101 182 49 214 229 238 11 182 204 154 236 114 219 92 119 108 197 219 50 251 158 237 11 218 92 35 236 123 214 230 154 154 95 248 178 48 51 53 63 242 127 110 115 249 29 83 243 126 199 194 23 75 232 142 109 153 173 135 43 222 59 246 7 7 102 242 222 31 92 222 251 231 226 43 174 30 190 99 95 113 119 172 37 100 231 70 216 169 121 59 247 158 29 97 127 231 236 220 212 188 196 203 34 175 36 37 146 138 242 201 40 175 66 62 25 51 19 35 82 49 126 21 203 89 40 202 130 18 21 149 229 175 130 64 120 89 16 68 34 68 36 222 136 84 156 87 68 162 25 109 17 252 71 82 80 163 120 25 162 12 128 105 197 152 70 36 30 75 60 81 120 44 18 44 243 88 139 225 68 4 175 167 228 213 40 90 75 201 50 33 235 153 200 90 20 175 242 100 45 34 106 9 162 98 188 30 33 24 194 12 132 113 9 165 21 188 26 129 105 9 170 9 222 232 95 77 74 217 184 32 97 20 35 40 139 17 65 32 107 216 9 152 71 48 41 34 25 65 195 122 61 45 173 64 180 18 35 171 73 81 64 144 199 208 168 18 8 150 16 204 96 148 18 193 82 4 39 69 16 33 32 46 162 184 130 227 138 65 180 164 128 101 195 81 49 199 174 68 209 50 129 70 103 92 70 17 104 210 152 28 33 208 224 18 134 9 8 151 191 194 36 129 105 4 5 12 227 4 165 13 5 161 152 132 34 24 169 146 176 130 144 17 10 15 141 43 8 132 50 134 4 65 67 49 220 151 16 74 243 112 9 162 180 177 9 66 34 1 9 0 99 16 138 24 100 16 90 198 216 232 148 8 206 96 108 60 21 130 82 24 73 60 250 15 115 68 120 46 ";
            String[] inputStirngArray = inputString2.split(" ");
            byte[] input = new byte[inputStirngArray.length];
            for (int i = 0; i < inputStirngArray.length; i++) {
         //       Log.d("DDL", "curr byte: " + inputStirngArray[i] + " len " + inputStirngArray[i].length());
                input[i] = (byte) ((int) Integer.valueOf(inputStirngArray[i]));

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
        Log.d("LLD", "decode tri called");
      //  Mat res;

      //  int n = (int)result[0];
        String res = "";
        int n = 210;
        for (int i = 1; i < n*3*2+n*3 + 1 ; i ++) {
         //   if (result[i] < 127) {
                res = res + String.valueOf(result[i]) + " ";
        //    }
     //       else {
      //          res = res + String.valueOf(result[i]+ 256) + " ";
       //     }
        }
        Log.d("LLD", "decode result: " + res);
//        if (!decodeColor) {
//            res = new Mat(64, 64, CvType.CV_64FC1);
//            for (int i = 0; i < 64; i++) {
//                for (int j = 0; j < 64; j++) {
//                    res.put(i, j, Float.valueOf((result[i * 64 + j])) / 10.0);
//                }
//            }
//        }
//        else {
//            res = new Mat(64, 64, CvType.CV_64FC3);
//            for (int i = 0; i < 64; i++) {
//                for (int j = 0; j < 64; j++) {
//                    res.put(i, j, new double[]{Float.valueOf((result[i * 64 + j])) / 10.0, Float.valueOf((result[4096 + (i * 64 + j)])) / 10.0, Float.valueOf((result[(4096 * 2) + (i * 64 + j)])) / 10.0});
//                }
//            }
//        }
//  return res;
    }
}