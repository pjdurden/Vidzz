package com.ggcoder.vidzz;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UploadDialog extends Dialog {

    private Context mContext;
    private Uri mUri;
    private String mFileName;
    private StorageReference mStorageRef;
    private FirebaseDatabase mDatabase;

    private VideoView mVideoView;
    private View mView;

    UploadDialog(@NonNull Context context) {    //Constructor
        super(context);
        mContext = context;
    }

    interface onResultListener {    // interface to tell parent activity if it will be destroyed
        void onResult(boolean ifDestroy);
    }

    private onResultListener mListener;

    UploadDialog(@NonNull Context context, @NonNull Uri uri, @NonNull String file, onResultListener listener) {  //Constructor with predefining Uri
        super(context);
        mContext = context;
        mUri = uri;
        mFileName = file;
        mListener = listener;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        try {
            FrameLayout fl = findViewById(R.id.frame_layout);
            TextView tv = findViewById(R.id.textView);
            View upload = findViewById(R.id.btn_upload);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) fl.getLayoutParams();
            ViewGroup.LayoutParams param = mVideoView.getLayoutParams();

            if (mVideoView.getHeight() > (mView.getHeight() - mView.getPaddingTop() - mView.getPaddingBottom() - tv.getHeight() - upload.getHeight() - params.bottomMargin - params.topMargin)) {
                param.height = (mView.getHeight() - mView.getPaddingTop() - mView.getPaddingBottom() - tv.getHeight() - upload.getHeight() - params.bottomMargin - params.topMargin);
                mVideoView.setLayoutParams(param);
                mVideoView.requestLayout();
              //  param.width = (params.height*2)/3;
            }

            this.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);   //Stretch the video to fit on screen but maintain aspect ratio
            this.getWindow().setBackgroundDrawable(mContext.getResources().getDrawable(R.drawable.shadow));
        } catch (NullPointerException e) {

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.video_confirm_dialog);

        //Initialize Firebase Features
        mStorageRef = FirebaseStorage.getInstance().getReference();
        mDatabase = FirebaseDatabase.getInstance();

        //Initialize Views
        mVideoView = findViewById(R.id.vv_upload_preview);
        mVideoView.setVideoURI(mUri);   //Set preview source to lacally created temp file
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mVideoView.start(); //Start Preview in loop
                mp.setLooping(true);
            }
        });

        mView = findViewById(R.id.dialog_main_layout);

        //Lock the Dialog Box
        this.setCancelable(false);

        final Dialog dialog = this;
        View.OnClickListener backPressed = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                File f = new File(mUri.getPath());  //Delete local file if Dialog is dismissed
                if (f.exists()) {
                    f.delete();
                }
                mListener.onResult(false);
            }
        };

        //Initialize Buttons
        View upload = findViewById(R.id.btn_upload);
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()); //Set Firebase File name as current time
                StorageReference videoRef = mStorageRef.child("videos/" + mFileName);
                final DatabaseReference dataRef = mDatabase.getReference();

                mListener.onResult(true);

                videoRef.putFile(mUri)  //Save File in Firebase Storage
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                dataRef.child("videos").child(timeStamp).setValue(mFileName);  //Save Filename in Firebase Database
                                Toast.makeText(mContext, "Your recording uploaded successfully!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                File f = new File(mUri.getPath());
                                if (f.exists()) {
                                    f.delete(); //Delete local file if failed to upload
                                }
                                Toast.makeText(mContext, "Failed to upload recording! : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });

                Intent i = new Intent(mContext, LandingActivity.class); //Go back to LandingActivity after clicking Upload Button
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(i);
            }
        });

        View discard = findViewById(R.id.btn_discard);
        discard.setOnClickListener(backPressed);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        this.dismiss();
        File f = new File(mUri.getPath());  //Delete local file if Dialog is dismissed
        if (f.exists()) {
            f.delete();
        }
        mListener.onResult(false);
    }
}
