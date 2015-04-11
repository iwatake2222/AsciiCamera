/**
 * PictureProcessTask
 * Convert Bitmap into Ascii Text
 *   if ActivityMain.m_convert == false: show original bitmap
 *   if ActivityMain.m_convert == true: show converted Ascii Text
 *     *** Tentative ***
 *     Result should be Ascii Text.
 *     However result is converted bitmap, because there seems to be difference between the height of EditText
 *     and the height of calculated height from Paint especially when font size is too small.
 * @author take.iwiw
 * @version 1.0.0
 */

package jp.take_iwiw.asciicamera;
import jp.take_iwiw.asciicamera.ActivityMain.CONVERT;
import jp.take_iwiw.asciicamera.ActivityMain.STATUS;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.ImageView;


public class PictureProcessTask extends AsyncTask<Bitmap, Integer, String> {

    final String BR = System.getProperty("line.separator");
    private ActivityMain m_parentCtx;
    ProgressDialog m_dialog;
    private float m_textSize;

    private float m_resultTxtWidthNum;    // number of characters per 1 line, based on the size of 'A'
    private float m_resultTxtHeightNum;    // number of lines per 1 row, based on the size of 'A'
    private String m_strElements;    // characters used to draw (from EditText)
    private float m_widthElements[];    // width of each element character Ratio for 'A'. ex. x2.0
    private Bitmap m_bmp;    // Result BitMap for NO Convert mode

    /*** Tentative ***/
    private int m_resultImgW;   // Width of result image
    private int m_resultImgH;   // Height of result image

    public PictureProcessTask(ActivityMain context, float textSize) {
        this.m_parentCtx = context;
        m_textSize = (int)textSize;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        m_dialog = new ProgressDialog(m_parentCtx);
        m_dialog.setTitle("Please wait");
        m_dialog.setMessage("Processing data...");
        m_dialog.setCancelable(false);
        m_dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        m_dialog.setMax(100);
        m_dialog.setProgress(0);
        m_dialog.show();

        if(m_parentCtx.m_convert == CONVERT.RAW)return;

        EditText editTxtResult = (EditText)m_parentCtx.findViewById(R.id.editText_result);
        editTxtResult.bringToFront();
        editTxtResult.setBackgroundColor(Color.WHITE);
        editTxtResult.setPadding(0, 0, 0, 0);
        editTxtResult.setTextSize(TypedValue.COMPLEX_UNIT_PX, m_textSize);
        editTxtResult.setTypeface(Typeface.MONOSPACE);

        /*** Calculate the number of characters per 1 line ***/
        Paint paint = new Paint();
        paint.setTextSize(m_textSize);    // use pixel for Paint.setTextSize
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setAntiAlias(true);

        /** Calculate the number of lines per 1 row **/
        FontMetrics fontMetrics = paint.getFontMetrics();
        m_resultTxtHeightNum =Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
        m_resultTxtHeightNum = (int)(editTxtResult.getHeight() / m_resultTxtHeightNum);


        /* Get the width of default character(A) */
        float widthChar = paint.measureText("A");
        m_resultTxtWidthNum = (int)(editTxtResult.getWidth() / widthChar);
        /* Eliminate Space from seed characters */
        EditText editTxtElement = (EditText)m_parentCtx.findViewById(R.id.editText_elements);
        m_strElements = editTxtElement.getText().toString();
        m_strElements = m_strElements.replaceAll(" ", "");
        //m_strElements = m_strElements.replaceAll("　", "");
        /* Calculate the width of each seed characters */
        if (m_strElements.length() == 0)m_strElements = "-";
        m_widthElements = new float[m_strElements.length()];
        for(int i = 0; i<m_strElements.length(); i++){
            m_widthElements[i] = paint.measureText(m_strElements.substring(i, i+1)) / widthChar;
        }

        /*** Tentative ***/
        ImageView resultImg = (ImageView)m_parentCtx.findViewById(R.id.imageView_result);
        m_resultImgW = resultImg.getWidth();
        m_resultImgH = resultImg.getHeight();

        //m_resultTxtWidthNum = 5;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        DebugUtility.logDebug("onPostExecute");

        //if(!m_parentCtx.isProcessing())return;

        if(m_parentCtx.m_convert == CONVERT.RAW){
            m_parentCtx.setImageResult(m_bmp);
        } else {
            m_parentCtx.setStringResult(result);
if(true){   /*** Tentative ***/
            /* Convert Ascii Text into Bitmap */
            Bitmap bmp = Bitmap.createBitmap(m_resultImgW, m_resultImgH, Bitmap.Config.RGB_565);
            Canvas canvas;
            canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint();
            paint.setTextSize(m_textSize);
            //paint.setTypeface(Typeface.MONOSPACE);
            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paint.setAntiAlias(true);
            //canvas.drawColor(0xFF404040);
            //paint.setColor(0xFF00FF00);
            FontMetrics fontMetrics = paint.getFontMetrics();
            float heightChar = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            int numY = (int)(m_resultImgH/heightChar);

            String[] resultLine = result.split(BR);
            if (resultLine.length < numY)numY = resultLine.length;
            for(int y = 0; y<numY; y++){
                canvas.drawText(resultLine[y], 0, 0 + (y+1)*heightChar, paint);
            }
            m_parentCtx.setImageResult(bmp);
} /*** Tentative ***/
        }

        try {
            m_dialog.dismiss();
        } catch (Exception e) {
            // just in case...
        }

        m_parentCtx.setStatus(STATUS.RESULT);


    }

