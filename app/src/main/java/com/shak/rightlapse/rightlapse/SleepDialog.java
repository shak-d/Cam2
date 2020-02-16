package com.shak.rightlapse.rightlapse;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;


public class SleepDialog extends DialogFragment  implements OnClickListener {

   public TextView line1, line2, line3;
   boolean recording = false;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            line3.setText("Battery level: "+level+"%");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.sleep);
        getActivity().registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.sleep_layout, container, false);
        view.findViewById(R.id.sleeapingBueau).setOnClickListener(this);

        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        view.setSystemUiVisibility(uiOptions);
        line1 = view.findViewById(R.id.Sline1);
        line2 = view.findViewById(R.id.Sline2);
        line3 = view.findViewById(R.id.Sline3);
        line1.setAlpha(0.5f);
        line2.setAlpha(0.5f);
        line3.setAlpha(0.5f);

        return view;
    }
    @Override
    public void onClick(View view){

        dismiss();

    }

    @Override
    public void onDismiss(DialogInterface dialog) {

        final FragmentTransaction ab = getFragmentManager().beginTransaction();

       // final FragmentTransaction ft = getParentFragment().getFragmentManager().beginTransaction();

        final SleepDialog df = this;

        new Thread("spx"){
            @Override
            public void run() {
                showToast("Resuming Sleep mode in 20 seconds");
                try {
                    sleep(20000);
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
                if(recording)
                    df.show(ab, "Rightlapse:abc");
            }
        }.start();

        super.onDismiss(dialog);
    }

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

  /*  @Override
    public int show(FragmentTransaction manager, String tag) {

          // getDialog().getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

           int k = super.show(manager, tag);
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
        getDialog().getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        getDialog().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        return k;
    }*/
}
