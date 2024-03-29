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
import android.hardware.usb.UsbManager;
import android.os.Build;
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
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.core.CameraFocusCalibration;
import com.dronelink.core.kernel.core.Descriptors;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.ui.DronelinkUI;
import com.dronelink.dji.DJIDroneSessionManager;
import com.dronelink.dji.ui.DJIDashboardActivity;
import com.mapbox.mapboxsdk.Mapbox;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, FuncExecutor.Listener, ModeExecutor.Listener {
    private static final String TAG = MainActivity.class.getCanonicalName();

    private static List<String> getRequiredPermissionList() {
        final List<String> requiredPermissionList = new ArrayList<>();
        requiredPermissionList.add(Manifest.permission.BLUETOOTH);
        requiredPermissionList.add(Manifest.permission.BLUETOOTH_ADMIN);
        requiredPermissionList.add(Manifest.permission.INTERNET);
        requiredPermissionList.add(Manifest.permission.ACCESS_WIFI_STATE);
        requiredPermissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        requiredPermissionList.add(Manifest.permission.ACCESS_NETWORK_STATE);
        requiredPermissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredPermissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        requiredPermissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        requiredPermissionList.add(Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissionList.add(Manifest.permission.BLUETOOTH_SCAN);
        }

        return requiredPermissionList;
    }

    private static class RequestCodes {
        private static final int REQUEST_PERMISSION = 1;
        private static final int DASHBOARD = 2;
    }

    private static final String MAP_CREDENTIALS_KEY = "INSERT YOUR CREDENTIALS KEY HERE";

    private final List<String> missingPermission = new ArrayList<>();

    private AssetManifest assetManifest;
    private int assetIndex;

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        //doesn't seem to work if the dashboard is open though :(
        //https://github.com/dji-sdk/Mobile-SDK-Android/issues/479
        final String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            final Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DronelinkUI.initialize(this);
        Dronelink.getInstance().addListener(this);
        Dronelink.getInstance().addDroneSessionManager(new DJIDroneSessionManager(getBaseContext()));

        checkAndRequestPermissions();
        setContentView(R.layout.activity_main);

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));

        //Dronelink.getInstance().identifyUser(new User("1234"));
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
        for (String eachPermission : getRequiredPermissionList()) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }

        if (missingPermission.isEmpty()) {
            ((DJIDroneSessionManager)Dronelink.getInstance().getTargetDroneSessionManager()).register(getApplicationContext());
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
            ((DJIDroneSessionManager)Dronelink.getInstance().getTargetDroneSessionManager()).register(getApplicationContext());
        }
        else {
            showToast("Please check if the permission is granted: " + missingPermission.get(0));
        }
    }

    public void onDashboard(View v) {
        final Intent intent = new Intent(getBaseContext(), DJIDashboardActivity.class);
        intent.putExtra("mapCredentialsKey", MAP_CREDENTIALS_KEY);
        startActivityIfNeeded(intent, RequestCodes.DASHBOARD);
        loadPlan();
        //loadFunc();
        //loadMode();
    }

    private void loadPlan() {
        try {
            Dronelink.getInstance().loadPlan(loadAssetTextAsString("plan.dronelink"), false, this, (final String error) -> { Log.e(TAG, "Unable to read mission plan: " + error); });
        } catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        } catch (final Dronelink.UnregisteredException e) {
            Log.e(TAG, "Dronelink SDK Unregistered");
        }
    }

    private void loadFunc() {
        try {
            Dronelink.getInstance().loadFunc(loadAssetTextAsString("func.dronelink"), this, (final String error) -> { Log.e(TAG, "Unable to read function: " + error); });
        } catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        } catch (final Dronelink.UnregisteredException e) {
            Log.e(TAG, "Dronelink SDK Unregistered");
        }
    }

    private void loadMode() {
        try {
            Dronelink.getInstance().loadMode(loadAssetTextAsString("mode.dronelink"), this, (final String error) -> { Log.e(TAG, "Unable to read mode: " + error); });
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
    public void onRegistered(final String error) {}

    @Override
    public void onDroneSessionManagerAdded(final DroneSessionManager droneSessionManager) {
        droneSessionManager.addListener(this);
    }

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {}

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {}

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {}

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {}

    @Override
    public void onModeLoaded(final ModeExecutor executor) {}

    @Override
    public void onModeUnloaded(final ModeExecutor executor) {}

    @Override
    public void onCameraFocusCalibrationRequested(final CameraFocusCalibration value) {}

    @Override
    public void onCameraFocusCalibrationUpdated(final CameraFocusCalibration value) {}

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
    public void onVideoFeedSourceUpdated(final DroneSession session, final Integer channel) {}

    @Override
    public void onMissionEstimating(final MissionExecutor executor) {}

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {}

    @Override
    public Message[] missionEngageDisallowedReasons(final MissionExecutor executor) { return null; }

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
    public void onMissionUpdatedDisconnected(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onFuncInputsChanged(final FuncExecutor executor) {}

    @Override
    public void onFuncExecuted(final FuncExecutor executor) {
        final String type = executor.getExecutableType();
        if (type == null) {
            return;
        }

        try {
            if ("Mission".equals(type)) {
                Dronelink.getInstance().loadMission(executor.getExecutableSerialized(),this, (final String error) -> {
                    Log.e(TAG, "Unable to read mission: " + error);
                });
            }
            else if ("Mode".equals(type)) {
                Dronelink.getInstance().loadMode(executor.getExecutableSerialized(), this, (final String error) -> {
                    Log.e(TAG, "Unable to read mode: " + error);
                });
            }
        } catch (final Dronelink.KernelUnavailableException e) {
            Log.e(TAG, "Dronelink Kernel Unavailable");
        } catch (final Dronelink.UnregisteredException e) {
            Log.e(TAG, "Dronelink SDK Unregistered");
        }
    }

    @Override
    public Message[] modeEngageDisallowedReasons(final ModeExecutor executor) { return null; }

    @Override
    public void onModeEngaging(final ModeExecutor executor) {}

    @Override
    public void onModeEngaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {}

    @Override
    public void onModeExecuted(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {}

    @Override
    public void onModeDisengaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement, final Message reason) {}
}
