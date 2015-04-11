/**
 * ViewCameraPreview
 * Preview, Camera control
 * @author take.iwiw
 * @version 1.0.0
 */

package jp.take_iwiw.asciicamera;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import jp.take_iwiw.asciicamera.ActivityMain.CONVERT;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class ViewCameraPreview extends SurfaceView implements SurfaceHolder.Callback{
    final private int m_resolutionId = 0;
    private Camera m_cam = null;    //!< @brief recreated when user change camera type
    private int m_cameraId = -1;
    private int m_surfaceWidth = 64;    //!< @brief Surface Size which is updated when onCreate or rotate
    private int m_surfaceHeight = 48;    //!< @brief Surface Size which is updated when onCreate or rotate
    private ActivityMain m_contextParent = null;
    private boolean m_isAutoFocusing = false;
    private boolean m_isPortrait = false;
    private boolean m_isFacing = false;


    public ViewCameraPreview(ActivityMain context, int cameraId) {
        super(context);
        DebugUtility.logDebug("ViewCameraPreview" + cameraId);

        m_contextParent=context;
        m_cameraId = cameraId;

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(m_cameraId, info);
        m_isFacing = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        m_isPortrait = (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        DebugUtility.logDebug("surfaceCreated" + holder.toString());

        m_cam = Camera.open(m_cameraId);
        try {
            m_cam.setPreviewDisplay(holder);
        } catch (IOException e) {
            DebugUtility.logError(e.toString());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if(m_cam != null){
            m_cam.stopPreview();
            m_cam.release();
            m_cam = null;
        }
    }

    /**
     * Adjust SurfaceSize and rotation
     * SurfaceSize is fit to the same aspect as preview
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //DebugUtility.logDebug("surfaceChanged " + width + ", " + height);
        m_surfaceWidth = width;
        m_surfaceHeight = height;

        /* Bug Fix */
        /* If user tap the screen to take picture and rotate screen at the sametime, */
        /* sometimes, surfaceChanged is called after m_cam.autoFocus. */
        /* in this case, autoFocus Callback is never called */
        if(m_contextParent.isProcessing())return;
        if(m_isAutoFocusing){
            DebugUtility.logError("surfaceChanged Conflict!!");
            m_contextParent.cancelProcess();
        }

        m_cam.stopPreview();
        Camera.Parameters p = m_cam.getParameters();
        List<Size> previewSizes = m_cam.getParameters().getSupportedPreviewSizes();
        Size size = previewSizes.get(m_resolutionId);
        p.setPreviewSize(size.width, size.height);

        /* Fit SurfaceSize to CameraPreview */
        if(m_isPortrait){
            adjustFrameLayoutSize(size.height, size.width);
        } else {
            adjustFrameLayoutSize(size.width, size.height);
        }

        /* Adjust rotation */
        setCameraDisplayOrientation();

        m_cam.setParameters(p);
        m_cam.startPreview();


        /*** Face Detection ***/
if(DebugUtility.FACE_DETECT){
        m_cam.setFaceDetectionListener(new FaceDetectionListener() {
            @Override
            public void onFaceDetection(Face[] faces, Camera camera) {
if(false){
                for (Face face : faces) {
                    DebugUtility.logDebug("face id: " + face.id);
                    DebugUtility.logDebug("face score: " + face.score);
                    DebugUtility.logDebug("face rect: " + face.rect.left + "," + face.rect.top + " - "
                                  + face.rect.right + "," + face.rect.bottom);
                    if (face.mouth != null) {
                        DebugUtility.logDebug("face mouth: " + face.mouth.x + "," + face.mouth.y);
                        DebugUtility.logDebug("face leftEye: " + face.leftEye.x + "," + face.leftEye.y);
                        DebugUtility.logDebug("face rightEye: " + face.rightEye.x + "," + face.rightEye.y);
                    }
                }
}
                m_contextParent.m_facePreview.setFaces(faces, getCameraRotation(), m_isFacing);
            }
        });

        try {
            m_cam.startFaceDetection();
        } catch (IllegalArgumentException e) {
            DebugUtility.logDebug("IllegalArgumentException.");
        } catch (RuntimeException e) {
            DebugUtility.logDebug("the method fails or the face detection is already running.");
        }
}

    }


    /**
     * Fit the aspect of surface to that of preview
     * ex.
     *  - if surface.w = 64, surface.h = 32, camera.w = 128, camera.h = 96
     *      -> surface.w = 42.7, surface.h = 32
     *  - if surface.w = 64, surface.h = 32, camera.w = 128, camera.h = 48
     *      -> surface.w = 64, surface.h = 24
     */
    private void adjustFrameLayoutSize(int previewW, int previewH)
    {
        int targetW = m_surfaceWidth;
        int targetH = m_surfaceHeight;

        double taregtRatio = Math.min((double) targetW / previewW , (double) targetH / previewH);
        targetW = (int)(previewW * taregtRatio);
        targetH = (int)(previewH * taregtRatio);

        /* In order to change SurfaceSize, change the size of FrameLayout */
        /* because FrameLayout is parent of this view */
        m_contextParent.setFrameLayoutSize(targetW, targetH);
    }

    private void setCameraDisplayOrientation() {
        m_cam.setDisplayOrientation(getCameraRotation());
    }

    public int getCameraRotation(){
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(m_cameraId, info);
        Activity activity = (Activity) getContext();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degree = 0;
        switch (rotation) {
        case Surface.ROTATION_0: degree = 0; break;
        case Surface.ROTATION_90: degree = 90; break;
        case Surface.ROTATION_180: degree = 180; break;
        case Surface.ROTATION_270: degree = 270; break;
        }

        if(info.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
            return  (info.orientation - degree + 360) % 360;
        } else {
            /* mirroring  */
            return  (-info.orientation - degree + 360*2) % 360;
        }
    }



    /**
     * Get Bitmap Image from Preview
     * if m_contextParent.m_convert = true: return BlackWhite Image
     * if m_contextParent.m_convert = false: return Color Image
     */
    public interface GetPreviewCallback
    {
        void onGetPreview(Bitmap bmp);
    }
    public void getPreview(final GetPreviewCallback cb){
        m_cam.setPreviewCallback(null);
        m_cam.setPreviewCallback(new PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                m_cam.setPreviewCallback(null);
                //DebugUtility.logDebug("GetPreview " + Integer.toString(data[100]));

                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(m_cameraId, info);
                int width = camera.getParameters().getPreviewSize().width;
                int height = camera.getParameters().getPreviewSize().height;

                boolean isBlackWhite = (m_contextParent.m_convert != CONVERT.RAW);
                int yuvFormat = camera.getParameters().getPreviewFormat();
                Bitmap bmp = createBitmapFromYUV(data, yuvFormat, width, height, getCameraRotation(), isBlackWhite);
                cb.onGetPreview(bmp);

            }
        });
    }

    private Bitmap createBitmapFromYUV(byte[] data, int yuvFormat, int width, int height, int degree, boolean isBlackWhite){
        Bitmap tmpBmp;
        if(isBlackWhite){
            tmpBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int index = 0;
            for( int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){
                    int kido = data[index++]&0xff;
                    tmpBmp.setPixel(x, y, Color.argb( 255, kido, kido, kido));

                }
            }
        } else {
            YuvImage yuv = new YuvImage(data, yuvFormat, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);
            byte[] bytes = out.toByteArray();
            tmpBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }

        /* Mirroring */
        if(m_isFacing){
            if(degree % 180 != 0){
                degree += 180;
                degree %= 360;
            }
        }

        Matrix mat = new Matrix();
        mat.postRotate(degree);
        Bitmap retBmp = Bitmap.createBitmap(tmpBmp, 0, 0, width, height, mat, true);        // Set original image size

        return retBmp;
    }

    public void execAutoFocus(final Camera.AutoFocusCallback cb){
        m_isAutoFocusing = true;
        m_cam.autoFocus(new AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                m_isAutoFocusing = false;
                cb.onAutoFocus(success, camera);
            }
        });
    }


    public void stopPreview(){
        if(m_cam != null){
            m_cam.stopPreview();
        }
    }

    public void startPreview(){
        if(m_cam != null){
            m_cam.startPreview();
        }

    }
}

