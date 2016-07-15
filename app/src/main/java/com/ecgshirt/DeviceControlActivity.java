package com.ecgshirt;

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import com.androidplot.xy.*;


/**
 *
 * Provides user interface to connect to shirt (RFduino on-board) and then
 * collect, filter and plot the ECG data in real time (200Hz sampling rate)
 * Alerts user if BLE connection is lost, file is *NOT* lost if this occurs
 * File is saved under Android/data/com.ecgshirt as "ecg.txt" and contains
 * timing information as well as the ECG voltage
 *
 * This code is a modified version of a sample Android app which connects
 * and displays a list of a device's available GATT services and characteristics
 * as well as the data which that characteristic holds.
 * The Activity communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 *
 * @author Katie Walker
 */

public class DeviceControlActivity extends Activity {

    CharSequence text = "Wrote to File!";
    int duration = Toast.LENGTH_LONG;

    /* Tag for LogCat purposes */
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    /* BLE device name and address from DeviceScanActivity */
    // public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    // public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";


    /*Information about the BLE connection and device */
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected = false;
    private ExpandableListView mGattServicesList;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    /* Service that manages BLE connection */
    private BluetoothLeService mBluetoothLeService;

    /* List of GATT characteristics and services offered by device */
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private final String LIST_DATA = "DATA";

    /* ArrayLists which hold unfiltered and filtered data, resp. */
    private String[] data_points;
    private List<Float> ecgDataValues;
    private List<String> angelDataValues;
    private List<Double> ecgFilteredDataValues;

    /* Plot and series for ECG data */
    private XYPlot mySimpleXYPlot;
    SimpleXYSeries series1;

    /* Number of points to display on plot during one screen refresh */
    private static final int HISTORY_SIZE = 200;

    /* Keeps track of how many points have been placed into the unfiltered data buffer */
    public int samples_collected;

    /* Intent for Bluetooth GATT service */
    public Intent gattServiceIntent;

    /* WakeLock variables */
    public PowerManager pm;
    public PowerManager.WakeLock wl;
    public Activity mDeviceControlActivity;

    /* Thread for plotting and saving data */
    public Thread mThread;

    /* Current time stamp (for file saving purposes) */
    private long currentTime;

    /* Context of DeviceControlActivity */
    public Context mContext;

    /* Flag to determine if the BLE connection was lost or terminated by the user */
    private boolean disconnectButtonHasBeenPressed;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;

        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

                //grab current time stamp for data logging in ecg file
                currentTime = System.currentTimeMillis();

                //if the thread for data plotting and saving has not yet started
                //or if it has been interrupted by something like a lost BLE connection
                //restart the thread

                //               if(mThread == null) {
                //mThread = new Thread(new DataPlotAndSave());
                //                   mThread.start();
                //               } else if(!mThread.isAlive() || mThread.isInterrupted()){
                //                   mThread.interrupt();
                //                   mThread = null;
                // mThread = new Thread(new DataPlotAndSave());
//                    mThread.start();
//                }
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();

