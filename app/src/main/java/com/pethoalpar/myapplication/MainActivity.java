package com.pethoalpar.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE = 1024;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String DATA_PATH = Environment.getExternalStorageDirectory().toString()+"/Tess";
    private static final String TESS_DATA = "/tessdata";
    Mat imageMat;
    Mat imageMat2;
    private Uri outputFileDir;
    private TessBaseAPI tessBaseAPI;
    private ImageView imageView;
    private TextView textView;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:{
                    imageMat = new Mat();
                    imageMat2 = new Mat();
                }
                break;
                default:{
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG,"OpenCv problem");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        }else{
            Log.d(TAG, "OpenCV initiated success");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private void prepareTessData(){
        try{
            File dir = new File(DATA_PATH+TESS_DATA);
            if(!dir.exists()){
                dir.mkdir();
            }
            String fileList[] = getAssets().list("");
            for(String fileName : fileList){
                String pathToDataFile = DATA_PATH + TESS_DATA + "/" + fileName;
                if(!(new File(pathToDataFile)).exists()){
                    InputStream is = getAssets().open(fileName);
                    OutputStream os = new FileOutputStream(pathToDataFile);
                    byte [] buff = new byte[1024];
                    int len;
                    while((len = is.read(buff))>0){
                        os.write(buff,0,len);
                    }
                    is.close();
                    os.close();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, e.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Activity activity = this;
        imageView = (ImageView) this.findViewById(R.id.imageView);
        textView = (TextView) this.findViewById(R.id.textView);
        checkPermissions();
        this.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissions();
                startCameraActivity();
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 120);
        }
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 121);
        }
    }

    private void startCameraActivity(){
        try{
            String imagePath = DATA_PATH + "/imgs";
            File dir = new File(imagePath);
            if(!dir.exists()){
                dir.mkdir();
            }
            String imageFilePath = imagePath + "/ocr.jpg";
            outputFileDir = Uri.fromFile(new File(imageFilePath));
            final Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileDir);
            if( pictureIntent.resolveActivity(getPackageManager()) != null){
                startActivityForResult(pictureIntent, REQUEST_CODE);
            }
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                prepareTessData();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 10;
                Bitmap bitmap = BitmapFactory.decodeFile(outputFileDir.getPath(), options);

                ExifInterface ei = null;
                try {
                    ei = new ExifInterface(outputFileDir.getPath());
                } catch (IOException e) {
                    Log.d(TAG, "IO problem");
                }
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        bitmap = rotateImage(bitmap, 90);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        bitmap = rotateImage(bitmap, 180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        bitmap = rotateImage(bitmap, 270);
                        break;
                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        break;
                }
                Utils.bitmapToMat(bitmap, imageMat);
                imageView.setImageBitmap(bitmap);
                detectText(imageMat);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Canceled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Problem", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap rotateImage (Bitmap source, float angle){
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source,0 ,0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void detectText(Mat mat){
        Imgproc.cvtColor(imageMat, imageMat2, Imgproc.COLOR_RGB2GRAY);
        Mat mRgba = mat;
        Mat mGray = imageMat2;

        Scalar CONTOUR_COLOR = new Scalar(1, 255, 128, 0);
        MatOfKeyPoint keyPoint = new MatOfKeyPoint();
        List<KeyPoint> listPoint = new ArrayList<>();
        KeyPoint kPoint = new KeyPoint();
        Mat mask = Mat.zeros(mGray.size(), CvType.CV_8UC1);
        int rectanx1;
        int rectany1;
        int rectanx2;
        int rectany2;

        Scalar zeros = new Scalar(0,0,0);
        List<MatOfPoint> contour2 = new ArrayList<>();
        Mat kernel = new Mat(1, 50, CvType.CV_8UC1, Scalar.all(255));
        Mat morByte = new Mat();
        Mat hierarchy = new Mat();

        Rect rectan3 = new Rect();
        int imgSize = mRgba.height() * mRgba.width();

        if(true){
            FeatureDetector detector = FeatureDetector.create(FeatureDetector.MSER);
            detector.detect(mGray, keyPoint);
            listPoint = keyPoint.toList();
            for(int ind = 0; ind < listPoint.size(); ++ind){
                kPoint = listPoint.get(ind);
                rectanx1 = (int ) (kPoint.pt.x - 0.5 * kPoint.size);
                rectany1 = (int ) (kPoint.pt.y - 0.5 * kPoint.size);

                rectanx2 = (int) (kPoint.size);
                rectany2 = (int) (kPoint.size);
                if(rectanx1 <= 0){
                    rectanx1 = 1;
                }
                if(rectany1 <= 0){
                    rectany1 = 1;
                }
                if((rectanx1 + rectanx2) > mGray.width()){
                    rectanx2 = mGray.width() - rectanx1;
                }
                if((rectany1 + rectany2) > mGray.height()){
                    rectany2 = mGray.height() - rectany1;
                }
                Rect rectant = new Rect(rectanx1, rectany1, rectanx2, rectany2);
                Mat roi = new Mat(mask, rectant);
                roi.setTo(CONTOUR_COLOR);
            }
            Imgproc.morphologyEx(mask, morByte, Imgproc.MORPH_DILATE, kernel);
            Imgproc.findContours(morByte, contour2, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
            Bitmap bmp = null;
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i<contour2.size(); ++i){
                rectan3 = Imgproc.boundingRect(contour2.get(i));
                try{
                    Mat croppedPart = mGray.submat(rectan3);
                    bmp = Bitmap.createBitmap(croppedPart.width(), croppedPart.height(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(croppedPart,bmp);
                } catch (Exception e){
                    Log.d(TAG,"Cropped part error");
                }
                if(bmp != null){
                    String str = getTextWithTesseract(bmp);
                    if(str != null){
                        sb.append(str).append("\n");
                    }
                }
            }
            textView.setText(sb.toString());
        }
    }

    private String getTextWithTesseract(Bitmap bitmap){
        try{
            tessBaseAPI = new TessBaseAPI();
        }catch (Exception e){
            Log.d(TAG,e.getMessage());
        }
        tessBaseAPI.init(DATA_PATH,"eng");
        tessBaseAPI.setImage(bitmap);
        String retStr = tessBaseAPI.getUTF8Text();
        tessBaseAPI.end();
        return retStr;
    }
}
