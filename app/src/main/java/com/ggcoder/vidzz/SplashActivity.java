package com.ggcoder.vidzz;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;

public class SplashActivity extends AppCompatActivity {

    private boolean havePermissions = false, isRecording = false;
    private String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
    };

    //Splash Screen Activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        //Check if the device supports cameras and exit if no camera found
        if (!checkCameraHardware(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Fatal Error")
                    .setMessage("Your device doesn't have a camera. The application will exit now.")
                    .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        }
        else {
            if (hasPermissions(this, PERMISSIONS)) {
                animate();
            }
            else {
                requestPermissions();
            }
        }
    }

    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    //Animate App Logo with GrowIn animation
    private void animate() {
        // create logs on device
        File logFolder = new File(Environment.getExternalStorageDirectory() + "/Vidzz/Logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs()
            ;        }
        File logFile = new File( logFolder, System.currentTimeMillis() + ".txt");
        try {
            Process process = Runtime.getRuntime().exec("logcat -c");
            process = Runtime.getRuntime().exec("logcat -f " + logFile);
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        ImageView mLogoView = findViewById(R.id.imageView);
        Animation GrowIn = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.grow_in);

        GrowIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                Handler handler = new Handler();    //Proceed to LandingActivity after Animation
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent MainIntent = new Intent(getApplicationContext(), LandingActivity.class);
                        MainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(MainIntent);
                    }
                }, 500);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        mLogoView.startAnimation(GrowIn);
    }

    // Check for Required Permissions
    private static boolean hasPermissions(Context context, String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;    // Return Tru only if all permissions granted
    }

    // Request for Required Permissions
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101) {   // Check if permissions Granted or not
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                    animate();   // Start Multimedia operations if all permissions granted
                } else {
                    new AlertDialog.Builder(this)   // Show a message to user why permissions are required
                            .setTitle("Alert!")
                            .setMessage("This application requires CAMERA and STORAGE permissions to record video. Please grant the permissions or the application will exit.\n\nNote - If you have previously clicked Don't ask again, you will have to grant the permissions from settings.")
                            .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    requestPermissions();   // Request for permissions if denied again
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            }
        }
    }
}

