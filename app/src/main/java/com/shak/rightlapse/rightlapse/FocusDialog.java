package com.shak.rightlapse.rightlapse;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

public class FocusDialog extends Dialog {


    public SeekBar focusBar;
    public CheckBox autoBox;
    TimelapseConfiguration timelapseConfiguration;
    BaseFragment baseFragment;

    public FocusDialog(Context context, TimelapseConfiguration tlc, BaseFragment bs) {
        super(context);
        timelapseConfiguration = tlc;
        baseFragment = bs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.focus_dialog);
        getWindow().setBackgroundDrawableResource(R.color.transblack);
        getWindow().setDimAmount(0.0f);
        focusBar = findViewById(R.id.focusSeek);
        autoBox = findViewById(R.id.checkBox);
        focusBar.setMax((int) timelapseConfiguration.minimumFocus * 10);


        focusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                baseFragment.changeCameraFocus(autoBox.isChecked(), (timelapseConfiguration.minimumFocus - ((float)progress/10.0f)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                autoBox.setChecked(false);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        autoBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                baseFragment.changeCameraFocus(isChecked, timelapseConfiguration.minimumFocus - ((float) focusBar.getProgress() / 10.0f));


            }
        });
    }


}
