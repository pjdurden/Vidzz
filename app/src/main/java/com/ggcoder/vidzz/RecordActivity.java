package com.ggcoder.vidzz;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordActivity extends AppCompatActivity {

    private boolean havePermissions = false, isRecording = false;
    private String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
    };

    private FrameLayout preview;

    private Camera mCamera;
    private Camera.Parameters params;
    private CameraPreview mCameraPreview;
    private MediaRecorder mMediaRecorder;
    private static int camID = 0;
    private int seconds = 0;

    private Uri mUri;
    private static String mFileName;
    private View recordButton;

    private Handler timerHandler;
    private Runnable timerTask;
    private TextView timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        timerHandler = new Handler();   // Creating thread to run recording timer

        timerTask = new Runnable() {
            @Override
            public void run() { // Creating Task to be performed by the Timer Thread
                if (!recordButton.isEnabled()) {
                    recordButton.setEnabled(true);
                }
                String ss, mm;
                if (seconds > 9) {
                    ss = Integer.toString((seconds % 60));
                } else {
                    ss = "0" + Integer.toString((seconds % 60));
                }
                if (seconds / 60 > 9) {
                    mm = Integer.toString((seconds % 3600) / 60);
                } else {
                    mm = "0" + Integer.toString((seconds % 3600) / 60);
                }
                timer.setText(mm + ":" + ss);
                seconds++;
                timerHandler.postDelayed(this, 1000);
            }
        };

        //Initialising Views
        timer = findViewById(R.id.tv_timer);
        timer.setVisibility(View.GONE);

        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {   // Setting Touch to Focus Feature
                if (mCamera != null) {
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {

                        }
                    });
                }
            }
        });

        final ImageView recordButtonIcon = findViewById(R.id.iv_record_btn);
        final ImageView toggleCamera = findViewById(R.id.btn_toggle);
        toggleCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {   // Toggle cameras by initialising camera again by different CAMERA_ID
                if (camID == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    camID = Camera.CameraInfo.CAMERA_FACING_FRONT;
                } else {
                    camID = Camera.CameraInfo.CAMERA_FACING_BACK;
                }
              //   Toast.makeText(RecordActivity.this, "Changed Cam Id", Toast.LENGTH_SHORT).show();
                preparePreview();   //Start preview after Switching
            }
        });

        final Context mContext = this;
        recordButton = findViewById(R.id.btn_record);   // Start Video recording after clicking Record Button
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {  // Check if aldready recording
                    mMediaRecorder.stop();  // stop the recording
                    releaseMediaRecorder(); // release the MediaRecorder object
                    // recordButtonText.setText("Record");
                    releaseCamera();
                    recordButtonIcon.setImageDrawable(getDrawable(R.drawable.baseline_play_arrow_black_36));    // Toggle Button Image
                    toggleCamera.setEnabled(true);  // Restore camera toggle capability
                    toggleCamera.setVisibility(View.VISIBLE);
                    isRecording = false;
                    timer.setVisibility(View.GONE); // Disable Timer
                    timerHandler.removeCallbacks(timerTask);
                    new UploadDialog(mContext, mUri, mFileName, new UploadDialog.onResultListener() {
                        @Override
                        public void onResult(boolean ifDestroy) {
                            if (!ifDestroy) {
                                preparePreview();   // reinitialise preview if activity not destroyed
                            }
                        }
                    }).show();    //Show preview of Video before uploading
                } else {
                    recordButton.setEnabled(false);
                    if (prepareVideoRecorder()) {   // Try to prepare mediarecorder
                        mMediaRecorder.start(); // Start recording if mediarecorder prepared successfully
                        //  recordButtonText.setText("Stop");
                        recordButtonIcon.setImageDrawable(getDrawable(R.drawable.ic_stop_black_36dp));  // Toggle Button Image
                        toggleCamera.setEnabled(false);
                        toggleCamera.setVisibility(View.GONE);
                        isRecording = true;
                        seconds = 1;    //Reset Timer
                        timer.setVisibility(View.VISIBLE);  // Start Recording Timer
                        timer.setText("00:00");
                        timerHandler.postDelayed(timerTask, 1000);
                    }
                    else {
                        recordButton.setEnabled(true);
                        releaseMediaRecorder(); // Release mediarecorder for other applications if failed to prepare
                        Toast.makeText(RecordActivity.this, "Error occurred while trying to record video!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    // Prepare Camera
    private static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(camID); // attempt to get a Camera instance
        } catch (Exception e) {
            Log.d("Error!", "Cannot open Camera Instance : " + e.getMessage()); // Camera is not available
        }
        return c; // returns null if camera is unavailable
    }

    // Prepare CAmera Preview
    private void preparePreview() {
        releaseCamera();
        mCamera = getCameraInstance();  // Get a new Camera Instance
        if (mCamera != null) {
            mCameraPreview = new CameraPreview(this, mCamera);  // Create a new Camera Surface View
            preview.addView(mCameraPreview);
            mCamera.setDisplayOrientation(90);  //Lock preview in Portrait Orientation
            getCameraParameters();
            mCamera.setParameters(params);  // Set Parameters for Preview
            mCamera.startPreview(); // Start the Preview
        }
    }

    // Prepare mediarecorder for Video Recording
    private boolean prepareVideoRecorder() {
        releaseMediaRecorder();
        mMediaRecorder = new MediaRecorder();   // Get a new MediaRecorder Instance
        mCamera.unlock();   //Unlock and set camera to MediaRecorder
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT); //Set Audio source
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);    //Set Video source
        if (camID == Camera.CameraInfo.CAMERA_FACING_BACK) {    // Lock Orientation of Video to portrait
            mMediaRecorder.setOrientationHint(90);
        } else {
            mMediaRecorder.setOrientationHint(270);
        }
        mMediaRecorder.setMaxDuration(120000);  // Set max. duration to 2:00 mins
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    recordButton.callOnClick(); // Launch Upload Dialog if Max. File size reached
                }
            }
        });
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P)); // Set Video Quality
        mUri = getOutputMediaFileUri(); // Get Temp. File Save Location
        mMediaRecorder.setOutputFile(mUri.getPath());  // Set Output File Type
        mMediaRecorder.setPreviewDisplay(mCameraPreview.getHolder().getSurface());  // Set the Video preview output Surface

        try {
            mMediaRecorder.prepare();   // Prepare MediaRecorder for Recording
        } catch (IllegalStateException e) { // Release MediaRecorder for other applications if failed to prepare
            Log.d("Error!", "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d("Error!", "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    // Prepare Camera Parameters
    private void getCameraParameters() {
        params = mCamera.getParameters();   // Get Default Parameters
        // Toast.makeText(this, "Triggered!!", Toast.LENGTH_LONG).show();
        //params.setRotation(90);

        List<String> focusModes = params.getSupportedFocusModes();  // Turn On AutoFocus
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        List<Camera.Size> displaySizes = params.getSupportedPreviewSizes(); // Set 480p Video Resolution
        for (Camera.Size s : displaySizes) {
            if (s.width == 720 && s.height == 480) {
                params.setPreviewSize(s.width, s.height);
                break;
            }
        }
    }

    // Get URi of Temp. File
    private static Uri getOutputMediaFileUri() {
        return Uri.fromFile(getOutputMediaFile());
    }

    // Create Temp. File
    private static File getOutputMediaFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()); // Set Filename to current TimeStamp
        mFileName = "VID_" + timeStamp + ".mp4";

        File videoFolder = new File(Environment.getExternalStorageDirectory() + "/Vidzz/Vidoes");
        if (!videoFolder.exists()) {
            videoFolder.mkdirs()
            ;        }
        File mediaFile = new File(videoFolder, mFileName);    // Create new Temp. file with path
        return mediaFile;
    }

    // Release MediaRecorder for other applications
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mCamera.lock();
        }
    }

    // Release Camera for other applications
    private void releaseCamera() {
        preview.removeAllViews();   // Remove previous Previews++
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
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
    protected void onResume() {
        super.onResume();
        if (!hasPermissions(this, PERMISSIONS)) {   // Check permissions before starting Activity
            requestPermissions();
        } else {
            preparePreview();
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder(); // Release MediaRecorder and Camera after Focus Lost
        releaseCamera();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);  // Lock Activity in Portrait Mode
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101) {   // Check if permissions Granted or not
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                    this.onResume();    // Start Multimedia operations if all permissions granted
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
