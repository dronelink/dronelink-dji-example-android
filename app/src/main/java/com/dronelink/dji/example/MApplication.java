//  MainActivity.java
//  DronelinkDJIExample
//
//  Created by Jim McAndrew on 11/7/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.dji.example;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.multidex.MultiDex;

import com.dronelink.core.Dronelink;
import com.dronelink.core.ui.DronelinkUI;
import com.dronelink.dji.DJIDroneSessionManager;
import com.mapbox.mapboxsdk.Mapbox;

public class MApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
        com.secneo.sdk.Helper.install(this);
        Dronelink.initialize(base);
    }
}