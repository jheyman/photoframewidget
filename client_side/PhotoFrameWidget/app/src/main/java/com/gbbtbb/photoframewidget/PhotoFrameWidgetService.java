package com.gbbtbb.photoframewidget;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Debug;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class PhotoFrameWidgetService extends IntentService {

    public PhotoFrameWidgetService() {
        super(PhotoFrameWidgetService.class.getName());
    }

    class ImageInfo
    {
        public String path;
        public int width;
        public int heigth;
        public int orientation;
    };

    final String BASE_DIR= "/mnt/photo";
    final int MIN_IMAGE_WIDTH = 128;
    final int MAX_IMAGE_WIDTH = 8192;
    final int TARGET_DISPLAYED_IMAGE_WIDTH = 800;
    final int TARGET_SAVED_IMAGE_WIDTH = 1600;

    @Override
    protected void onHandleIntent(Intent intent) {

        Bitmap b = null;

        final String action = intent.getAction();

        Log.i("PhotoFrameWidgetService", "onHandleIntent action= " + action );

        if (action.equals(PhotoFrameWidgetProvider.FULLRESSAVE_ACTION)) {

            String imagePath = intent.getStringExtra("imagepath");
            int width = intent.getIntExtra("width", 0);
            int heigth = intent.getIntExtra("height", 0);
            int orientation = intent.getIntExtra("orientation", 0);

            // get image data, with no rescaling
            b = getImage(imagePath, width, heigth, orientation, TARGET_SAVED_IMAGE_WIDTH);

            // Save image to temporary location
            String path = intent.getStringExtra("savepath");
            Log.i("PhotoFrameWidgetService", "Saving path = " + path);

            FileOutputStream out = null;
            try {
                out = new FileOutputStream(path+"/temp.png");
                b.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Notify widget that image has been saved to disk
            final Intent doneIntent = new Intent(this, PhotoFrameWidgetProvider.class);
            doneIntent.setAction(PhotoFrameWidgetProvider.FULLRESSAVINGDONE_ACTION);
            final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            try {
                Log.i("PhotoFrameWidgetService", "onHandleIntent: launching pending Intent for loading done");
                donePendingIntent.send();
            }
            catch (PendingIntent.CanceledException ce) {
                Log.i("PhotoFrameWidgetService", "onHandleIntent: Exception: "+ce.toString());
            }

        } else if (action.equals(PhotoFrameWidgetProvider.GETLOWRESIMAGE_ACTION)) {
            // Get a random image from remote server
            ImageInfo inf = getRandomImageInfo(BASE_DIR);

            String pathToRandomImage = inf.path;
            int width = inf.width;
            int heigth = inf.heigth;

            // Just filtering out suspiciously small or large images
            if ((width > MIN_IMAGE_WIDTH) && (width < MAX_IMAGE_WIDTH)) {

                b = getImage(pathToRandomImage, width, heigth, inf.orientation, TARGET_DISPLAYED_IMAGE_WIDTH);

                Log.i("PhotoFrameWidgetService", "refreshing widget with image " + pathToRandomImage);
                ComponentName me = new ComponentName(this, PhotoFrameWidgetProvider.class);
                AppWidgetManager mgr = AppWidgetManager.getInstance(this);
                mgr.updateAppWidget(me, buildUpdate(this, b, inf));
            }
            else {
                Log.i("PhotoFrameWidgetService", String.format("image width (%d) is out of bounds [%d, %d]", width, MIN_IMAGE_WIDTH, MAX_IMAGE_WIDTH));
            }
        }
    }

    private RemoteViews buildUpdate(Context context, Bitmap b, ImageInfo inf) {

        RemoteViews rv = new RemoteViews(context.getPackageName(),R.layout.photoframewidget);
        if (b != null) {
            rv.setImageViewBitmap(R.id.imageView, b);
        }
        else {
            Log.e("PhotoFrameWidgetService", "NULL bitmap: skipping");
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.preview);
            rv.setImageViewBitmap(R.id.imageView, bm);
        }
        rv.setTextViewText(R.id.textView, inf.path.substring(BASE_DIR.length()));

        final Intent doneIntent = new Intent(context, PhotoFrameWidgetProvider.class);
        doneIntent.setAction(PhotoFrameWidgetProvider.LOWRESLOADINGDONE_ACTION);
        doneIntent.putExtra("imagepath", inf.path);
        doneIntent.putExtra("width", inf.width);
        doneIntent.putExtra("heigth", inf.heigth);
        doneIntent.putExtra("orientation", inf.orientation);
        final PendingIntent donePendingIntent = PendingIntent.getBroadcast(context, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            Log.i("PhotoFrameWidgetService", "buildUpdate: launching pending Intent for loading done");
            donePendingIntent.send();
        }
        catch (PendingIntent.CanceledException ce) {
            Log.i("PhotoFrameWidgetService", "buildUpdate: Exception: "+ce.toString());
        }

        // image is about to be displayed: hide progress bar now.
        rv.setViewVisibility(R.id.loadingProgress, View.GONE);
        return rv;
    }

    private String httpRequest(String url) {
        String result = "";

        Log.i("PhotoFrameWidgetService", "Performing HTTP request " + url);

        try {

            URL targetUrl = new URL(url);
            HttpURLConnection urlConnection = (HttpURLConnection) targetUrl.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                result = readStream(in);
            }
            finally {
                urlConnection.disconnect();
            }
        } catch(Exception e) {
            Log.e("PhotoFrameWidgetService", "httpRequest: Error in http connection "+e.toString());
        }

        String data ;
        if (result.length() <= 128)
            data = result;
        else
            data = "[long data....]";

        Log.i("PhotoFrameWidgetService", "httpRequest completed, received "+ result.length() + " bytes: " + data);

        return result;
    }

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while(i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private ImageInfo getRandomImageInfo(String basePath) {

        String charset = "UTF-8";
        String query = "";
        ImageInfo inf = new ImageInfo();

        try {
            query = String.format("http://192.168.0.13:8081/getRandomImagePath.php?basepath=%s",
                    URLEncoder.encode(basePath, charset));
        }
        catch (UnsupportedEncodingException e) {
            Log.e("PhotoFrameWidgetService", "getRandomImageInfo: error encoding URL params: " + e.toString());
        }

        String result = httpRequest(query);

        String[] parts = result.split(";");

        inf.path = parts[0];

        try{
            inf.width = Integer.parseInt(parts[1]);
            inf.heigth = Integer.parseInt(parts[2]);
        } catch (NumberFormatException n) {
            Log.e("PhotoFrameWidgetService", "getRandomImageInfo: error parsing image width or height: " + n.toString());
            inf.width = 512;
            inf.heigth = 512;
        }

        if (parts.length > 3) {
            try{
                inf.orientation = Integer.parseInt(parts[3]);
            } catch (NumberFormatException n) {
                Log.e("PhotoFrameWidgetService", "getRandomImageInfo: error parsing image orientation: " + n.toString());
                inf.orientation = 1;
            }
        } else {
            // EXIF orientation data is not available: set default value
            inf.orientation = 1;
        }

        return inf;
    }

    public static double getAvailableMemoryInMB(){
        double max = Runtime.getRuntime().maxMemory()/1024;
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);
        return (max - memoryInfo.getTotalPss())/1024;
    }

    private Bitmap getImage(String path, int originalWidth, int originalHeight, int originalOrientation, int targetWidth) {

        Bitmap b=null;
        Bitmap temp;

        String charset = "UTF-8";
        String query = "";

        try {
            query = String.format("http://192.168.0.13:8081/getImage.php?path=%s",
                    URLEncoder.encode(path, charset));
        }
        catch (UnsupportedEncodingException e) {
            Log.e("PhotoFrameWidgetService", "getImage: Error encoding URL params: " + e.toString());
        }

        double am = getAvailableMemoryInMB();

        try {
            URL url = new URL(query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();
            InputStream imageDataStream = new BufferedInputStream(conn.getInputStream());

            // decode the input image data stream into a bitmap, and resample as appropriate to fit a predefined size
            // this is useful to avoid decoding very large images into equally large bitmaps, which could cause out of
            // memory conditions on the device, and is not useful anyway since rendered image will be quite small.
            BitmapFactory.Options options = new BitmapFactory.Options();

            int inSampleSize = 1;

            if (originalWidth > targetWidth) {

                while ((originalWidth / inSampleSize) > targetWidth) {
                    inSampleSize *= 2;
                }
            }

            //Log.i("PhotoFrameWidgetService", "getImage: resampling factor=" + Integer.toString(inSampleSize));

            // Only proceed to load the resampled image if it is going to fit in available memory
            int resampledBitmapSizeInMB = (originalWidth/inSampleSize)*(originalHeight/inSampleSize)*4/(1024*1024);
            if ( resampledBitmapSizeInMB < 0.9*am ) {

                options.inSampleSize = inSampleSize;
                temp = BitmapFactory.decodeResourceStream(null, null, imageDataStream, null, options);
                int imageHeight = options.outHeight;
                int imageWidth = options.outWidth;

                imageDataStream.close();

                // Rotate image depending on the orientation setting gathered from the image EXIF data
                int rotationAngle = 0;
                switch (originalOrientation) {
                    case 1:
                        rotationAngle = 0;
                        break;
                    case 3:
                        rotationAngle = 180;
                        break;
                    case 6:
                        rotationAngle = 90;
                        break;
                    case 8:
                        rotationAngle = 270;
                        break;
                    default:
                        Log.e("PhotoFrameWidgetService", "Orientation value " + Integer.toString(originalOrientation) + " unknown");
                        break;
                }

                // if needed rotate image depending on EXIF orientation data, to view it heads-up
                if (rotationAngle != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotationAngle);
                    b = Bitmap.createBitmap(temp, 0, 0, temp.getWidth(), temp.getHeight(), matrix, true);
                } else {
                    b = temp;
                }
            }
            else {
                Log.e("PhotoFrameWidgetService", "Too few RAM available (" + Double.toString(0.9*am) + ") to load image (needed " + Integer.toString(resampledBitmapSizeInMB)+")");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("PhotoFrameWidgetService", "getImage: exception reading image over network");
        }

        return b;
    }
}
