package com.shak.rightlapse.rightlapse;

import android.app.AlertDialog;
import android.hardware.camera2.CameraCaptureSession;
import android.media.MediaRecorder;
import android.support.v4.app.DialogFragment;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.CheckBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

public class TimelapseConfiguration {


    public boolean infiniteDuration = true;
    public int hourDuration = 0;
    public int minuteDuration = 0;
    public int minutesInterval = 0;
    public int secondsInterval = 1;
    public int msInterval = 0;


    AlertDialog durationDialog = null;
    DialogFragment pickerDialog = null;
    AlertDialog intervalDialog = null;
    AlertDialog resDialog = null;

    /*
     *
     * 0=2160
     * 1=1080
     * 2=720
     * 3=480
     *
     * */
    boolean initialize = false;
    public List<List<Size>> resolutions = new ArrayList<List<Size>>(4);
    public List<List<Size>> ratios = new ArrayList<List<Size>>(4);
    Size ChosenRes; //default lowest

    boolean shutterControl  = false;
    boolean focusControl    = false;
    boolean isoControl      = false;
    boolean wbcontrol       = false;

    boolean autoShutter = true;
    boolean autoFocus   = true;
    boolean autoiso     = true;
    boolean autoWb      = true;

    Range<Long> shutterRange;
    Range<Integer> isoRange;

    float focusDistance = 0.0f;
    float minimumFocus = 0.0f;
    boolean focusMeters = false;
    FocusDialog focusDialog = null;


    int[] isos = {50, 100, 150, 200, 250, 300, 350, 400, 500, 650, 800, 1000, 1300, 1600, 2500, 3200, 4000, 6000};
    int currentiso = 100;
    List<Integer> possibleIsos;
    IsoDialog isoDialog = null;
    CheckBox autoisoCheck = null;

    int wb = 50; //from 0 to 100
    WbDialog wbDialog = null;

    ShutterDialog shutterDialog = null;
    CheckBox shutterCheck = null;
    Long shutterSpeed = 33333335L;
    Map<String, Long> shutterSpeeds = new LinkedHashMap<>();
    Long lastSpeed = 33333335L;
    int safePosition = 1000;

    boolean isRecording = false;
    Timer elapsedTimer = null;
    int eSec = 0;
    int eMin = 0;
    int eHou = 0;

    MediaRecorder enc;
    View recordView;
    CameraCaptureSession.CaptureCallback captureCallback;
    SleepDialog sleepDialog = null;

    CameraGrid grid;

    boolean justStarted = true;

    //480, 720, 1080, 4k
    int[] bitrates = {3000000, 4300000, 5800000, 6800000};
    int bitrate = 0;
    boolean customBitrate = false;
    int actualBitrate = 2500000;

    int capturedFrames = 0;
    Surface recordingSurface;
}