                //if the BLE connection has been lost and the user did not
                //choose to disconnect, alert the user
                if(!disconnectButtonHasBeenPressed) {
                    disconnectButtonHasBeenPressed = false;

                    // Vibrate for 100ms = 1 second
                    Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(1000);

                    //ALERTDIALOG WINDOW TO ALERT USER THAT BLE DISCONNECTED
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
                    // set title
                    alertDialogBuilder.setTitle("BLE connection lost!");
                    // set dialog message
                    alertDialogBuilder.setMessage("Connection with the shirt has been lost!\nPlease try connecting again.");
                    alertDialogBuilder.setCancelable(true);
                    alertDialogBuilder.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            // if this button is clicked, go back to list of devices
                            //in order to reconnect
                            onBackPressed();
                        }
                    });
                    // create alert dialog
                    AlertDialog alertDialog = alertDialogBuilder.create();
                    // show it
                    alertDialog.show();
                }
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));

            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };


    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        //create Android/data/com.ecgshirt folder, if it doesn't already exist
        File myDir = this.getExternalFilesDir(null);

        mContext = this;

        //ArrayList for data values straight from RFduino
        ecgDataValues = new ArrayList<Float>();
        angelDataValues = new ArrayList<String>();
        //ArrayList for data values after digital filtering on app
        ecgFilteredDataValues = new ArrayList<Double>();

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra("EXTRAS_DEVICE_NAME");
        mDeviceAddress = intent.getStringExtra("EXTRAS_DEVICE_ADDRESS");


        // Sets up UI references
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        mConnectionState = (TextView) findViewById(R.id.connection_state);
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        gattServiceIntent = new Intent(this, BluetoothLeService.class);
        //connect to BLE device
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        //grab the instance of this class
        mDeviceControlActivity = this;

        //Acquire partial wake lock so that app will continue to run
        //and collect data even when screen is turned off
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE, "DeviceControlActivity Tag");
        wl.acquire();

        //keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection); //disconnect from BLE device when we leave this activity
        mBluetoothLeService = null;
        if(wl != null) wl.release(); //release wake lock
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            menu.findItem(R.id.menu_home).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mBluetoothLeService != null) {
            switch (item.getItemId()) {
                case R.id.menu_connect:
                    mBluetoothLeService.connect(mDeviceAddress);
                    return true;
                case R.id.menu_disconnect:
                    disconnectButtonHasBeenPressed = true;
                    mBluetoothLeService.disconnect();
                    return true;
                case android.R.id.home:
                    onBackPressed();
                    return true;
                case R.id.menu_home:
                    //go back to home screen
                    mBluetoothLeService.disconnect();
                    Intent intent = new Intent(this, MainScreen.class);
                    startActivity(intent);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    /** Demonstrates how to iterate through the supported GATT Services/Characteristics.
     * In this sample, we populate the data structure that is bound to the ExpandableListView
     * on the UI.
     * @param gattServices list of GATT services that the device offers
     */
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            //each currentServiceData is a service UUID and service value (name)
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    /**
     * Thread to plot and save filtered ECG data in real time
     *
     */
    /*
    public class DataPlotAndSave implements Runnable {

        @Override
        public void run() {

            BluetoothGattCharacteristic characteristic;
            int charaProp;
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            sdfDate.setTimeZone(TimeZone.getDefault());

            samples_collected = 0; //initialize data buffer counter to 0

            //wait for list of device's GATT characteristics to be built
            while(!mConnected || mGattCharacteristics == null || mGattCharacteristics.size() != 3){;}

            //grab RFduino's 3rd service and 1st characteristic (BLE READ/NOTIFY)
            if(mConnected && mGattCharacteristics != null && mGattCharacteristics.size() == 3){
                characteristic = mGattCharacteristics.get(2).get(0);

                //if notifications have not yet been enabled, turn them on
                charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mBluetoothLeService.setCharacteristicNotification(characteristic,true);
                    Log.v(TAG, "Thread has enabled notifications");
                }
            }

            //as long as the device is connected,
            //filter, plot and save the values
            while(mConnected) {

                //once buffer is full, start filtering
                //after filtering, plot and save
                //then clear buffer
                //if(angelDataValues.size() > 0){

                /***********************************************************************
                 *
                 * Filter using ecgVoltageDataValues ArrayList
                 * Return ecgFilteredDataValues with filtered data
                 *
                 **********************************************************************/
//                	 filter();

    /***********************************************************************
     *
     * Write filtered values and a time stamp to the file and then plot
     *
     **********************************************************************/
    /*
                try{
                    //put ecg file in Android/data/com.ecgshirt
                    File traceFile = new File(mContext.getExternalFilesDir(null), "ecg2.txt");
                    //if the file does not already exist, create it
                    if (!traceFile.exists()){
                        traceFile.createNewFile();
                    }

                    Date now;
                    String strDate;
                    BufferedWriter ecgVoltageFileWriter = new BufferedWriter(new FileWriter(traceFile, true /*append*///));

    //plot and save the filtered data
    //for(int i = 0; i < HISTORY_SIZE; i++){

    //for (int i = 0; i < angelDataValues.size(); i++){
    //String f = angelDataValues.get(i);
//

    //time stamp has format: "yyyy-MM-dd HH:mm:ss.SSS"
 /*                   now = new Date(currentTime);
                    strDate = sdfDate.format(now);
                    if (!angelDataValues.equals(null)) {
                        String f = angelDataValues.get(0);
                        //write time stamp and filtered value to file
                        ecgVoltageFileWriter.write(strDate + ", " + "f" + "\n");
                    }


                    currentTime += 1000; //increment time stamp by 1000 ms
                    //}


                    ecgVoltageFileWriter.close(); // close file writer

                } catch (Exception e){
                    Log.e(TAG, "ERROR with file manipulation or plotting!\n" + e.getMessage());
                }

                //clear ArrayLists
                //note that we don't actually lose any data!
//				    ecgFilteredDataValues.clear();
                ecgDataValues.clear();
                //mySimpleXYPlot.redraw(); //plot the new values
                samples_collected = 0; //reset buffer counter

                // }else if (angelDataValues.size() == 0){

                //}
            }

        }
    } */

}

