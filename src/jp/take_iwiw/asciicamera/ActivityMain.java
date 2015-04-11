/**
 * ActivityMain
 * @author take.iwiw
 * @version 1.0.0
 */

package jp.take_iwiw.asciicamera;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import jp.take_iwiw.asciicamera.ViewCameraPreview.GetPreviewCallback;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;


public class ActivityMain extends Activity {
    /* Set onCreate */
    private ActivityMain m_ctx;
    private ViewCameraPreview m_cameraPreview = null;    //!< @brief View for Preview. it contains camera instance
    public ViewCameraOverlay m_facePreview = null;    //!< @brief View for FaceFrame
    private int m_cameraId_BACK = -1;    //!< @brief CameraID. fixed when application starts
    private int m_cameraId_FACE = -1;    //!< @brief CameraID. fixed when application starts

    /* Set onCreateOptionsMenu */
    private MenuItem m_menuFaceBack;
    private MenuItem m_menuRetake;
    private MenuItem m_menuCopy;
    private MenuItem m_menuSave;
    private MenuItem m_menuSetting;
    private MenuItem m_menuInfo;

    private int m_cameraId = -1;    //!< @brief CameraID. changed by user setting.

    /** @brief Application Status */
    enum STATUS {MONITOR, PROCESSING, RESULT, SUSPEND_PROCESSING, SUSPEND_FINISHPROCESS,};
    private STATUS m_status=STATUS.MONITOR;

    /* values about user settings */
    enum CONVERT {GRAYSCALE, EDGE, RAW, };
    public CONVERT m_convert = CONVERT.RAW;    //!< @brief whether convert to ascii or not
    private static final int REQUEST_CODE_PREFERENCES = 1;
    private float m_textSize = 3;    //!< @brief FontSize for result. changed by user setting

    private String m_strResult="";
    private Bitmap m_imageResult;

