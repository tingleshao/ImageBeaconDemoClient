package com.example.chongshao.imagebeacondemoclient;

import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;


// TODO: decode a sample JPEG image
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imageView = (ImageView)this.findViewById(R.id.imageView);

        imageView.setImageResource(R.drawable.images);
    }
}
