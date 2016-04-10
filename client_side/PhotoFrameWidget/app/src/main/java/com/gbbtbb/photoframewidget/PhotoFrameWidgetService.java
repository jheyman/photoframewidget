package com.gbbtbb.photoframewidget;

import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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
        public int orientation;
    };

    final int MIN_IMAGE_WIDTH = 128;
    final int MAX_IMAGE_WIDTH = 8192;
    final int TARGET_IMAGE_WIDTH = 512;

    @Override
    protected void onHandleIntent(Intent intent) {
        Bitmap b = null;
        // Get a random image from remote server
        ImageInfo inf = getRandomImageInfo("/mnt/photo");

        String pathToRandomImage = inf.path;
        int width = inf.width;

        // Just filtering out suspiciously small or large images
        if ((width > MIN_IMAGE_WIDTH) && (width < MAX_IMAGE_WIDTH)) {

            b = getImage(pathToRandomImage, width, inf.orientation);

            // if valid, refresh widget with it
            if (b != null) {
                Log.i("PhotoFrameWidgetService", "refreshing widget with image " + pathToRandomImage);
                ComponentName me = new ComponentName(this, PhotoFrameWidgetProvider.class);
                AppWidgetManager mgr = AppWidgetManager.getInstance(this);
                mgr.updateAppWidget(me, buildUpdate(this, b, pathToRandomImage));
            } else {
                Log.i("PhotoFrameWidgetService", "could not get image " + pathToRandomImage);
            }
        } else {
            Log.i("PhotoFrameWidgetService", String.format("image width (%d) is out of bounds [%d, %d]", width, MIN_IMAGE_WIDTH, MAX_IMAGE_WIDTH));
        }
    }

    private RemoteViews buildUpdate(Context context, Bitmap b, String imagePath) {

        RemoteViews rv = new RemoteViews(context.getPackageName(),R.layout.photoframewidget);
        rv.setImageViewBitmap(R.id.imageView, b);
        rv.setTextViewText(R.id.textView, imagePath);
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
        } catch (NumberFormatException n) {
            Log.e("PhotoFrameWidgetService", "getRandomImageInfo: error parsing image width: " + n.toString());
            inf.width = 512;
        }

        if (parts.length > 2) {
            try{
                inf.orientation = Integer.parseInt(parts[2]);
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

    private Bitmap getImage(String path, int originalWidth, int originalOrientation) {

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

        try {
            URL url = new URL(query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();
            InputStream imageDataStream = new BufferedInputStream(conn.getInputStream());

            // decode the input image data stream into a bitmap, and resample as appropriate to fit a predefined size
            // this is useful to avoid decoding very large images into equally large bitmaps, which could cause out of
            // memory conditions on the device, and is not useful anyway since rendered image will be quite small.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = originalWidth/TARGET_IMAGE_WIDTH;
            temp = BitmapFactory.decodeResourceStream(null, null, imageDataStream, null, options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;
            Log.i("PhotoFrameWidgetService", "getImage: resampled height=" + Integer.toString(imageHeight) + ", resampled width=" + Integer.toString(imageWidth));

            imageDataStream.close();

            // Rotate image depending on the orientation setting gathered from the image EXIF data
            int rotationAngle = 0;
            switch(originalOrientation) {
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
                b =temp;
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.i("PhotoFrameWidgetService", "getImage: exception reading image over network");
        }

        return b;
    }
}
