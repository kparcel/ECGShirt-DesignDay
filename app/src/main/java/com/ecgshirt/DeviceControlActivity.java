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
import android.view.WindowManager;
import android.widget.TextView;

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
	
	/* Tag for LogCat purposes */
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    /* BLE device name and address from DeviceScanActivity */
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    /*Information about the BLE connection and device */
    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected = false;
    
    /* Service that manages BLE connection */
    private BluetoothLeService mBluetoothLeService;
    
    /* List of GATT characteristics and services offered by device */
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    /* ArrayLists which hold unfiltered and filtered data, resp. */
    private List<Float> ecgDataValues;
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
                if(mThread == null) {
                    mThread = new Thread(new DataPlotAndSave());
                    mThread.start();
                } else if(!mThread.isAlive() || mThread.isInterrupted()){
                	mThread.interrupt();
                	mThread = null;
                    mThread = new Thread(new DataPlotAndSave());
                    mThread.start();
                }
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                
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
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);

                samples_collected+=20; //20 data points per packet
                String[] data_points = data.split(",");

                float ecg_voltage;
                for(int i = 0; i < 20; i++){
                	//convert from "byte/char" to integer
                	//multiply by conversion factor: [-1V,+1V] with 8-bits
                	//conversion factor = (+1 - (-1))/(2^8) = 0.0078125
                	//so int = (V_sample - (-1)) / 0.0078125
                	//get V_sample = int*0.0078125 - 1 
                	// old conversion factors for 0-5V: 0.01953125 with 8-bit ADC, 0.0049 with 10-bit ADC
	            	ecg_voltage = Integer.parseInt(data_points[i])*((float)0.0078125)-1;
	            	ecgDataValues.add(ecg_voltage); //add data value to array list
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
        
        //create Android/data/com.ecgshirt folder, if it doesn't already exist
        File myDir = this.getExternalFilesDir(null);
        
        mContext = this;

        //ArrayList for data values straight from RFduino
        ecgDataValues = new ArrayList<Float>();
        //ArrayList for data values after digital filtering on app
    	ecgFilteredDataValues = new ArrayList<Double>();
    	
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        
        // Sets up UI references
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        gattServiceIntent = new Intent(this, BluetoothLeService.class);
        //connect to BLE device
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        /* BUILD PLOT */
        mySimpleXYPlot = (XYPlot) findViewById(R.id.plotarea);
        series1 = new SimpleXYSeries("ECG data");
        series1.useImplicitXVals(); // disable if using a time stamp as x value

        mySimpleXYPlot.setRangeBoundaries(0, 5, BoundaryMode.AUTO); //dynamically change range based on the y values being plotted
        mySimpleXYPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED); // disable if using a time stamp as x value
        //line and points are red
        LineAndPointFormatter lineAndPoint = new LineAndPointFormatter(Color.RED, Color.RED, null, null); //line format for graph
        lineAndPoint.getLinePaint().setStrokeWidth(5); // increase line thickness
        mySimpleXYPlot.addSeries(series1, lineAndPoint);
        
        // 5 divisions for domain and range (these are the gridlines)
        mySimpleXYPlot.setDomainStepValue(5);
        mySimpleXYPlot.setRangeStepValue(5);
        mySimpleXYPlot.setTicksPerRangeLabel(1);
        mySimpleXYPlot.setDomainLabel("Time");
        mySimpleXYPlot.getDomainLabelWidget().pack();
        mySimpleXYPlot.setRangeLabel("ECG voltage");
        mySimpleXYPlot.getRangeLabelWidget().pack();

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
                if(samples_collected >= HISTORY_SIZE){
                	
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
                	try{
                		//put ecg file in Android/data/com.ecgshirt
                		File traceFile = new File(mContext.getExternalFilesDir(null), "ecg.txt");
                		//if the file does not already exist, create it
            		    if (!traceFile.exists()){
        			       traceFile.createNewFile();
            		    }
            		    
	                    Date now;
	                    String strDate;
                		BufferedWriter ecgVoltageFileWriter = new BufferedWriter(new FileWriter(traceFile, true /*append*/));

						//plot and save the filtered data
						for(int i = 0; i < HISTORY_SIZE; i++){
//							if(ecgFilteredDataValues.size() > i){
							if(ecgDataValues.size() > i){
						        if (series1.size() > HISTORY_SIZE) {
						            series1.removeFirst();
						        }
						        
						        float f = ecgDataValues.get(i);
//						        double f = ecgFilteredDataValues.get(i);
						        
				            	//time stamp has format: "yyyy-MM-dd HH:mm:ss.SSS"
				            	now = new Date(currentTime);
				            	strDate = sdfDate.format(now);
				            	
				            	//add data point to series for plotting
						        series1.addLast(null, f);
						        
						        //write time stamp and filtered value to file
						        ecgVoltageFileWriter.write(strDate + ", " + f + "\n");
						        
						        currentTime += 5; //increment time stamp by 5 ms (200 Hz)
							}
						}
						
						ecgVoltageFileWriter.close(); // close file writer
						
				    } catch (Exception e){
				    	Log.e(TAG, "ERROR with file manipulation or plotting!\n" + e.getMessage());
				    }
                	
                	//clear ArrayLists
                	//note that we don't actually lose any data!
//				    ecgFilteredDataValues.clear();
				    ecgDataValues.clear();
				    mySimpleXYPlot.redraw(); //plot the new values
				    samples_collected = 0; //reset buffer counter
				    
                }
            }

        }

    
        /**
         * Filtering function
         * Expect ecgDataValues to contain unfiltered data
         * Then return ecgFilteredDataValues with the filtered data points for plotting and saving
         */
	    public void filter(){
	    	
	    	int size = ecgDataValues.size();
	    	
	    	// LP filter variables
	    	double[] voltageLP = new double[size + 6];
	    	double y1 = 0.0;
	    	double y2 = 0.0;
	    	double[] x = new double[26];
	    	int n = 12;
	    	double y0;
	    	
	    	// High Pass Filter Variables
	    	double[] voltageHP = new double[size + 22];
	    	double hpy1 = 0.0;
	    	double hpy0 = 0.0;
	    	double[] hpx = new double[66];
	    	int m = 32;
	    	
	    	double data;
	    	try{
	    		
	    		// Low pass filter
		    	for(int i = 0; i < size; i++){
		    		data = ecgDataValues.get(i);
		    		x[n+13] = data;
		    		x[n] = data;
		    		y0 = (y1*2)-y2+x[n]-(x[n+6]*2)+x[n+12];
		    		y2 = y1;
		    		y1=y0;
		    		y0=y0/32;
		    		if(--n<0) n = 12;
		    		voltageLP[i+6] = y0;
		    		
		    	}
		    
		    	
		    	for (int j=6; j<size+6; j++) {
		    		hpx[m]    = voltageLP[j];
		    		hpx[m+33] = voltageLP[j];
		    		
		    		hpy0 = hpy1 + hpx[m] - hpx[m+32];
		    		
		    		//shift registers
		    		hpy1 = hpy0;
		    		
		    		if(--m < 0) m = 32;
		    		voltageHP[j+16] = hpx[m+16] - (hpy0/32);
		    	
		    	}
		    	
	    	} catch(ArrayIndexOutOfBoundsException e){
	    		e.getStackTrace();
	    	}
	    	
	    	//add filtered data values to ecgFilteredDataValues
	    	for(int i = 0; i < voltageHP.length; i++){
	    		ecgFilteredDataValues.add(voltageHP[i]);
	    	}
	    	
	    }
    }
    
}