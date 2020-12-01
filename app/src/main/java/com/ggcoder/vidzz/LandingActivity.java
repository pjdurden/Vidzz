package com.ggcoder.vidzz;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LandingActivity extends AppCompatActivity {

    private float y1, y2;
    private Integer currIndex = 0;
    private String currFile = "";
    private long prevCount = 0;
    private static boolean isFirstStart = true;

    private String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
    };

    private VideoView mVideoView;
    private View mView;
    private ProgressDialog bufferProgressDialog;

    static String uriPrefix = "https://firebasestorage.googleapis.com/v0/b/vidzz-1e92a.appspot.com/o/videos%2F";
    static String uriSuffix = "?alt=media";

    private StorageReference mStorageRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);

        //Toast.makeText(this, "Came here", Toast.LENGTH_SHORT).show();
        //Checking if application is launching for first time
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (!prefs.getBoolean("ranBefore", false)) {
            new WelcomeDialog(this).show(); //Show Welcome message and Instructions at first launch
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("ranBefore", true);
            editor.apply();
        }

        //Restore State of Media Player if Activity Restarts
        FileInputStream is;
        BufferedReader reader;
        final File file = new File(Environment.getExternalStorageDirectory() + "/Vidzz/Temp/temp.txt");
        if (file.exists()) {
            try {
                is = new FileInputStream(file);
                reader = new BufferedReader(new InputStreamReader(is));
                currFile = reader.readLine();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // check for required permissions
        if (hasPermissions(this, PERMISSIONS)) {
            initialise();
        }
        else {
            requestPermissions();
        }
    }

    private void initialise() {
        //Initialize Firebase Features
        mStorageRef = FirebaseStorage.getInstance().getReference();
        final FirebaseDatabase mDatabase = FirebaseDatabase.getInstance();

        final List<String> videoList = new ArrayList<String>(); //Initialise list to manage Video List

        //Initialize Views
        final ImageView edgeEffectBottom = findViewById(R.id.iv_edge_bottom);
        final ImageView edgeEffectTop = findViewById(R.id.iv_edge_top);
        final Animation slideUpDrop = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up_drop);  // Setting u[ edge effect animations
        final Animation slideDownDrop = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_down_drop);
        final Animation setScale = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.set_scale);
        edgeEffectBottom.startAnimation(setScale);
        edgeEffectTop.startAnimation(setScale);

        // create buffer progress dialog
        bufferProgressDialog = new ProgressDialog(LandingActivity.this);
        bufferProgressDialog.setMessage("Loading the Video...\nPlease Wait!");
        bufferProgressDialog.setTitle("Buffering...");
        bufferProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        bufferProgressDialog.setCancelable(false);

        // setup video view
        mVideoView = findViewById(R.id.vv_main);
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { //Set video on loop
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
                bufferProgressDialog.dismiss();
            }
        });
        mView = findViewById(R.id.main_layout);

        //Attach Swipe Gestures listener to parent View
        OnSwipeTouchListener onSwipeTouchListener = new OnSwipeTouchListener(this, mView);
        OnSwipeTouchListener.OnSwipe onSwipe = new OnSwipeTouchListener.OnSwipe() {
            @Override
            public void onSwipeDown() { //Play previous video on Swipe Down Gesture
                // Toast.makeText(LandingActivity.this, "Swiped Down", Toast.LENGTH_SHORT).show();
                if (currIndex != 0) {   //Check if there is any video before
                    currIndex--;    //Decreasing Video Index
                    currFile = videoList.get(currIndex);
                    Toast.makeText(LandingActivity.this, "Playing Previous...", Toast.LENGTH_SHORT).show();
                    playVideo();
                }
                else {
                    edgeEffectTop.startAnimation(slideDownDrop);
                }
            }

            @Override
            public void onSwipeUp() {   //Play next video on Swipe Up Gesture
                // Toast.makeText(LandingActivity.this, "Swiped Up", Toast.LENGTH_SHORT).show();
                if (currIndex < videoList.size() - 1) {  //Check if there is any video after
                    currIndex++;    //Increasing Video Index
                    currFile = videoList.get(currIndex);
                    Toast.makeText(LandingActivity.this, "Playing Next...", Toast.LENGTH_SHORT).show();
                    playVideo();
                }
                else {
                    edgeEffectBottom.startAnimation(slideUpDrop);
                }
            }
        };
        onSwipeTouchListener.setOnSwipeListener(onSwipe);
        mView.setOnTouchListener(onSwipeTouchListener);

        // fetch videos list from firebase
        mDatabase.getReference().child("videos").addValueEventListener(new ValueEventListener() {
           @Override
           public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
               for (DataSnapshot d : dataSnapshot.getChildren()) {
                   if (!videoList.contains(d.getValue().toString())) {
                       if (dataSnapshot.getChildrenCount() == (prevCount + 1) ) {   // show new video added alert only after initialisation
                           Toast.makeText(LandingActivity.this, "New Video Added!", Toast.LENGTH_SHORT).show();
                       }
                       prevCount = dataSnapshot.getChildrenCount();
                       videoList.add(d.getValue().toString());
                   }
               }
               Collections.sort(videoList, Collections.<String>reverseOrder());    //Sorting the videos in reverse order of Date
               if (!videoList.isEmpty()) {
                   // Toast.makeText(LandingActivity.this, videoList.get(0), Toast.LENGTH_LONG).show();
                   if (currFile == "" || isFirstStart) {
                       currFile = videoList.get(currIndex);
                   }
                   else if (videoList.contains(currFile)){
                       currIndex = videoList.indexOf(currFile);
                   }
                   playVideo();
                   //   Toast.makeText(LandingActivity.this, uri.toString(), Toast.LENGTH_LONG).show();
               }
           }

           @Override
           public void onCancelled(@NonNull DatabaseError databaseError) {

           }
       });

        //Setting action for record button click that starts the RecordingActivity
        View recordButton = findViewById(R.id.btn_record_main);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(LandingActivity.this, RecordActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });
    }

    private void playVideo() {
        try {
            bufferProgressDialog.show(); // Show buffering dialog box
        } catch (Exception e){
            Log.d("Error!", e.getMessage());
        }
        final File f = new File(Environment.getExternalStorageDirectory() + "/Vidzz/Vidoes/" + currFile);
       // Toast.makeText(this, f.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        if (f.exists()) {
            Uri uri = Uri.fromFile(f);  // play from local storage if exists there
            mVideoView.setVideoURI(uri);    //Setting New Video Uri
            mVideoView.start(); //Play new Video
        }
        else {  // if local cache doesn't exists
            File d = new File(Environment.getExternalStorageDirectory() + "/Vidzz/Vidoes/");    // make required directories
            d.mkdirs();
            //f.mkdirs();
          //  Toast.makeText(this, "Downloading File..."+f.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            mStorageRef.child("videos/" + currFile).getFile(f)  // download video to local cache
                    .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                          //  Toast.makeText(getApplicationContext(), f.getAbsolutePath(), Toast.LENGTH_LONG).show();
                          //  Snackbar snackbar = Snackbar.make(mView, f.getAbsolutePath(), BaseTransientBottomBar.LENGTH_LONG);
                                //    snackbar.show();
                            mVideoView.setVideoURI(Uri.fromFile(f));    //Setting New Video Uri
                            mVideoView.start(); //Play new Video
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() { // directly stream from Firebase storage
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            f.delete();
                            Uri uri = Uri.parse(uriPrefix + currFile + uriSuffix);
                            mVideoView.setVideoURI(uri);    //Setting New Video Uri
                            mVideoView.start(); //Play new Video
                        }
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVideoView.start(); //Restart Video Player after focus coming back to activity
    }

    @Override
    protected void onPause() {
        super.onPause();
        isFirstStart = false;
        File d = new File(Environment.getExternalStorageDirectory() + "/Vidzz/Temp/");  // create backup of current filename
        d.mkdirs();
        File f = new File(d, "temp.txt");
        try {
            FileOutputStream fos = new FileOutputStream(f);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
            bw.write(currFile);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("index", currIndex);    //Saving file index for retrieval after activity restart
        outState.putString("uri", currFile);
        super.onSaveInstanceState(outState);
    }

    //Custom class for detecting swipe gestures using OnTouchListener and Gesture Detector
    public static class OnSwipeTouchListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;
        private Context context;

        public interface OnSwipe {  //Interface to post callouts to listener
            public void onSwipeDown();
            public void onSwipeUp();
        }

        private OnSwipe listener;

        OnSwipeTouchListener(Context ctx, View mainView) {  //Constructor
            gestureDetector = new GestureDetector(ctx, new GestureListener());
            mainView.setOnTouchListener(this);
            context = ctx;
            listener = null;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

        public void setOnSwipeListener(OnSwipe onSwipe) {
            listener = onSwipe;
        }

        //Custom gesture listener class to examine fling events
        public class GestureListener extends GestureDetector.SimpleOnGestureListener {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                boolean result = false;
                try {
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {  //Compare initial and final Y coordinates to check which gesture occured
                        if (diffY > 0) {
                            onSwipeBottom();
                        } else if (Math.abs(-1.0*diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            onSwipeTop();
                        }
                        result = true;
                    }
                }
                catch (Exception exception) {
                    exception.printStackTrace();
                }
                return result;
            }
        }

        void onSwipeTop() {
         //   Toast.makeText(context, "Swiped Up", Toast.LENGTH_SHORT).show();
            listener.onSwipeUp();
        }

        void onSwipeBottom() {
        //    Toast.makeText(context, "Swiped Down", Toast.LENGTH_SHORT).show();
            listener.onSwipeDown();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101) {   // Check if permissions Granted or not
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                    initialise();    // Start Multimedia operations if all permissions granted
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