    @Override
    /**
     * Get CameraInformation(static) and User settings.
     * Set Views.
     * Do not attache the camera here.
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DebugUtility.logDebug("onCreate");
        m_ctx = this;
        setContentView(R.layout.layout_activity_main);

        /**
         * Locate EditText on the ActionBar
         * references: http://www.sanfoundry.com/java-android-program-add-custom-view-actionbar/
         */
        ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.layout_actionbar_edittext);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM
                | ActionBar.DISPLAY_SHOW_HOME);


        /* Get Camera Static Information. it cannot be changed by user. */
        getCameraInfo();

        /* Get User Setting */
        PreferenceManager.setDefaultValues(this, R.xml.settings, true);
        getSetting();

        /* Set Face Frame view (Preview is set later) */
        FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout_main);
        m_facePreview = new ViewCameraOverlay(this);
        frmLayout.addView(m_facePreview);
    }


    @Override
    /**
     * Fix view according to status
     * Open camera.
     */
    protected void onResume() {
        super.onResume();
        DebugUtility.logDebug("onResume " + m_status);

        /* Open Camera, Set Preview View, Start Preview, Set onTouchListener */
        startCamera();

        /*  Fix view according to the last status */
        if(m_status == STATUS.SUSPEND_FINISHPROCESS){
            /* finish converting background, and resume app after suspend */
            setStatus(STATUS.RESULT);
        } else if(m_status == STATUS.SUSPEND_PROCESSING){
            /* still converting, and resume app after suspend */
            setStatus(STATUS.PROCESSING);
        } else {
            setStatus(m_status);
        }



        /* Load seed characters */
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String str = sharedPreferences.getString("STR_ELEMENTS", "");
        if(str.length() != 0){
            EditText editTxtElement = (EditText)findViewById(R.id.editText_elements);
            editTxtElement.setText(str);
        }


    }


    @Override
    protected void onPause() {
        super.onPause();
        DebugUtility.logDebug("onPause " + m_status);

        if(m_status == STATUS.PROCESSING)setStatus(STATUS.SUSPEND_PROCESSING);

        /* Stop Preview, Close Camera, Recover FrameLayout */
        stopCamera();

        /* Store seed characters */
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = sharedPreferences.edit();
        EditText editTxtElement = (EditText)findViewById(R.id.editText_elements);
        editor.putString("STR_ELEMENTS", editTxtElement.getText().toString());
        editor.commit();
    }

    @Override
    protected void onDestroy() {
        // TODO 自動生成されたメソッド・スタブ
        super.onDestroy();
        DebugUtility.logDebug("onDestroy " + m_status);
    }



    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        /* In order to change menu items properties later, save MenuItem when they created */
        /* ToDo: I don't know other ways */
        m_menuFaceBack = menu.findItem(R.id.menu_faceBack);
        m_menuRetake = menu.findItem(R.id.menu_retake);
        m_menuCopy = menu.findItem(R.id.menu_copy);
        m_menuSave = menu.findItem(R.id.menu_save);
        m_menuSetting = menu.findItem(R.id.menu_setting);
        m_menuInfo = menu.findItem(R.id.menu_info);

        if(m_cameraId == m_cameraId_FACE){
            m_menuFaceBack.setTitle(getString(R.string.camera_sel_face));
        } else {
            m_menuFaceBack.setTitle(getString(R.string.camera_sel_back));
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){
        case R.id.menu_faceBack:
            /* Change camera type only when monitoring */
            if(m_status == STATUS.MONITOR){
                if(m_cameraId == m_cameraId_FACE){
                    m_cameraId = m_cameraId_BACK;
                    m_menuFaceBack.setTitle(getString(R.string.camera_sel_back));
                } else {
                    m_cameraId = m_cameraId_FACE;
                    m_menuFaceBack.setTitle(getString(R.string.camera_sel_face));
                }
            } else {
                DebugUtility.logError(" " + m_status);
            }
            /* Restart Camera because camera type has changed */
            stopCamera();
            startCamera();
            break;
        case R.id.menu_retake:
            /* Change status from RESULT to MONITOR */
            setStatus(STATUS.MONITOR);
            break;
        case R.id.menu_copy:
            /* Copy the result text to Clipboard */
            ClipData.Item clipItem;
            /*** Tentative ***/
if(false){
            EditText editTxtResult = (EditText)findViewById(R.id.editText_result);
            clipItem = new ClipData.Item(editTxtResult.getText());
} else {
            clipItem = new ClipData.Item(m_strResult);
}
            String[] mimeType = new String[1];
            mimeType[0] = ClipDescription.MIMETYPE_TEXT_URILIST;
            ClipData cd = new ClipData(new ClipDescription("text_data", mimeType), clipItem);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(cd);
            break;
        case R.id.menu_save:
            /* Save FrameLayout Image as Jpeg in the internal memory */
            String saveDir = Environment.getExternalStorageDirectory().getPath() + "/" + getString(R.string.folder_save);
            File file = new File(saveDir);
            if (!file.exists()) {
                if (!file.mkdir()) {
                    DebugUtility.logError(saveDir.toString());
                }
            }
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String imgPath = saveDir + "/" + sf.format(cal.getTime()) + ".jpg";
            FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout_main);
            frmLayout.setDrawingCacheEnabled(true);
            Bitmap save_bmp = Bitmap.createBitmap(frmLayout.getDrawingCache());
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(imgPath, true);
                save_bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
                registAndroidDB(imgPath); /* Register DB to reflect gallery */
            } catch (Exception e) {
                DebugUtility.logError(e.getMessage());
            }
            fos = null;
            frmLayout.setDrawingCacheEnabled(false);
            break;
        case R.id.menu_setting:
            /* Open Setting activity */
            Intent intent = new Intent(ActivityMain.this, ActivitySettings.class);
            startActivityForResult(intent, REQUEST_CODE_PREFERENCES);
            break;
        case R.id.menu_info:
            /* Show Information Dialog */
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.info_detail))
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            builder.show();

            break;
        default:
            DebugUtility.logError("menuId = " + item.getItemId());
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * In order to "COPY", keep result string
     * caller: PictureProcessTask when finish convert
     * @param str: converted text
     */
    public void setStringResult(String str){
        m_strResult = str;
    }

    /**
     * Set Converted IMAGE
     * Caller PictureProcessTask.onPostExecute
     */
    public void setImageResult(Bitmap bmp){
        m_imageResult = bmp;
    }


    /**
     * @brief Set FrameLayout Size.
     * @param width(px)
     * @param heigh(px)
     * caller: ViewCameraPreview when surfaceChanged or camera type changed.
     * Sizes of camera preview, face frame, and result image will be adjusted automatically.
     * Because they are contained FrameLayout.
     */
    public void setFrameLayoutSize(int width, int height){
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, height);
        FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout_main);
        frmLayout.setLayoutParams(lp);
    }


    void startCamera()
    {
        DebugUtility.logDebug("startCamera");

        /* ViewCameraPreview controls camera and shows preview */
        m_cameraPreview = new ViewCameraPreview(this, m_cameraId);
        FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout_main);
        frmLayout.addView(m_cameraPreview);
        m_facePreview.bringToFront();

        /* Register listener on preview touch */
        /* Touch -> AutoFocus -> Convert -> ShowResult */
        m_cameraPreview.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    DebugUtility.logDebug("onTouch");
                    if (m_status == STATUS.MONITOR) {
                        setStatus(STATUS.PROCESSING);
                        m_cameraPreview.execAutoFocus(autoFocusCallback);
                    }
                }
                return true;
            }
        });
    }

    private void stopCamera(){
        DebugUtility.logDebug("stopCamera");

        FrameLayout frmLayout = (FrameLayout)findViewById(R.id.frameLayout_main);
        frmLayout.removeView(m_cameraPreview);
        m_cameraPreview = null;

        /* In case the sizes of face camera and back camera is totally different, recover frameLayout size */
        /* because preview size is adjusted based on frameLayout size */
        //RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        //frmLayout.setLayoutParams(lp);
    }

    /**
     * After finishing Auto Focus, take one image from preview then start image processing
     */
    private Camera.AutoFocusCallback autoFocusCallback = new AutoFocusCallback() {

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            DebugUtility.logDebug("AutoFocus finish");
            m_cameraPreview.getPreview(new GetPreviewCallback() {
                public void onGetPreview(Bitmap bmp) {
                    m_cameraPreview.stopPreview();
                    PictureProcessTask pictureProcessTask = new PictureProcessTask(m_ctx, m_textSize);
                    if(DebugUtility.DEMO == true){
                        Bitmap bmpDummy = BitmapFactory.decodeResource(getResources(),R.drawable.demo);
                        pictureProcessTask.execute(bmpDummy);
                    } else {
                        pictureProcessTask.execute(bmp);
                    }
                }
            });

        }
    };


    /**
     * Switch status, and change UI setting
     * @param status
     * Caller must be UI thread
     */
    public void setStatus(STATUS status){
        DebugUtility.logDebug("status = " + status);
        EditText editTxtResult = (EditText)findViewById(R.id.editText_result);
        ImageView imgResult = (ImageView)findViewById(R.id.imageView_result);
        switch (status){
        case MONITOR:
            if(m_menuFaceBack != null){
                m_menuFaceBack.setVisible(true);
                m_menuRetake.setVisible(false);
                m_menuCopy.setEnabled(false);
                m_menuSave.setEnabled(false);
                m_menuSetting.setVisible(true);
                m_menuInfo.setVisible(true);
            }
            editTxtResult.setText("");
            editTxtResult.setVisibility(View.INVISIBLE);
            imgResult.setVisibility(View.INVISIBLE);
            m_cameraPreview.setVisibility(View.VISIBLE);
            m_facePreview.setVisibility(View.VISIBLE);
            m_cameraPreview.startPreview();
            lockScreenOrientation(m_ctx, false);    // Enable Rotate Screen
            break;
        case PROCESSING:
            lockScreenOrientation(m_ctx, true);    // Disable Rotate Screen

            if(m_menuFaceBack != null){
                m_menuFaceBack.setVisible(false);
                m_menuRetake.setVisible(false);
                m_menuCopy.setEnabled(false);
                m_menuSave.setEnabled(false);
                m_menuSetting.setVisible(false);
                m_menuInfo.setVisible(false);
            }
            editTxtResult.setVisibility(View.INVISIBLE);
            imgResult.setVisibility(View.INVISIBLE);
            break;
        case RESULT:
            if(m_status == STATUS.SUSPEND_PROCESSING){    // Finish Convert Process when suspend
                status = STATUS.SUSPEND_FINISHPROCESS;   // Show Result when next onResume
                break;
            }
            showResult();
            lockScreenOrientation(m_ctx, true);    // Disable Rotate Screen
            break;
        case SUSPEND_PROCESSING:
        case SUSPEND_FINISHPROCESS:
            //Do nothing
            break;
        default:
        }
        m_status = status;
    }

    private void showResult() {
        EditText editTxtResult = (EditText)findViewById(R.id.editText_result);
        ImageView imgResult = (ImageView)findViewById(R.id.imageView_result);
        if(m_menuFaceBack != null){
            m_menuFaceBack.setVisible(false);
            m_menuRetake.setVisible(true);
            m_menuCopy.setEnabled(true);
            m_menuSave.setEnabled(true);
            m_menuSetting.setVisible(false);
            m_menuInfo.setVisible(true);
        }

        if(m_convert != CONVERT.RAW){
            /*** Tentative ***/
if(false){
            editTxtResult.setText(m_strResult);
            editTxtResult.bringToFront();
            editTxtResult.setVisibility(View.VISIBLE);
} else{
            imgResult.setImageBitmap(m_imageResult);
            imgResult.bringToFront();
            imgResult.setVisibility(View.VISIBLE);
}
        } else {
            imgResult.setImageBitmap(m_imageResult);
            imgResult.bringToFront();
            imgResult.setVisibility(View.VISIBLE);
        }
        m_cameraPreview.setVisibility(View.INVISIBLE);
        m_facePreview.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PREFERENCES) {
            getSetting();
        }
    }

    private void getSetting(){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

        /* Get Converte type */
        String strConvert = pref.getString(ActivitySettings.PREF_KEY_CONVERT, "");

        if(strConvert.equals(getString(R.string.convert_type_grayscale_val))){
            m_convert = CONVERT.GRAYSCALE;
        } else if(strConvert.equals(getString(R.string.convert_type_edge_val))){
            m_convert = CONVERT.EDGE;
        } else {
            m_convert = CONVERT.RAW;
        }

        /* Get Font Soze */
        /* convert fontsize(%) to fontsize(SP) */
        int fontSize = pref.getInt(ActivitySettings.PREF_KEY_FONT_SIZE, 50);
        int TEST_SIZE_MAX = 16;
        int TEST_SIZE_MIN = 2;
        m_textSize = (TEST_SIZE_MAX-TEST_SIZE_MIN)*fontSize/100.0f + TEST_SIZE_MIN;

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){
            if(m_status == STATUS.RESULT){
                setStatus(STATUS.MONITOR);
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean isProcessing(){
        return (m_status==STATUS.PROCESSING);
    }
    public void cancelProcess(){
        setStatus(STATUS.MONITOR);
    }
    public STATUS getStatus(){
        return m_status;
    }

    /**
     * Get Camera Information
     * Call once when application starts
     * Default Camera is set FRONT
     */
    public void getCameraInfo(){
        if( (m_cameraId_FACE != -1) || (m_cameraId_BACK != -1) )return;
        int numberOfCameras = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                m_cameraId_BACK = i;
            }
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                m_cameraId_FACE = i;
                m_cameraId = i;
            }
        }

        /* If there is no proper camerea, set 0 */
        if(m_cameraId_BACK == -1){
            m_cameraId_BACK = 0;
        }
        if(m_cameraId_FACE == -1){
            m_cameraId_FACE = 0;
        }

        if (m_cameraId == -1){
            m_cameraId = m_cameraId_FACE;
        } else {
            /* only when m_cameraId is restored */
        }
    }

    private void registAndroidDB(String path) {
        ContentValues values = new ContentValues();
        ContentResolver contentResolver = ActivityMain.this.getContentResolver();
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put("_data", path);
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    /**
     * ENABLE/DISABLE LOCK ROTATE SCREEN
     * @param flg true:DISABLE, false:ENABLE
     * references: http://www.takaiwa.net/2013/09/android_19.html
     */
    public static void lockScreenOrientation(Activity activity, Boolean flg){
        if(flg){
            switch (((WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE))
                    .getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        break;
                    case Surface.ROTATION_180:
                        activity.setRequestedOrientation(9/* reversePortait */);
                        break;
                    case Surface.ROTATION_270:
                        activity.setRequestedOrientation(8/* reverseLandscape */);
                        break;
                    default :
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }else{
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("CAMERA_ID", m_cameraId);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        m_cameraId = savedInstanceState.getInt("CAMERA_ID");
    }

}
