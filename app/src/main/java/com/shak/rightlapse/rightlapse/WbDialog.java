package com.shak.rightlapse.rightlapse;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

public class WbDialog extends Dialog {


    TimelapseConfiguration timelapseConfiguration;
    BaseFragment baseFragment;

    SeekBar wbBar;
    CheckBox autoBox;

    public WbDialog(Context context, TimelapseConfiguration tlc, BaseFragment bs) {
        super(context);
        timelapseConfiguration = tlc;
        baseFragment = bs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.wb_dialog);
        getWindow().setBackgroundDrawableResource(R.color.transblack);
        getWindow().setDimAmount(0.0f);

        wbBar = findViewById(R.id.seekBar);
        autoBox = findViewById(R.id.wbCheck);

        autoBox.setChecked(timelapseConfiguration.autoWb);

        wbBar.setMax(100);

        wbBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                baseFragment.changeCameraWb(autoBox.isChecked(), progress);

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

                autoBox.setChecked(false);

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {



            }
        });

        autoBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {



                baseFragment.changeCameraWb(isChecked, wbBar.getProgress());

            }
        });
    }


   /* @Override
    public void show() {
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        this.getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }*/
}
