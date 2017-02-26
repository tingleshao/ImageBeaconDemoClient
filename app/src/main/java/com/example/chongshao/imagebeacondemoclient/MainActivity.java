package com.example.chongshao.imagebeacondemoclient;

import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imageView = (ImageView)this.findViewById(R.id.imageView);

        imageView.setImageResource(R.drawable.images);
        this.readFromCSV("foo");
    }

    public Mat imageFromDCTMat(Mat dctMat) {
        Mat img = new Mat();
        Core.idct(dctMat, img);
        return img;
    }

    public void readFromCSV(String filename) {
        InputStream inputStream = getApplicationContext().getResources().openRawResource(R.raw.android_icon);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = null;
        try {
            line = reader.readLine();
            while (line != null) {
                Log.d("DDL", line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
