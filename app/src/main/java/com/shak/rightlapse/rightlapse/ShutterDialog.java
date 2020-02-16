package com.shak.rightlapse.rightlapse;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ShutterDialog extends Dialog {

    TimelapseConfiguration timelapseConfiguration;
    BaseFragment baseFragment;

    public SeekBar shutterBar;
    public CheckBox autoBox;
    private TextView shutterValue;

    public ShutterDialog(Context context, TimelapseConfiguration tlc, BaseFragment bs) {
        super(context);
        timelapseConfiguration = tlc;
        baseFragment = bs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.shutter_dialog);
        getWindow().setBackgroundDrawableResource(R.color.transblack);
        getWindow().setDimAmount(0.0f);

        shutterBar = findViewById(R.id.ShutterBar);
        autoBox = findViewById(R.id.shutterCheck);
        timelapseConfiguration.shutterCheck = autoBox;
        shutterValue = findViewById(R.id.shutterValue);

        autoBox.setChecked(timelapseConfiguration.autoShutter);

        timelapseConfiguration.shutterSpeeds.put( "1/8000", 125000L);
        timelapseConfiguration.shutterSpeeds.put( "1/6400", 156250L);
        timelapseConfiguration.shutterSpeeds.put( "1/5000", 200000L);
        timelapseConfiguration.shutterSpeeds.put( "1/4000", 250000L);
        timelapseConfiguration.shutterSpeeds.put( "1/3200", 312500L);
        timelapseConfiguration.shutterSpeeds.put( "1/2500", 400000L);
        timelapseConfiguration.shutterSpeeds.put( "1/2000", 500000L);
        timelapseConfiguration.shutterSpeeds.put( "1/1600", 625000L);
        timelapseConfiguration.shutterSpeeds.put( "1/1000", 1000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/800", 1250000L);
        timelapseConfiguration.shutterSpeeds.put( "1/500", 2000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/400", 2500000L);
        timelapseConfiguration.shutterSpeeds.put( "1/250", 4000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/160", 6250000L);
        timelapseConfiguration.shutterSpeeds.put( "1/125", 8000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/80", 12500000L);
        timelapseConfiguration.shutterSpeeds.put( "1/50", 20000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/30", 33333335L);
        timelapseConfiguration.shutterSpeeds.put( "1/15", 66666668L);
        timelapseConfiguration.shutterSpeeds.put( "1/10", 100000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/6", 166666668L);
        timelapseConfiguration.shutterSpeeds.put( "1/4", 250000000L);
        timelapseConfiguration.shutterSpeeds.put( "1/2", 500000000L);
        timelapseConfiguration.shutterSpeeds.put( "0.8", 800000000L);
        timelapseConfiguration.shutterSpeeds.put( "1", 1000000000L);
        timelapseConfiguration.shutterSpeeds.put( "1.6", 1600000000L);
        timelapseConfiguration.shutterSpeeds.put( "2", 2000000000L);
        timelapseConfiguration.shutterSpeeds.put( "3", 3000000000L);
        timelapseConfiguration.shutterSpeeds.put( "4", 4000000000L);
        timelapseConfiguration.shutterSpeeds.put( "6", 6000000000L);
        timelapseConfiguration.shutterSpeeds.put( "8", 8000000000L);
        timelapseConfiguration.shutterSpeeds.put( "10", 10000000000L);
        timelapseConfiguration.shutterSpeeds.put( "13", 13000000000L);
        timelapseConfiguration.shutterSpeeds.put( "15", 15000000000L);
        timelapseConfiguration.shutterSpeeds.put( "20", 20000000000L);
        timelapseConfiguration.shutterSpeeds.put( "25", 25000000000L);
        timelapseConfiguration.shutterSpeeds.put( "30", 30000000000L);

        final List<String> possibleSpeeds = new ArrayList<String>();



        for(String cu : timelapseConfiguration.shutterSpeeds.keySet()){

            Long speed = timelapseConfiguration.shutterSpeeds.get(cu);
            if(speed >= timelapseConfiguration.shutterRange.getLower() && speed <= timelapseConfiguration.shutterRange.getUpper())
                possibleSpeeds.add(cu);


        }

        shutterBar.setMax(possibleSpeeds.size()-1);


        shutterBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                String name = possibleSpeeds.get(progress);
                Long speed = timelapseConfiguration.shutterSpeeds.get(name);

                //if(possibleSpeeds.size() > progress+1 && timelapseConfiguration.shutterSpeeds.get(possibleSpeeds.get(progress+1))/1000000 > baseFragment.timeToMillis()) {

                  //  timelapseConfiguration.safeSpeed = speed;
                //}

                //if(speed < timelapseConfiguration.safeSpeed) {
                    shutterValue.setText("Shutter speed: " + name + " seconds");
                    timelapseConfiguration.safePosition = progress;
                    baseFragment.changeCameraShutter(autoBox.isChecked(), speed);
                //}
                //else{

                  //  shutterValue.setText("Shutter speed: Can't be greater than frame interval("+baseFragment.timeToMillis()+" ms)");

                //}

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

                autoBox.setChecked(false);

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                //if(timelapseConfiguration.safePosition < seekBar.getProgress()){

                  //  seekBar.setProgress(timelapseConfiguration.safePosition);
                //}

            }
        });

        autoBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {


                if(isChecked)
                    shutterValue.setText("Shutter speed: Auto");
                else
                    shutterValue.setText("Shutter speed: " + possibleSpeeds.get(shutterBar.getProgress()) );

                baseFragment.changeCameraShutter(isChecked, timelapseConfiguration.shutterSpeed);


            }
        });

    }


}
