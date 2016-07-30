package com.gbbtbb.photoframewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;

public class PhotoFrameWidgetProvider extends AppWidgetProvider {

    public static String SENDEMAIL_ACTION = "com.gbbtbb.PhotoFrameWidgetProvider.SENDEMAIL_ACTION";
    public static String GETLOWRESIMAGE_ACTION = "com.gbbtbb.PhotoFrameWidgetProvider.GETLOWRESIMAGE_ACTION";
    public static String LOWRESLOADINGDONE_ACTION = "com.gbbtbb.PhotoFrameWidgetProvider.LOWRESLOADINGDONE_ACTION";
    public static String FULLRESSAVE_ACTION = "com.gbbtbb.PhotoFrameWidgetProvider.FULLRESSAVE_ACTION";
    public static String FULLRESSAVINGDONE_ACTION = "com.gbbtbb.PhotoFrameWidgetProvider.FULLRESSAVINGDONE_ACTION";

    private static String imageName = "";
    private static int imageHeight ;
    private static int imageWidth ;
    private static int imageOrientation ;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int count = appWidgetIds.length;

        Log.i("PhotoFrameWidgetProvidr", "onUpdate...");
        for (int i = 0; i < count; i++) {
            int widgetId = appWidgetIds[i];

            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.photoframewidget);

            // Register an intent so that onUpdate gets called again when user touches the widget,
            Intent intent = new Intent(context, PhotoFrameWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.imageView, pendingIntent);

            final Intent onClickIntent = new Intent(context, PhotoFrameWidgetProvider.class);
            onClickIntent.setAction(PhotoFrameWidgetProvider.SENDEMAIL_ACTION);
            //onClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            onClickIntent.setData(Uri.parse(onClickIntent.toUri(Intent.URI_INTENT_SCHEME)));
            final PendingIntent onClickPendingIntent = PendingIntent.getBroadcast(context, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.emailicon, onClickPendingIntent);

            // We are about to start loading a new image: make the progress bar visible
            remoteViews.setViewVisibility(R.id.loadingProgress, View.VISIBLE);

            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }

        // Get all ids
        ComponentName thisWidget = new ComponentName(context, PhotoFrameWidgetProvider.class);

        // Build the intent to call the service
        Intent intent = new Intent(context.getApplicationContext(), PhotoFrameWidgetService.class);
        intent.setAction(PhotoFrameWidgetProvider.GETLOWRESIMAGE_ACTION);
        //intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        // Update the widgets via the service
        context.startService(intent);

        Log.i("PhotoFrameWidgetProvidr", "onUpdate: background service started");
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();
        int[] appWidgetIds;
        Log.i("PhotoFrameWidgetProvidr", "onReceive " + action + " " + imageName);

        String path = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString();

        if (action.equals(SENDEMAIL_ACTION)) {

            // Show progress bar, this saving the image to disk may take a while
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(),  R.layout.photoframewidget);
            rv.setViewVisibility(R.id.loadingProgress, View.VISIBLE);
            ComponentName me = new ComponentName(ctx, PhotoFrameWidgetProvider.class);
            appWidgetManager.updateAppWidget(me, rv);

            // Build the intent to save full res image to disk
            Intent saveImgIntent = new Intent(ctx.getApplicationContext(), PhotoFrameWidgetService.class);
            saveImgIntent.setAction(PhotoFrameWidgetProvider.FULLRESSAVE_ACTION);
            saveImgIntent.putExtra("savepath", path);
            saveImgIntent.putExtra("imagepath", imageName);
            saveImgIntent.putExtra("height", imageHeight);
            saveImgIntent.putExtra("width", imageWidth);
            saveImgIntent.putExtra("orientation", imageOrientation);
            ctx.startService(saveImgIntent);

        } else if (action.equals(FULLRESSAVINGDONE_ACTION)) {

            // Hide progress bar now
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(),  R.layout.photoframewidget);
            rv.setViewVisibility(R.id.loadingProgress, View.GONE);
            ComponentName me = new ComponentName(ctx, PhotoFrameWidgetProvider.class);
            appWidgetManager.updateAppWidget(me, rv);

            // Then send it as an attachment in an email
            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            emailIntent.setType("text/html");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{""});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Photo");
            emailIntent.putExtra(Intent.EXTRA_TEXT, imageName);

            File f = new File(path, "temp.png");

            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));

            ctx.startActivity(emailIntent);
        } else if (action.equals(LOWRESLOADINGDONE_ACTION)) {

            imageName = intent.getStringExtra("imagepath");
            imageWidth = intent.getIntExtra("width", 0);
            imageHeight = intent.getIntExtra("heigth", 0);
            imageOrientation = intent.getIntExtra("orientation", 0);

            Log.i("PhotoFrameWidgetProvidr", "imagePath is " + imageName);
            Log.i("PhotoFrameWidgetProvidr", "imageHeight is " + Integer.toString(imageHeight));
            Log.i("PhotoFrameWidgetProvidr", "imageWidth is " + Integer.toString(imageWidth));
            Log.i("PhotoFrameWidgetProvidr", "imageOrientation is " + Integer.toString(imageOrientation));
        }

        super.onReceive(ctx, intent);
    }
}

