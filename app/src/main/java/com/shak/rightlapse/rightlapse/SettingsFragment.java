package com.shak.rightlapse.rightlapse;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener{

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        addPreferencesFromResource(R.xml.pref_general);

       /* if(!isExternalStorageWritable() && !Environment.isExternalStorageRemovable() && !Environment.isExternalStorageEmulated()) {
            android.support.v7.preference.Preference memory = findPreference("memory");
            memory.setSummary("No external memory detected");
            memory.setEnabled(false);
        }*/
       Preference fpsPreference = findPreference("fps");
       SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
       String value = sharedPreferences.getString("fps", "30");

           fpsPreference.setSummary("Current playback frame rate: " + value + " fps");

        Preference privacy = findPreference("privacy");
       // Preference donate = findPreference("donate");
        Preference report = findPreference("report");
        privacy.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                String url = "https://lapse-full-manual-t.flycricket.io/privacy.html";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return false;
            }
        });
       /* donate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                String url = "https://paypal.me/shakovtech";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return false;
            }
        });*/

        report.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                String url = "mailto:shakovtech@gmail.com";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return false;
            }
        });



    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

           /*if(key.equals("sleep")){

               Preference vital = findPreference("vitalinfo");
               if(sharedPreferences.getBoolean("sleep",false))
                   vital.setEnabled(true);
               else {
                   vital.setEnabled(false);
               }
           }*/

           if(key.equals("fps")){

               Preference fps = findPreference("fps");
               String value = sharedPreferences.getString("fps", "30");

               fps.setSummary("Current playback frame rate: " + value  + " fps");
           }

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /*public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }*/
}
