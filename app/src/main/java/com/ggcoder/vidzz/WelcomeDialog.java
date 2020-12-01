package com.ggcoder.vidzz;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

public class WelcomeDialog extends Dialog {

    private Context mContext;

    WelcomeDialog(@NonNull Context context) {
        super(context);
        mContext = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.welcome_dialog);

        try {
            this.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);   //Stretch the Dialog Box to fit on screen but maintain aspect ratio
            this.getWindow().setBackgroundDrawable(mContext.getResources().getDrawable(R.drawable.shadow));
        } catch (NullPointerException e) {

        }

        final Dialog d = this;
        final View discard = findViewById(R.id.btn_dismiss);
        discard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
    }
}
