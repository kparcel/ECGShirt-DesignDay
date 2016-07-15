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

import java.util.HashMap;
/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    
    /* Information for the ECG t-shirt */
    public static String ECG_INFO_VOLTAGE_CHARACTERISTIC = "00002221-0000-1000-8000-00805f9b34fb";
    public static String ECG_INFO_VOLTAGE_SERVICE = "00002220-0000-1000-8000-00805f9b34fb";
    public static String ECG_CHARACTERTISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    //Information for Activity Monitoring Service: Angel Sensor Steps & Acceleration Energy
    public static String ANGEL_ACTIVITY_MONITORING_SERVICE = "68b52738-4a04-40e1-8f83-337a29c3284d";
    public static String ANGEL_STEP_COUNT_MEASUREMENT = "7a543305-6b9e-4878-ad67-29c5a9d99736";
    public static String ANGEL_STEP_COUNT_CONFIG = "7a542902-6b9e-4878-ad67-29c5a9d99736"; //NOT SURE IF THIS IS RIGHT
    public static String ANGEL_ACCEL_MAG_MEASUREMENT = "9e3bd0d7-bdd8-41fd-af1f-5e99679183ff";

    //Information for Angel Sensor Temperature
    public static String HEALTH_THERMOMETER_SERVICE = "00001809-0000-1000-8000-00805f9b34fb";
    public static String TEMPERATURE_MEASUREMENT = "00002A1C-0000-1000-8000-00805f9b34fb";
    public static String TEMPERATURE_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        attributes.put(ECG_INFO_VOLTAGE_SERVICE, "ECG Signal");
        //ECG BLE read/notify characteristic
        attributes.put(ECG_INFO_VOLTAGE_CHARACTERISTIC, "ECG Info (Voltage)");
        //ECG BLE read/notify characteristic
        attributes.put(ECG_INFO_VOLTAGE_CHARACTERISTIC, "ECG Info (Voltage)");

        // Angel Services
        attributes.put(HEART_RATE_SERVICE, "Heart Rate Service");
        attributes.put(HEALTH_THERMOMETER_SERVICE, "Health Thermometer Service");
        attributes.put(ANGEL_ACTIVITY_MONITORING_SERVICE, "Activity Monitoring Service");
        
        // Standard/sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");


        attributes.put(TEMPERATURE_MEASUREMENT, "Skin Temperature Measurement");


        //Angel Characteristics
        attributes.put(ANGEL_STEP_COUNT_MEASUREMENT, "Angel Step Count");
        attributes.put(ANGEL_ACCEL_MAG_MEASUREMENT, "Angel Accel. Energy Mag.");

        //Configurations?
        attributes.put(ANGEL_STEP_COUNT_CONFIG, "Step Count Configuration");
        attributes.put(CLIENT_CHARACTERISTIC_CONFIG, "Configuration for Standard BT char");


        //

    }


    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}