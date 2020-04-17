//  MainActivity.java
//  DronelinkDJIExample
//
//  Created by Jim McAndrew on 11/7/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.dji.example;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.dronelink.core.AssetManifest;
import com.dronelink.core.CameraFile;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.mission.command.Command;
import com.dronelink.core.mission.core.Descriptors;
import com.dronelink.core.mission.core.Message;
import com.dronelink.dji.DJIDroneSessionManager;
import com.dronelink.dji.ui.DJIDashboardActivity;
import com.mapbox.mapboxsdk.Mapbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, FuncExecutor.Listener {
    private static final String TAG = MainActivity.class.getCanonicalName();

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
        Manifest.permission.VIBRATE, // Gimbal rotation
        Manifest.permission.INTERNET, // API requests
        Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
        Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
        Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
        Manifest.permission.ACCESS_FINE_LOCATION, // Maps
        Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
        Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
        Manifest.permission.BLUETOOTH, // Bluetooth connected products
        Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
        Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
        Manifest.permission.READ_PHONE_STATE, // Device UUID accessed upon registration
        Manifest.permission.RECORD_AUDIO // Speaker accessory
    };

    private class RequestCodes {
        private static final int REQUEST_PERMISSION = 1;
        private static final int DASHBOARD = 2;
    }

    private List<String> missingPermission = new ArrayList<>();

    private AssetManifest assetManifest;
    private int assetIndex;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_main);

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        Dronelink.getInstance().getSessionManager().addListener(this);
        Dronelink.getInstance().register("INSERT YOUR ENVIRONMENT KEY HERE", null);
        try {
            //use Dronelink.KernelVersionTarget to see the minimum compatible kernel version that the current core supports
            Dronelink.getInstance().installKernel(loadAssetTextAsString("dronelink-kernel.js"));
            assetManifest = Dronelink.getInstance().createAssetManifest("example", new String[]{"tag1", "tag2"});
            final Descriptors descriptors = new Descriptors();
            descriptors.name = "name";
            descriptors.description = "description";
            descriptors.tags = new String[]{"tag1", "tag2"};
            assetIndex = assetManifest.addAsset("key", descriptors);
        }
        catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        }
        catch (final Dronelink.KernelInvalidException e) {
            Log.e(TAG, "Dronelink Kernel Invalid");
        }
        catch (final Dronelink.KernelIncompatibleException e) {
            Log.e(TAG, "Dronelink Kernel Incompatible");
        }
    }

    private void checkAndRequestPermissions() {
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }

        if (missingPermission.isEmpty()) {
            ((DJIDroneSessionManager)Dronelink.getInstance().getSessionManager()).register(getApplicationContext());
        }
        else {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    RequestCodes.REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RequestCodes.REQUEST_PERMISSION) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }

        if (missingPermission.isEmpty()) {
            ((DJIDroneSessionManager)Dronelink.getInstance().getSessionManager()).register(getApplicationContext());
        }
        else {
            showToast("Please check if the permission is granted.");
        }
    }

    public void onDashboard(View v) {
        startActivityIfNeeded(new Intent(getBaseContext(), DJIDashboardActivity.class), RequestCodes.DASHBOARD);
        loadPlan();
        //loadFunc();
    }

    private void loadPlan() {
        try {
            Dronelink.getInstance().loadPlan(loadAssetTextAsString("plan.lz"), false, Dronelink.getInstance().getSessionManager().getSession(), this, (final String error) -> { Log.e(TAG, "Unable to read mission plan: " + error); });
        } catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        } catch (final Dronelink.UnregisteredException e) {
            Log.e(TAG, "Dronelink SDK Unregistered");
        }
    }

    private void loadFunc() {
        try {
            Dronelink.getInstance().loadFunc(loadAssetTextAsString("func.lz"), this);
        } catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        } catch (final Dronelink.UnregisteredException e) {
            Log.e(TAG, "Dronelink SDK Unregistered");
        }
    }

    private void showToast(final String toastMsg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show());
    }

    private String loadAssetTextAsString(final String name) {
        BufferedReader in = null;
        try {
            final StringBuilder buf = new StringBuilder();
            final InputStream is = getApplicationContext().getAssets().open(name);
            in = new BufferedReader(new InputStreamReader(is));

            String str;
            boolean isFirst = true;
            while ( (str = in.readLine()) != null ) {
                if (isFirst)
                    isFirst = false;
                else
                    buf.append('\n');
                buf.append(str);
            }
            return buf.toString();
        } catch (final IOException e) {
            Log.e(TAG, "Error opening asset " + name);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException e) {
                    Log.e(TAG, "Error closing asset " + name);
                }
            }
        }

        return null;
    }

    @Override
    public void onOpened(final DroneSession session) {
        session.addListener(this);
    }

    @Override
    public void onClosed(final DroneSession session) {
        Dronelink.getInstance().announce((session.getName() == null ? "drone" : session.getName()) + " disconnected");
    }

    @Override
    public void onInitialized(final DroneSession session) {
        Dronelink.getInstance().announce((session.getName() == null ? "drone" : session.getName()) + " connected");
    }

    @Override
    public void onLocated(final DroneSession session) {}

    @Override
    public void onMotorsChanged(final DroneSession session, final boolean value) {}

    @Override
    public void onCommandExecuted(final DroneSession session, final Command command) {}

    @Override
    public void onCommandFinished(final DroneSession session, final Command command, final CommandError error) {}

    @Override
    public void onCameraFileGenerated(final DroneSession session, final CameraFile file) {
        assetManifest.addCameraFile(assetIndex, file);
        //assetManifest.getSerialized() to get the manually tracked asset manifest json
    }

    @Override
    public void onMissionEstimating(final MissionExecutor executor) {}

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {}

    @Override
    public void onMissionEngaging(final MissionExecutor executor) {}

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {
        //save mission to back-end using: executor.getMissionSerializedAsync(...
        //get asset manifest using: executor.getAssetManifestSerialized()
        //load mission later using Dronelink.getInstance().loadMission(...
    }

    @Override
    public void onFuncExecuted(final FuncExecutor executor) {
        try {
            Dronelink.getInstance().loadMission(executor.getMissionSerialized(), Dronelink.getInstance().getSessionManager().getSession(), this, (final String error) -> { Log.e(TAG, "Unable to read mission: " + error); });
        } catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        } catch (final Dronelink.UnregisteredException e) {
            Log.e(TAG, "Dronelink SDK Unregistered");
        }
    }
}
