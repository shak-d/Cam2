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

public class IsoDialog extends Dialog {


    public SeekBar isoBar;
    public CheckBox autoBox;
    private TextView isoValue;

    TimelapseConfiguration timelapseConfiguration;
    BaseFragment baseFragment;

    public IsoDialog(Context context, TimelapseConfiguration tlc, BaseFragment bs) {
        super(context);
        timelapseConfiguration = tlc;
        baseFragment = bs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.iso_dialog);
        getWindow().setBackgroundDrawableResource(R.color.transblack);
        getWindow().setDimAmount(0.0f);

        isoBar = findViewById(R.id.seekBar);
        autoBox = findViewById(R.id.isoCheck);
        isoValue = findViewById(R.id.isoValue);

        List<Integer> possibleIsos = new ArrayList<Integer>();

        timelapseConfiguration.autoisoCheck = autoBox;
        autoBox.setChecked(timelapseConfiguration.autoiso);

        for(int iso : timelapseConfiguration.isos){

            if(iso >= timelapseConfiguration.isoRange.getLower() && iso <= timelapseConfiguration.isoRange.getUpper())
                possibleIsos.add(iso);

        }

        isoBar.setMax(possibleIsos.size()-1);
        timelapseConfiguration.possibleIsos = possibleIsos;

        isoBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                int iso = timelapseConfiguration.possibleIsos.get(progress);
                isoValue.setText("ISO: " + iso);
                baseFragment.changeCameraIso(autoBox.isChecked(), iso);
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

                int iso = timelapseConfiguration.possibleIsos.get(isoBar.getProgress());
                if(isChecked)
                    isoValue.setText("ISO: Auto");
                else
                    isoValue.setText("ISO: " + iso);

                baseFragment.changeCameraIso(isChecked, iso);

            }
        });

    }

}
