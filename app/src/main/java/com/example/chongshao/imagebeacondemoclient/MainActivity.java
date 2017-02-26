package com.example.chongshao.imagebeacondemoclient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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


public class MainActivity extends AppCompatActivity {

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imageView = (ImageView)this.findViewById(R.id.imageView);

        imageView.setImageResource(R.drawable.images);
        String csvText = this.readFromCSV("foo");
        Mat m = this.csvTextToMat(csvText, 64, 64);
        Mat img = this.imageFromDCTMat(m, 64, 64);
        Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Log.d("DDL", Boolean.toString(img.type() == CvType.CV_8UC1));
        Utils.matToBitmap(img, bmp);
        imageView.setImageBitmap(bmp);
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
}