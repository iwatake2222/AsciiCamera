/**
 * ViewCameraOverlay
 * Face frame drawing
 * @author take.iwiw
 * @version 1.0.0
 */

package jp.take_iwiw.asciicamera;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera.Face;
import android.view.View;


public class ViewCameraOverlay extends View {
    private Paint mPaint;
    private Face[] mFaces;
    private int m_cameraDegree;
    private boolean m_isFacing;

    public ViewCameraOverlay(Context context) {
        super(context);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(Color.MAGENTA);
        mPaint.setAlpha(128);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public void setFaces(Face[] faces, int cameraDegree, boolean isFacing) {
        mFaces = faces;
        m_cameraDegree = cameraDegree;
        m_isFacing = isFacing;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFaces == null) {
            return;
        }
        for (Face face : mFaces) {
            if (face == null) {
                continue;
            }

            /* Convert coordinate system */
            /*   from  top left = (0, 0), bottom right = (canvas.width, canvas.height), unit = pixel */
            /*   to    top left = (-1000, 1000), bottom right = (1000, 1000), center of canvas = (0, 0) */
            Matrix matrix = new Matrix();
            matrix.postRotate(m_cameraDegree, 0, 0);
            matrix.postScale(getWidth() / 2000f, getHeight() / 2000f);
            matrix.postTranslate(getWidth() / 2f, getHeight() / 2f);

            // Mirroring
            if(m_isFacing)matrix.preScale(-1, 1);

            int saveCount = canvas.save();
            canvas.concat(matrix);
            canvas.drawRect(face.rect, mPaint);
            canvas.restoreToCount(saveCount);
        }
    }
}

