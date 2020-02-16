package com.shak.rightlapse.rightlapse;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.widget.ArrayAdapter;
import android.app.TimePickerDialog;
import android.widget.TimePicker;
import android.view.WindowManager;


import static java.lang.Thread.sleep;

public class BaseFragment extends Fragment
        implements  View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "RightLapseTest";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;



    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    //private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;
   // private Size tmpSize;

    TimelapseConfiguration timelapseConfiguration;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
   private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //if(timelapseConfiguration.isDone)
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
            //else {
               // mBackgroundHandler.post(new AddExposure(timelapseConfiguration, reader.acquireNextImage()));
               // AddExposure(reader.acquireNextImage());

            //}
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);


    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    private Surface surface;

    private Handler mTimerHandler = new Handler();

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                        if(!timelapseConfiguration.isRecording)
                            timelapseConfiguration.lastSpeed = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        //captureStillPicture();
                        startShooting();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            //captureStillPicture();
                            startShooting();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        //captureStillPicture();
                        startShooting();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
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


    /**private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {



        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }



    }*/

    public static BaseFragment newInstance() {
        return new BaseFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        view.findViewById(R.id.video_length).setOnClickListener(this);
        view.findViewById(R.id.frame_interval).setOnClickListener(this);
        view.findViewById(R.id.quality).setOnClickListener(this);
        view.findViewById(R.id.aspect_ratio).setOnClickListener(this);
        view.findViewById(R.id.cameraShutter).setOnClickListener(this);
        view.findViewById(R.id.cameraFocus).setOnClickListener(this);
        view.findViewById(R.id.cameraIso).setOnClickListener(this);
        view.findViewById(R.id.cameraWb).setOnClickListener(this);
        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);


        TextView line1 = view.findViewById(R.id.line1);
        TextView line2 = view.findViewById(R.id.line2);

        line1.setText("Recording time: Unlimited");
        line2.setText("Frame interval: 00:01.0");

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission();
        }
       /* if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WAKE_LOCK)
                != PackageManager.PERMISSION_GRANTED) {
            requestDisplayPermission();
        }*/

        if(!sharedPreferences.contains("disclosure")){

            sharedPreferences.edit().putBoolean("disclosure", false).apply();

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage("This app makes use of the Camera2 API, some devices may not fully support certain features(focus, exposure and white balance).");
            builder.setTitle("Disclaimer");
            builder.setPositiveButton("I understand", null);
            builder.show();
        }

        if(!sharedPreferences.contains("rate")){

            sharedPreferences.edit().putInt("rate", 0).apply();

        }
        else{

            int count = sharedPreferences.getInt("rate", 0);

            if(count == 4){

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage("If you enjoyed the app and found it useful, please rate it on Google play, it really helps us to get more visibility on the store");
                builder.setTitle("Help us, rate the app");
                builder.setPositiveButton("Rate the app", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sharedPreferences.edit().putInt("rate", 10).apply();
                        Uri uri = Uri.parse("market://details?id=" + getContext().getPackageName());
                        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                        // To count with Play market backstack, After pressing back button,
                        // to taken back to our application, we need to add following flags to intent.
                        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        try {
                            startActivity(goToMarket);
                        } catch (ActivityNotFoundException e) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("http://play.google.com/store/apps/details?id=" + getContext().getPackageName())));
                        }
                    }
                });

                builder.setNegativeButton("Later", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sharedPreferences.edit().putInt("rate", -5).apply();
                    }
                });

                builder.show();

            }
            else{

                sharedPreferences.edit().putInt("rate", count+1).apply();

            }

        }


    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //mFile = new File(getActivity().getExternalFilesDir(null), "file.mp4");
        //Date time = Calendar.getInstance().getTime();

       new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+File.separator+"Lapse").mkdirs();

        timelapseConfiguration = new TimelapseConfiguration();

    }

    @Override
    public void onResume() {
        super.onResume();
        if(!timelapseConfiguration.isRecording) {
            startBackgroundThread();
            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
            // a camera and start preview from here (otherwise, we wait until the surface is ready in
            // the SurfaceTextureListener).
            if (mTextureView.isAvailable()) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        }
    }

    @Override
    public void onPause() {
        if(!timelapseConfiguration.isRecording) {
            closeCamera();
            stopBackgroundThread();

        }
        else{
            stopRecording();
            closeCamera();
            stopBackgroundThread();
        }
        super.onPause();
    }

    private void requestStoragePermission(){

        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            //new ConfirmationDialogStorage().show(getChildFragmentManager(), FRAGMENT_DIALOG);
           /* new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission_storage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    2);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    if (getActivity() != null) {
                                        getActivity().finish();
                                    }
                                }
                            })
                    .create();*/
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 2);
        }

    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
           // new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        }
        else if(requestCode == 2){

            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission_storage))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }

        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                if(!timelapseConfiguration.initialize){

                    setupCameraFeatures(characteristics, timelapseConfiguration);
                    InitializeQuality(map);
                    timelapseConfiguration.initialize = true;
                }

             //   mImageReader = ImageReader.newInstance(timelapseConfiguration.ChosenRes.getWidth(), timelapseConfiguration.ChosenRes.getHeight(),
               //         ImageFormat.JPEG, /*maxImages*/3);

                //tmpSize = new Size(map.getOutputSizes(ImageFormat.RAW_SENSOR)[0].getWidth(),map.getOutputSizes(ImageFormat.RAW_SENSOR)[0].getHeight());

               // mImageReader = ImageReader.newInstance(timelapseConfiguration.ChosenRes.getWidth(),timelapseConfiguration.ChosenRes.getWidth(), ImageFormat.JPEG, 6 );

               // int b = ImageFormat.getBitsPerPixel(ImageFormat.RAW_SENSOR);


                //mImageReader.setOnImageAvailableListener(
                  //      mOnImageAvailableListener, mBackgroundHandler);

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

               mPreviewSize = timelapseConfiguration.ChosenRes;

                if(timelapseConfiguration.ChosenRes.getWidth() > MAX_PREVIEW_WIDTH)
                {
                    float t1 = (float)timelapseConfiguration.ChosenRes.getWidth()/(float)timelapseConfiguration.ChosenRes.getHeight();

                    if(t1 == 4.0f/3.0f)
                        mPreviewSize = new Size(1440, 1080);
                    if(t1 == 16.0f/9.0f)
                        mPreviewSize = new Size(1920, 1080);
                    if(t1 == 1.0f)
                        mPreviewSize = new Size(1080, 1080);
                    if(t1 == 18.0f/9.0f)
                        mPreviewSize = new Size(1440, 720);

                }

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                timelapseConfiguration.grid = (CameraGrid) getActivity().findViewById(R.id.grid);

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                boolean eGrid = sharedPreferences.getBoolean("cameraGrid", false);

                if(eGrid)
                    timelapseConfiguration.grid.setAlpha(1.0f);
                else
                    timelapseConfiguration.grid.setAlpha(0.0f);

                timelapseConfiguration.grid.getLayoutParams().width = (mPreviewSize.getWidth()*mTextureView.mH)/mPreviewSize.getHeight();
                timelapseConfiguration.grid.getLayoutParams().height = mTextureView.mH;
                timelapseConfiguration.grid.requestLayout();

                mCameraId = cameraId;


                return;
            }


        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    public void changeCameraResolution(){

        updateTextInfo();

       // mImageReader.close();
        //mImageReader = ImageReader.newInstance(timelapseConfiguration.ChosenRes.getWidth(), timelapseConfiguration.ChosenRes.getHeight(),
          //      ImageFormat.JPEG, /*maxImages*/2);
        //mImageReader.setOnImageAvailableListener(
          //      mOnImageAvailableListener, mBackgroundHandler);


        mPreviewSize = timelapseConfiguration.ChosenRes;

        if(timelapseConfiguration.ChosenRes.getWidth() > MAX_PREVIEW_WIDTH)
        {
            float t1 = (float)timelapseConfiguration.ChosenRes.getWidth()/(float)timelapseConfiguration.ChosenRes.getHeight();

            if(t1 == 4.0f/3.0f)
                mPreviewSize = new Size(1440, 1080);
            if(t1 == 16.0f/9.0f)
                mPreviewSize = new Size(1920, 1080);
            if(t1 == 1.0f)
                mPreviewSize = new Size(1080, 1080);
            if(t1 == 18.0f/9.0f)
                mPreviewSize = new Size(1440, 720);

        }

        mTextureView.setAspectRatio(
                mPreviewSize.getWidth(), mPreviewSize.getHeight());

        //configureTransform(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        timelapseConfiguration.grid.getLayoutParams().width = (mPreviewSize.getWidth()*timelapseConfiguration.grid.getLayoutParams().height)/mPreviewSize.getHeight();
        timelapseConfiguration.grid.requestLayout();

        mPreviewRequestBuilder.removeTarget(surface);

        surface = new Surface(texture);
        try {
        mPreviewRequestBuilder.addTarget(surface);

        mCaptureSession.abortCaptures();


    mCameraDevice.createCaptureSession(Arrays.asList(surface),
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }

                    // When the session is ready, we start displaying the preview.
                    mCaptureSession = cameraCaptureSession;
                    try {
                        // Auto focus should be continuous for camera preview.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        Resetcamerachanges();

                        // Finally, we start displaying the camera preview.
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                mCaptureCallback, mBackgroundHandler);


                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(
                        @NonNull CameraCaptureSession cameraCaptureSession) {
                    showToast("Failed");
                }
            }, null
    );
}catch (CameraAccessException e){
    e.printStackTrace();

}
    }

    /**
     * Opens the camera specified by {@link BaseFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
           // if (null != mImageReader) {
             //   mImageReader.close();
               // mImageReader = null;
            //}
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
         mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;

                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.

                                Resetcamerachanges();

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);



                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try
        {
            if(timelapseConfiguration.autoFocus) {
                // This is how to tell the camera to lock focus.
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START);
                // Tell #mCaptureCallback to wait for the lock.
                mState = STATE_WAITING_LOCK;
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
            }
            else
                runPrecaptureSequence();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            if(timelapseConfiguration.autoiso) {
                // This is how to tell the camera to trigger.
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                // Tell #mCaptureCallback to wait for the precapture sequence to be set.
                mState = STATE_WAITING_PRECAPTURE;
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
            }
            else{
               // captureStillPicture();
                startShooting();
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {

        if(!timelapseConfiguration.isRecording)
            return;

        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.addTarget(timelapseConfiguration.recordingSurface);
            captureBuilder.addTarget(surface);
            // Use the same AE and AF modes as the preview.
            if(timelapseConfiguration.autoFocus)
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            else{

                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, timelapseConfiguration.focusDistance);

            }

            if(!timelapseConfiguration.autoiso){

                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, timelapseConfiguration.currentiso);
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, timelapseConfiguration.shutterSpeed);
            }
            if(!timelapseConfiguration.autoWb){

                captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                captureBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, computeTemperature(timelapseConfiguration.wb));

            }

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            //mCaptureSession.stopRepeating();
            //mCaptureSession.abortCaptures();

            mCaptureSession.capture(captureBuilder.build(), timelapseConfiguration.captureCallback, mBackgroundHandler);

            Log.d(TAG, "Captured");

            // timelapseConfiguration.isDone = false;
            //mCaptureSession.setRepeatingRequest(captureBuilder.build(), CaptureCallback, null);

            //timelapseConfiguration.altExposureTimer.start();

        } catch (CameraAccessException e) {
            Log.d(TAG, "S000");
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
           /* mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);*/
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecording(){

        timelapseConfiguration.isRecording = true;
        ImageButton recordButton = (ImageButton) timelapseConfiguration.recordView.findViewById(R.id.picture);
        recordButton.setImageResource(R.drawable.ic_stop1);
        final TextView elapsedTime = (TextView) getActivity().findViewById(R.id.line1);
        final TextView line2 = (TextView) getActivity().findViewById(R.id.line2);
        final TextView line3 = (TextView) getActivity().findViewById(R.id.line3);
        line2.setText("");
        line3.setText("");

        double captureRate;

        SimpleDateFormat mdformat = new SimpleDateFormat("YYMMdd_HHmmss");

        timelapseConfiguration.enc = new MediaRecorder();

        String name =  mdformat.format(Calendar.getInstance().getTime()) + ".mp4";
        mFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Lapse", name);



        if(!timelapseConfiguration.autoShutter)
            captureRate = 1.0/(((double)timeToMillis() + (timelapseConfiguration.shutterSpeed / 1000000.0) )/1000.0);
        else
            captureRate = 1.0/((double) timeToMillis()/1000.0);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String fps = sharedPreferences.getString("fps", "30");
        final int framerate;

        if(fps.equals("30"))
            framerate = 30;
        else
            framerate = 60;

        int bitrate;

        if(timelapseConfiguration.customBitrate)
            bitrate = timelapseConfiguration.actualBitrate*1000;
        else
            bitrate = timelapseConfiguration.bitrates[timelapseConfiguration.bitrate];

        if(bitrate < 0)
            bitrate = timelapseConfiguration.bitrates[timelapseConfiguration.bitrate];

        timelapseConfiguration.recordingSurface = MediaCodec.createPersistentInputSurface();
        timelapseConfiguration.enc.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        timelapseConfiguration.enc.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        timelapseConfiguration.enc.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        timelapseConfiguration.enc.setVideoSize(timelapseConfiguration.ChosenRes.getWidth(), timelapseConfiguration.ChosenRes.getHeight());
        timelapseConfiguration.enc.setVideoFrameRate(framerate);
        timelapseConfiguration.enc.setOutputFile(mFile.getAbsolutePath());
        timelapseConfiguration.enc.setVideoEncodingBitRate(bitrate);
        timelapseConfiguration.enc.setCaptureRate(captureRate);
        timelapseConfiguration.enc.setInputSurface(timelapseConfiguration.recordingSurface);

        try {
            timelapseConfiguration.enc.prepare();
        }catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Preparation Error");
        }
        Log.d(TAG, "Prepared");
        final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

            timelapseConfiguration.elapsedTimer = new Timer();
            final TimerTask mTt1 = new TimerTask() {
                public void run() {
                    mTimerHandler.post(new Runnable() {
                        public void run(){
                            updateElapsedTime(elapsedTime, line2, format, framerate);
                            if(!timelapseConfiguration.infiniteDuration && timelapseConfiguration.hourDuration == timelapseConfiguration.eHou && timelapseConfiguration.minuteDuration == timelapseConfiguration.eMin)
                                stopRecording();
                        }
                    });
                }
            };


        try {
            mCameraDevice.createCaptureSession(Arrays.asList(timelapseConfiguration.recordingSurface, surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {

                    Log.d(TAG, "Configured");
                    mCaptureSession = session;
                    timelapseConfiguration.elapsedTimer .schedule(mTt1, 0, 1000);
                    HideUnavailableSettings();
                    showToast(mFile.toString());
                    startShooting();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            showToast("CaptureSession Failed");
                }
            }, null);

        }
        catch (CameraAccessException e){
            e.printStackTrace();
        }



    }

    private void startShooting(){

      //  timelapseConfiguration.shootingThread = new Thread("Shooting") {
       //     public void run(){
                    //timelapseConfiguration.enc.start();
        Log.d(TAG, "Shooting started");
                   timelapseConfiguration.captureCallback
                            = new CameraCaptureSession.CaptureCallback() {

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            Log.d(TAG, mFile.toString());
                            unlockFocus();
                            if(timelapseConfiguration.isRecording){

                                try {
                                    if(timelapseConfiguration.justStarted){

                                        timelapseConfiguration.justStarted = false;
                                        sleep((long)(timeToMillis()*0.3));
                                        timelapseConfiguration.enc.start();
                                        Log.d(TAG, "Started");
                                        sleep((long)(timeToMillis()*0.7));

                                    }
                                    else
                                        sleep(timeToMillis());

                                    captureStillPicture();
                                    timelapseConfiguration.capturedFrames += 1;
                                } catch (InterruptedException e){

                                    e.printStackTrace();

                                }
                            }

                        }
                    };

                   captureStillPicture();
                   Log.d(TAG, "First Capture");

                   Thread sleeper = new Thread("sleepy"){

                       @Override
                       public void run() {
                           SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                           boolean sleep = sharedPreferences.getBoolean("vitalinfo", false);

                           if(sleep){
                               if(timelapseConfiguration.sleepDialog == null)
                                   timelapseConfiguration.sleepDialog = new SleepDialog();
                               timelapseConfiguration.sleepDialog.recording = true;

                               try {
                                   showToast("Starting Sleep mode in 5 seconds");
                                   sleep(5000);
                               }catch (InterruptedException e){
                                   e.printStackTrace();
                               }
                               FragmentTransaction ft = getFragmentManager().beginTransaction();
                              timelapseConfiguration.sleepDialog.show(ft, "Rightlapse:SleepingBeauty");

                           }
                       }
                   };

                    sleeper.start();

                    //stopRecording();
                    //unlockFocus();


        //    }
      //  };
       // timelapseConfiguration.shootingThread.start();
        //timelapseConfiguration.shootingThread.start();
       // timelapseConfiguration.shootingHandler = new Handler(timelapseConfiguration.shootingThread.getLooper());

    }

    private void stopRecording(){

        timelapseConfiguration.isRecording = false;
        timelapseConfiguration.elapsedTimer.cancel();
        if(timelapseConfiguration.sleepDialog != null)
            timelapseConfiguration.sleepDialog.recording = false;
        timelapseConfiguration.eHou = 0;
        timelapseConfiguration.eMin = 0;
        timelapseConfiguration.eSec = 0;
        ImageButton recordButton = (ImageButton) timelapseConfiguration.recordView.findViewById(R.id.picture);
        recordButton.setImageResource(R.drawable.ic_record);
        updateTextInfo();
        ShowUnavailableSettings();
        timelapseConfiguration.enc.stop();
        timelapseConfiguration.enc.reset();
        timelapseConfiguration.enc.release();
        timelapseConfiguration.recordingSurface.release();
        timelapseConfiguration.justStarted = true;
        timelapseConfiguration.capturedFrames = 0;

    }

    public long timeToMillis(){

        return (timelapseConfiguration.msInterval*100) + (timelapseConfiguration.secondsInterval*1000) + (timelapseConfiguration.minutesInterval*60000);

    }

    private void ShowUnavailableSettings(){

        ImageButton ratioButton = (ImageButton) getActivity().findViewById(R.id.aspect_ratio);
        ratioButton.setAlpha(1f);
        ratioButton.setClickable(true);

        ImageButton qualityButton = (ImageButton) getActivity().findViewById(R.id.quality);
        qualityButton.setAlpha(1f);
        qualityButton.setClickable(true);

        ImageButton intervalButton = (ImageButton) getActivity().findViewById(R.id.frame_interval);
        intervalButton.setAlpha(1f);
        intervalButton.setClickable(true);

        ImageButton settingsButton = (ImageButton) getActivity().findViewById(R.id.info);
        settingsButton.setAlpha(1f);
        settingsButton.setClickable(true);

    }
    private void HideUnavailableSettings(){

        ImageButton ratioButton = (ImageButton) getActivity().findViewById(R.id.aspect_ratio);
        ratioButton.setAlpha(0.5f);
        ratioButton.setClickable(false);

        ImageButton qualityButton = (ImageButton) getActivity().findViewById(R.id.quality);
        qualityButton.setAlpha(0.5f);
        qualityButton.setClickable(false);

        ImageButton intervalButton = (ImageButton) getActivity().findViewById(R.id.frame_interval);
        intervalButton.setAlpha(0.5f);
        intervalButton.setClickable(false);

        ImageButton settingsButton = (ImageButton) getActivity().findViewById(R.id.info);
        settingsButton.setAlpha(0.5f);
        settingsButton.setClickable(false);

    }

    private void updateElapsedTime(TextView textView, TextView line2, SimpleDateFormat format, int fps){

        if(timelapseConfiguration.eSec == 59){

            if(timelapseConfiguration.eMin == 59) {
                timelapseConfiguration.eHou += 1;
                timelapseConfiguration.eMin = 0;
                timelapseConfiguration.eSec = 0;
            }
            else {
                timelapseConfiguration.eMin += 1;
                timelapseConfiguration.eSec = 0;
            }
        }
        else
            timelapseConfiguration.eSec += 1;

        String s, m, h;
        s = String.valueOf(timelapseConfiguration.eSec);
        m = String.valueOf(timelapseConfiguration.eMin);
        h = String.valueOf(timelapseConfiguration.eHou);

        if(s.length() == 1)
            s = "0" + s;
        if(m.length() == 1)
            m = "0" + m;
        if(h.length() == 1)
            h = "0" + h;

        textView.setText(h + ":" + m + ":" + s);


        int secPlayback = timelapseConfiguration.capturedFrames /fps;
        int hours = secPlayback / 3600;
        int minutes = (secPlayback % 3600) / 60;
        int seconds = secPlayback % 60;

        String s1, m1, h1;
        s1 = String.valueOf(seconds);
        m1 = String.valueOf(minutes);
        h1 = String.valueOf(hours);


        if(s1.length() == 1)
            s1 = "0" + s1;
        if(m1.length() == 1)
            m1 = "0" + m1;
        if(h1.length() == 1)
            h1 = "0" + h1;



        line2.setText("Playback duration: " + h1 + ":" + m1 + ":" + s1);

        if( timelapseConfiguration.sleepDialog != null && timelapseConfiguration.sleepDialog.line3 != null){

            timelapseConfiguration.sleepDialog.line2.setText("Recording time: " + h + ":" + m + ":" + s);

            timelapseConfiguration.sleepDialog.line1.setText("Time: " + format.format(Calendar.getInstance().getTime()));

        }

    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.picture: {
                //takePicture();
                timelapseConfiguration.recordView = view;
                if(!timelapseConfiguration.isRecording)
                    startRecording();
                else {
                    stopRecording();
                }
                break;
            }

            case R.id.info: {

                Intent intent = new Intent(getContext(), SettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(intent);
                break;
            }

            case R.id.video_length:{

                if(timelapseConfiguration.durationDialog == null) {
                    Activity activity = getActivity();
                    AlertDialog.Builder selectDialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog);

                    selectDialog.setTitle("Select video length:");



                    final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.select_dialog_item);

                    arrayAdapter.add("Infinite");
                    arrayAdapter.add("Customized");

                    final BaseFragment bs = this;

                    selectDialog.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String selected = arrayAdapter.getItem(which);

                            if (selected == "Customized") {

                                if(timelapseConfiguration.pickerDialog == null) {
                                    timelapseConfiguration.pickerDialog = new TimePickerFragment();
                                    ((TimePickerFragment) timelapseConfiguration.pickerDialog).setLapse(timelapseConfiguration, bs);

                                    timelapseConfiguration.pickerDialog.show(getFragmentManager(), "timePicker");
                                }
                                else
                                    timelapseConfiguration.pickerDialog.show(getFragmentManager(), "timePicker");


                            }
                            if (selected == "Infinite") {
                                timelapseConfiguration.infiniteDuration = true;
                                updateTextInfo();
                            }
                        }
                    });
                    timelapseConfiguration.durationDialog = selectDialog.show();

                }
                timelapseConfiguration.durationDialog.show();

                break;
            }

            case R.id.frame_interval:{

                if(timelapseConfiguration.intervalDialog == null) {
                    Activity activity = getActivity();

                    AlertDialog.Builder selectDialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog);
                    LayoutInflater inflater = activity.getLayoutInflater();
                    selectDialog.setTitle("Select frame interval: mm:ss.ds");
                    View dialogView = inflater.inflate(R.layout.interval_dialog, null);
                    selectDialog.setView(dialogView);

                    final NumberPicker secPicker = (NumberPicker) dialogView.findViewById(R.id.secPick);
                    secPicker.setMinValue(0);
                    secPicker.setMaxValue(60);
                    secPicker.setValue(1);

                    final NumberPicker minPicker = (NumberPicker) dialogView.findViewById(R.id.minPick);
                    minPicker.setMinValue(0);
                    minPicker.setMaxValue(60);
                    minPicker.setValue(0);

                    final NumberPicker msPicker = (NumberPicker) dialogView.findViewById(R.id.millisecPick);
                    msPicker.setMinValue(0);
                    msPicker.setMaxValue(9);
                    msPicker.setValue(0);

                    selectDialog.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            if (secPicker.getValue() != 0 || minPicker.getValue() != 0 || msPicker.getValue() != 0) {

                                timelapseConfiguration.minutesInterval = minPicker.getValue();
                                timelapseConfiguration.secondsInterval = secPicker.getValue();
                                timelapseConfiguration.msInterval = msPicker.getValue();
                                updateTextInfo();
                            } else {

                                showToast("Invalid configuration: frame interval can't be 0");
                            }


                        }
                    });
                    selectDialog.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });

                    timelapseConfiguration.intervalDialog = selectDialog.show();
                }
                else
                    timelapseConfiguration.intervalDialog.show();

                break;
            }

            case R.id.cameraFocus:{

                if(timelapseConfiguration.focusDialog == null) {

                    if (!timelapseConfiguration.focusControl) {
                        showToast("Manual focus is not available on your device");
                        break;
                    }

                    timelapseConfiguration.focusDialog = new FocusDialog(getContext(), timelapseConfiguration, this);
                    timelapseConfiguration.focusDialog.show();

                }
                else
                    timelapseConfiguration.focusDialog.show();

                break;
            }

            case R.id.cameraIso:{

                if(timelapseConfiguration.isoDialog == null) {

                    if (!timelapseConfiguration.isoControl) {
                        showToast("Manual ISO is not available on your device");
                        break;
                    }

                    timelapseConfiguration.isoDialog = new IsoDialog(getContext(), timelapseConfiguration, this);
                    timelapseConfiguration.isoDialog.show();

                }
                else
                    timelapseConfiguration.isoDialog.show();

                break;
            }

            case R.id.cameraShutter:{

                if(timelapseConfiguration.shutterDialog == null) {

                    if (!timelapseConfiguration.shutterControl) {

                        showToast("Manual shutter speed is not available on your device");
                        break;

                    }
                    timelapseConfiguration.shutterDialog = new ShutterDialog(getContext(), timelapseConfiguration, this);
                    timelapseConfiguration.shutterDialog.show();
                }
                else
                    timelapseConfiguration.shutterDialog.show();


                break;
            }

            case R.id.cameraWb:{

                if(timelapseConfiguration.wbDialog == null) {

                    if (!timelapseConfiguration.wbcontrol) {

                        showToast("Manual white balance is not available on your device");
                        break;

                    }

                    timelapseConfiguration.wbDialog = new WbDialog(getContext(), timelapseConfiguration, this);
                    timelapseConfiguration.wbDialog.show();

                }
                else
                    timelapseConfiguration.wbDialog.show();

                break;
            }

            case R.id.quality:{

                if(timelapseConfiguration.resDialog == null) {

                    Activity activity = getActivity();
                    final AlertDialog.Builder selectDialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog);

                    selectDialog.setTitle("Select video quality:");
                    LayoutInflater inflater = activity.getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.quality_dialog, null);

                    ListView listView = (ListView)dialogView.findViewById(R.id.qualityView);
                    final EditText bitText = (EditText) dialogView.findViewById(R.id.bitrate);

                    bitText.setText(String.valueOf(timelapseConfiguration.bitrates[timelapseConfiguration.bitrate]/1000));


                    selectDialog.setView(dialogView);

                    final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.select_dialog_item);


                    for (int i = 0; i < 4; i++) {

                        if (timelapseConfiguration.resolutions.get(i).size() > 0) {

                            switch (i) {

                                case 0: {
                                    arrayAdapter.add("4K");
                                    break;
                                }
                                case 1: {
                                    arrayAdapter.add("Full HD");
                                    break;
                                }
                                case 2: {
                                    arrayAdapter.add("HD");
                                    break;
                                }
                                case 3: {
                                    arrayAdapter.add("480p");
                                    break;
                                }
                            }


                        }

                    }

                    listView.setAdapter(arrayAdapter);
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            String selected = (String)parent.getItemAtPosition(position);

                            if (selected == "480p") {

                                timelapseConfiguration.bitrate = 0;
                                String rate = String.valueOf(timelapseConfiguration.bitrates[timelapseConfiguration.bitrate]/1000);
                                bitText.setText(rate);
                                timelapseConfiguration.customBitrate = false;
                                if (timelapseConfiguration.ChosenRes.getHeight() == timelapseConfiguration.resolutions.get(3).get(0).getHeight())
                                    return;

                                timelapseConfiguration.ChosenRes = timelapseConfiguration.resolutions.get(3).get(0);


                            }
                            if (selected == "HD") {


                                timelapseConfiguration.bitrate = 1;
                                String rate = String.valueOf(timelapseConfiguration.bitrates[timelapseConfiguration.bitrate]/1000);
                                bitText.setText(rate);
                                timelapseConfiguration.customBitrate = false;
                                if (timelapseConfiguration.ChosenRes.getHeight() == timelapseConfiguration.resolutions.get(2).get(0).getHeight())
                                    return;

                                timelapseConfiguration.ChosenRes = timelapseConfiguration.resolutions.get(2).get(0);

                            }
                            if (selected == "Full HD") {

                                timelapseConfiguration.bitrate = 2;
                                String rate = String.valueOf(timelapseConfiguration.bitrates[timelapseConfiguration.bitrate]/1000);
                                bitText.setText(rate);
                                timelapseConfiguration.customBitrate = false;
                                if (timelapseConfiguration.ChosenRes.getHeight() == timelapseConfiguration.resolutions.get(1).get(0).getHeight())
                                    return;

                                timelapseConfiguration.ChosenRes = timelapseConfiguration.resolutions.get(1).get(0);



                            }
                            if (selected == "4K") {

                                timelapseConfiguration.bitrate = 3;
                                String rate = String.valueOf(timelapseConfiguration.bitrates[timelapseConfiguration.bitrate]/1000);
                                bitText.setText(rate);
                                timelapseConfiguration.customBitrate = false;
                                if (timelapseConfiguration.ChosenRes.getHeight() == timelapseConfiguration.resolutions.get(0).get(0).getHeight())
                                    return;

                                timelapseConfiguration.ChosenRes = timelapseConfiguration.resolutions.get(0).get(0);

                            }



                            changeCameraResolution();
                        }
                    });

                    bitText.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {



                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            timelapseConfiguration.customBitrate = true;
                        }
                    });

                  selectDialog.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int which) {

                          if(timelapseConfiguration.customBitrate) {

                              if(bitText.getText().toString().equals("")){

                                  timelapseConfiguration.customBitrate = false;
                                  bitText.setText(String.valueOf(timelapseConfiguration.bitrates[timelapseConfiguration.bitrate]/1000));
                              }
                              else {
                                  Integer c = Integer.parseInt(bitText.getText().toString());
                                  timelapseConfiguration.actualBitrate = c;
                              }
                          }

                      }
                  });


                  timelapseConfiguration.resDialog =  selectDialog.show();


                }
                else
                    timelapseConfiguration.resDialog.show();

                break;
            }

            case R.id.aspect_ratio:{
                Activity activity = getActivity();
                AlertDialog.Builder selectDialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog);

                selectDialog.setTitle("Select video aspect ratio:");

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.select_dialog_item);

                if (timelapseConfiguration.ChosenRes.getHeight() == 480) {

                        for (Size size : timelapseConfiguration.ratios.get(3))

                            arrayAdapter.add(size.getWidth() + ":" + size.getHeight());

                    } else if (timelapseConfiguration.ChosenRes.getHeight() == 720) {

                        for (Size size : timelapseConfiguration.ratios.get(2))

                            arrayAdapter.add(size.getWidth() + ":" + size.getHeight());
                    } else if (timelapseConfiguration.ChosenRes.getHeight() == 1080) {

                        for (Size size : timelapseConfiguration.ratios.get(1))

                            arrayAdapter.add(size.getWidth() + ":" + size.getHeight());

                    } else if (timelapseConfiguration.ChosenRes.getHeight() == 2160) {

                        for (Size size : timelapseConfiguration.ratios.get(0))

                            arrayAdapter.add(size.getWidth() + ":" + size.getHeight());

                    }

                    selectDialog.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String selected = arrayAdapter.getItem(which);
                            if (selected.matches("4:3")) {

                                Size target = new Size(((timelapseConfiguration.ChosenRes.getHeight() * 4) / 3), timelapseConfiguration.ChosenRes.getHeight());

                                if (target.getWidth() != timelapseConfiguration.ChosenRes.getWidth()) {

                                    timelapseConfiguration.ChosenRes = target;
                                    changeCameraResolution();
                                }

                            } else if (selected.matches("16:9")) {

                                Size target = new Size(((timelapseConfiguration.ChosenRes.getHeight() * 16) / 9), timelapseConfiguration.ChosenRes.getHeight());

                                if (target.getWidth() != timelapseConfiguration.ChosenRes.getWidth()) {

                                    timelapseConfiguration.ChosenRes = target;
                                    // closeCamera();
                                    //openCamera(initS.getWidth(),initS.getHeight());
                                    changeCameraResolution();
                                }

                            }
                            else if (selected.matches("1:1")) {

                                Size target = new Size(((timelapseConfiguration.ChosenRes.getHeight() * 1) / 1), timelapseConfiguration.ChosenRes.getHeight());

                                if (target.getWidth() != timelapseConfiguration.ChosenRes.getWidth()) {

                                    timelapseConfiguration.ChosenRes = target;
                                    changeCameraResolution();
                                }

                            } else if (selected.matches("18:9")) {

                                Size target = new Size(((timelapseConfiguration.ChosenRes.getHeight() * 18) / 9), timelapseConfiguration.ChosenRes.getHeight());

                                if (target.getWidth() != timelapseConfiguration.ChosenRes.getWidth()) {

                                    timelapseConfiguration.ChosenRes = target;
                                    changeCameraResolution();
                                }
                            }


                        }
                    });


                   selectDialog.show();




                break;
            }

        }
    }

    public void updateTextInfo(){

        TextView line1 = getActivity().findViewById(R.id.line1);
        TextView line2 = getActivity().findViewById(R.id.line2);
        TextView line3 = getActivity().findViewById(R.id.line3);

        if(timelapseConfiguration.infiniteDuration)
            line1.setText("Recording time: Unlimited");
        else
            line1.setText("Recording time: "+timelapseConfiguration.hourDuration + " hours " + timelapseConfiguration.minuteDuration + " minutes");

        String s, m;
        s = String.valueOf(timelapseConfiguration.secondsInterval);
        m = String.valueOf(timelapseConfiguration.minutesInterval);

        if(s.length() == 1)
            s = "0" + s;
        if(m.length() == 1)
            m = "0" + m;

        line2.setText("Frame interval: " + m + ":" + s + "." + timelapseConfiguration.msInterval);

        line3.setText("Resolution: " + timelapseConfiguration.ChosenRes.toString());
    }

    private void InitializeQuality(StreamConfigurationMap map){

        for (int i = 0; i < 4; i++) {

            timelapseConfiguration.resolutions.add(new ArrayList<Size>());
            timelapseConfiguration.ratios.add(new ArrayList<Size>());

        }

        for (Size cSize : map.getOutputSizes(ImageFormat.JPEG)) {

            if (cSize.getHeight() == 1080)
                timelapseConfiguration.resolutions.get(1).add(cSize);
            if (cSize.getHeight() == 720)
                timelapseConfiguration.resolutions.get(2).add(cSize);
            if (cSize.getHeight() == 480)
                timelapseConfiguration.resolutions.get(3).add(cSize);
            if (cSize.getHeight() == 2160)
                timelapseConfiguration.resolutions.get(0).add(cSize);

        }

        List<Size> toRemove = new ArrayList<Size>();

        for (int i = 0; i < 4; i++) {

            for (Size cSize : timelapseConfiguration.resolutions.get(i)) {

                if ((float) ((float) cSize.getWidth() / (float) cSize.getHeight()) == (float) (16.0f / 9.0f)) {

                    timelapseConfiguration.ratios.get(i).add(new Size(16, 9));
                    continue;

                }

                if ((float) ((float) cSize.getWidth() / (float) cSize.getHeight()) == (float) (4.0f / 3.0f)) {

                    timelapseConfiguration.ratios.get(i).add(new Size(4, 3));
                    continue;

                }

                if ((float) ((float) cSize.getWidth() / (float) cSize.getHeight()) == (float) (18.0f / 9.0f)) {

                    timelapseConfiguration.ratios.get(i).add(new Size(18, 9));
                    continue;

                }

                if ((float) ((float) cSize.getWidth() / (float) cSize.getHeight()) == (float) (1 / 1)) {

                    timelapseConfiguration.ratios.get(i).add(new Size(1, 1));
                    continue;

                }

                toRemove.add(cSize);

            }

        }

        //Remove not allowed resolutions

        for (List<Size> l1 : timelapseConfiguration.resolutions) {

            for (Size toR : toRemove)
                l1.remove(toR);

        }

        int lowestNotEmpty = 3;

        for (int i = 3; i > 0; i++) {

            if (timelapseConfiguration.resolutions.get(i).size() > 0) {

                lowestNotEmpty = i;
                break;

            }

        }

        timelapseConfiguration.ChosenRes = timelapseConfiguration.resolutions.get(lowestNotEmpty).get(0);

        TextView line3 = getActivity().findViewById(R.id.line3);
        line3.setText("Resolution: " + timelapseConfiguration.ChosenRes.toString());

        if(timelapseConfiguration.ChosenRes.getHeight() == 480)
            timelapseConfiguration.bitrate = 0;
        else if(timelapseConfiguration.ChosenRes.getHeight() == 720)
            timelapseConfiguration.bitrate = 1;
        else if(timelapseConfiguration.ChosenRes.getHeight() == 1080)
            timelapseConfiguration.bitrate = 2;
        else if (timelapseConfiguration.ChosenRes.getHeight() == 2160)
            timelapseConfiguration.bitrate = 3;


    }

    private void setupCameraFeatures(CameraCharacteristics camCha, final TimelapseConfiguration timelapseConfiguration){


        int[] camCapabilities = camCha.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean postProcess = false;
        for(int mode : camCapabilities)
        {
            if(mode == CaptureRequest.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
                postProcess = true;

        }

        Range<Long> shutter  = camCha.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE); //CONTROL_AE_MODE needs turn off
        if(shutter != null){
            timelapseConfiguration.shutterControl = true;
            timelapseConfiguration.shutterRange = shutter;
        }
        Range<Integer> iso = camCha.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if(iso != null){
            timelapseConfiguration.isoControl = true;
            timelapseConfiguration.isoRange = iso;
        }

        int[] wbOptions = camCha.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);

        for(int mode : wbOptions){

            if (mode == CaptureRequest.CONTROL_AWB_MODE_OFF && postProcess){

                timelapseConfiguration.wbcontrol = true;
                break;
            }

        }

        int colorsArr = camCha.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        int sArr = camCha.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);

        int[] focusModes = camCha.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        for(int mode : focusModes){

            if(mode == CaptureRequest.CONTROL_AF_MODE_OFF){

                timelapseConfiguration.focusControl = true;
                timelapseConfiguration.minimumFocus = camCha.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

                Integer calibration = camCha.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION);

                if(calibration == CaptureRequest.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED || calibration == CaptureRequest.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE)
                    timelapseConfiguration.focusMeters = true;

                break;
            }

        }

    }

    public void changeCameraFocus(boolean auto, float focus) {


        if(auto == true && timelapseConfiguration.autoFocus == true){
            return;

        }

        else if(auto == true && timelapseConfiguration.autoFocus == false){

            timelapseConfiguration.autoFocus = true;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequest = mPreviewRequestBuilder.build();


        }

        else if(auto == false && timelapseConfiguration.autoFocus == true){

            timelapseConfiguration.autoFocus = false;
            timelapseConfiguration.focusDistance = focus;

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            mPreviewRequest = mPreviewRequestBuilder.build();

        }

        else if(auto == false && timelapseConfiguration.autoFocus == false){

            mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            mPreviewRequest = mPreviewRequestBuilder.build();
            timelapseConfiguration.focusDistance = focus;

        }

        try {
            if(!timelapseConfiguration.isRecording) {
                mCaptureSession.stopRepeating();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mBackgroundHandler);
            }
        }catch (CameraAccessException e){

            e.printStackTrace();
        }

    }

    public void changeCameraIso(boolean auto, int iso){

        if(auto == true && timelapseConfiguration.autoiso == true){
            return;

        }

        if(auto == true && timelapseConfiguration.autoiso == false){

            timelapseConfiguration.autoiso = true;
            timelapseConfiguration.autoShutter = true;
            if(timelapseConfiguration.shutterCheck != null)
                timelapseConfiguration.shutterCheck.setChecked(true);

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

        }

        if(auto == false && timelapseConfiguration.autoiso == true){

            timelapseConfiguration.autoiso = false;
            timelapseConfiguration.autoShutter = false;
            if(timelapseConfiguration.shutterCheck != null)
                timelapseConfiguration.shutterCheck.setChecked(false);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, timelapseConfiguration.frameDuration);
           /* if(timelapseConfiguration.shutterSpeed < 33333335L)

                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, timelapseConfiguration.shutterSpeed);
            else
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33333335L);*/

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, timelapseConfiguration.lastSpeed);
            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);

        }

        if(auto == false && timelapseConfiguration.autoiso == false){

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
            timelapseConfiguration.currentiso = iso;

        }

        mPreviewRequest = mPreviewRequestBuilder.build();

        try {
            if(!timelapseConfiguration.isRecording) {
                mCaptureSession.stopRepeating();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mBackgroundHandler);
            }
        }catch (CameraAccessException e){

            e.printStackTrace();
        }

    }

    public void changeCameraWb(boolean auto, int temp){

        if(auto == true && timelapseConfiguration.autoWb == false){

            timelapseConfiguration.autoWb = true;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

        }
        if(auto == false && timelapseConfiguration.autoWb == true){

            timelapseConfiguration.autoWb = false;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, computeTemperature(temp));
            timelapseConfiguration.wb = temp;


        }
        if(auto == false && timelapseConfiguration.autoWb == false){

            mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, computeTemperature(temp));
            timelapseConfiguration.wb = temp;
        }

        mPreviewRequest = mPreviewRequestBuilder.build();

        try {
            if(!timelapseConfiguration.isRecording) {
                mCaptureSession.stopRepeating();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mBackgroundHandler);
            }
        }catch (CameraAccessException e){

            e.printStackTrace();
        }

    }

    public void changeCameraShutter(boolean auto, long speed){

        if(auto == true && timelapseConfiguration.autoiso == false){

            timelapseConfiguration.autoiso = true;
            timelapseConfiguration.autoShutter = true;
            if(timelapseConfiguration.autoisoCheck != null)
                timelapseConfiguration.autoisoCheck.setChecked(true);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

        }

        if(auto == false && timelapseConfiguration.autoiso == true){

            timelapseConfiguration.autoiso = false;
            timelapseConfiguration.autoShutter = false;
            if(timelapseConfiguration.autoisoCheck != null)
                timelapseConfiguration.autoisoCheck.setChecked(false);
            timelapseConfiguration.shutterSpeed = speed;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, timelapseConfiguration.frameDuration);
            if(speed < 33333335L)

                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, speed);
            else
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33333335L);
            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, timelapseConfiguration.currentiso);

        }

        if(auto == false && timelapseConfiguration.autoShutter == false){

            timelapseConfiguration.shutterSpeed = speed;

            if(speed < 166666668L)

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, speed);
            else
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 166666668L);

        }

        mPreviewRequest = mPreviewRequestBuilder.build();

        try {
            if(!timelapseConfiguration.isRecording) {
                mCaptureSession.stopRepeating();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mBackgroundHandler);
            }
        }catch (CameraAccessException e){

            e.printStackTrace();
        }

    }

    public void Resetcamerachanges(){

        if(!timelapseConfiguration.autoFocus){

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, timelapseConfiguration.focusDistance);

        }

        if(!timelapseConfiguration.autoiso || !timelapseConfiguration.autoShutter){

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, timelapseConfiguration.frameDuration);
            if(timelapseConfiguration.shutterSpeed < 33333335L)
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, timelapseConfiguration.shutterSpeed);
            else
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33333335L);

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, timelapseConfiguration.currentiso);

        }

        if(!timelapseConfiguration.autoWb){

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, computeTemperature(timelapseConfiguration.wb));

        }
    }

    private static RggbChannelVector computeTemperature(final int factor)
    {
        return new RggbChannelVector(0.635f + (0.0208333f * factor), 1.0f, 1.0f, 3.7420394f + (-0.0287829f * factor));
    }

    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;


        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;

        }

        @Override
        public void run() {



            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
           FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
  /*  static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }*/

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if(key.equals("cameraGrid")){

                if(sharedPreferences.getBoolean("cameraGrid", false))
                    timelapseConfiguration.grid.setAlpha(1.0f);
                else
                    timelapseConfiguration.grid.setAlpha(0.0f);

        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "Permissions not granted.";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })
                    .create();
        }

    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        TimelapseConfiguration conf;
        BaseFragment baseFragment;

        public void setLapse(TimelapseConfiguration config, BaseFragment bf) {

            conf = config;
            baseFragment = bf;
        }


        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {


            // Create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(getActivity(), this, 0, 10,
                    true);
        }



        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {


            if(hourOfDay == 0 && minute == 0){

                conf.infiniteDuration = true;

                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, "Invalid duration: can't be 0, set to infinite", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                return;
            }

            conf.infiniteDuration = false;
            conf.hourDuration = hourOfDay;
            conf.minuteDuration = minute;

            baseFragment.updateTextInfo();

        }
    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }


    }

    public static class ConfirmationDialogStorage extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission_storage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    2);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }


    }

}