    @Override
    protected String doInBackground(Bitmap... params) {
        //DebugUtility.logDebug("Process start");

        if(m_parentCtx.m_convert == CONVERT.RAW){
            m_bmp = params[0];
            return "";
        }

        int targetW = (int)m_resultTxtWidthNum - 1; // margin
        //int targetH = (int)(targetW * ((float)params[0].getHeight()/params[0].getWidth()));
        int targetH = (int)(m_resultTxtHeightNum - 1);  // margin
        Matrix mat = new Matrix();
        mat.postScale((float)targetW / params[0].getWidth(), (float)targetH / params[0].getHeight());
        Bitmap bmp = Bitmap.createBitmap(params[0], 0, 0, params[0].getWidth(), params[0].getHeight(), mat, true);    // set original image size

        String str;

        if(m_parentCtx.m_convert == CONVERT.GRAYSCALE){
            str = convertGrayScale(targetW, targetH, bmp);
        } else {
            str = convertEdge(targetW, targetH, bmp);
        }

        return str;
    }

    /* Convert image into ascii by edge detection */
    private String convertEdge(int targetW, int targetH, Bitmap bmp) {
        String str = "";
        int totalPixel = bmp.getHeight();
        int lengthElements = m_strElements.length();
        int indexElements = 0;

        final int thEdge = 20;
        for( int y = 1; y < targetH-1; y++){
            float x = 1;
            while( x < targetW-1){
                int kidoPreX = bmp.getPixel((int)x-1, y) & 0xff;
                int kidoPreY = bmp.getPixel((int)x, y-1) & 0xff;
                int kidoNextX = bmp.getPixel((int)x+1, y) & 0xff;
                int kidoNextY = bmp.getPixel((int)x, y+1) & 0xff;
                if( (Math.abs(kidoPreX-kidoNextX) > thEdge) || (Math.abs(kidoPreY-kidoNextY) > thEdge) ){
                //if( Math.abs(kido-kidoNextX) + Math.abs(kido-kidoNextY) > th*2){
                    String strPut = m_strElements.substring(indexElements, indexElements+1);
                    str = str + strPut;
                    x+=m_widthElements[indexElements];
                    if(++indexElements >= lengthElements)indexElements=0;
                } else {
                    str = str + " ";
                    x++;
                }
            }
            int gap = (int)Math.ceil(x - targetW);
            if( gap >= 1){
                str = str.substring(0, str.length()-gap);
            }
            publishProgress((100*y)/totalPixel);
            str = str + BR;

            //if(!m_parentCtx.isProcessing())return "";
        }
        return str;
    }

    /* Convert image into ascii by grayscale */
    private String convertGrayScale(int targetW, int targetH, Bitmap bmp) {
        /* Auto Brightness Control */
        /* In order to get average brightness, convert 1x1 bitmap */
        Matrix brightCheckMat = new Matrix();
        brightCheckMat.postScale((float)1 / targetW, (float)1 / targetH);
        Bitmap brightCheckBmp = Bitmap.createBitmap(bmp, 0, 0, targetW, targetH, brightCheckMat, true);
        int brightness = brightCheckBmp.getPixel(0, 0) & 0xff;
        DebugUtility.logDebug("Brightness = " + brightness);
        int adj = brightness - 128;
        adj  /= 4;    // adj range = -32 to +32
        int th1 = 150;    //white
        int th2 = 100;    //gray
        int th3 = 60;    //black
        th1 += adj; if(th1<0)th1=0; th1 %= 255;
        th2 += adj; if(th2<0)th2=0; th2 %= 255;
        th3 += adj; if(th3<0)th3=0; th3 %= 255;

        String str = "";
        int totalPixel = bmp.getHeight();
        int lengthElements = m_strElements.length();
        int indexElements = 0;

        for( int y = 0; y < targetH; y++){
            float x = 0;
            while( x < targetW){
                int kido = bmp.getPixel((int)x, y) & 0xff;
                if(kido>th1){
                    str = str + " ";
                    x++;
                } else if(kido>th2){
                    String strPut = m_strElements.substring(indexElements, indexElements+1);
                    if(strPut.getBytes().length >= 2){
                        str = str + "  ";
                        x+=2;
                    } else {
                        str = str + " ";
                        x+=1;
                    }
                    str = str + strPut;
                    x+=m_widthElements[indexElements];
                    if(++indexElements >= lengthElements)indexElements=0;
                } else if(kido>th3){
                    str = str + "-";
                    x++;
                } else  {
                    String strPut = m_strElements.substring(indexElements, indexElements+1);
                    str = str + strPut;
                    x+=m_widthElements[indexElements];
                    if(++indexElements >= lengthElements)indexElements=0;
                }
            }
            int gap = (int)Math.ceil(x - targetW);
            if( gap >= 1){
                str = str.substring(0, str.length()-gap);
            }
            publishProgress((100*y)/totalPixel);
            str = str + BR;

            //if(!m_parentCtx.isProcessing())return "";
        }
        return str;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        m_dialog.setProgress(values[0]);

    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }


}
