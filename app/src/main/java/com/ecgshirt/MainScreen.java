package com.ecgshirt;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * Welcome screen for ECG shirt app
 * 
 * @author Katie Walker
 *
 */
public class MainScreen extends Activity {

    private Button connectToBleButton;


    @Override
    public void onBackPressed() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main_screen);
        addListenerOnButton();



    }

    /**
     * When user presses "Connect to shirt" button, go to DeviceScanActivity
     * which lists advertising BLE devices
     */
    public void addListenerOnButton() {

        final Context context = this;

        connectToBleButton = (Button) findViewById(R.id.connectButton);

        connectToBleButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(context, DeviceScanActivity.class);
                startActivity(intent);

            }

        });

    }

}
