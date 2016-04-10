package com.gbbtbb.photoframewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class PhotoFrameWidgetProvider extends AppWidgetProvider {

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

            // We are about to start loading a new image: make the progress bar visible
            remoteViews.setViewVisibility(R.id.loadingProgress, View.VISIBLE);

            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }

        // Get all ids
        ComponentName thisWidget = new ComponentName(context, PhotoFrameWidgetProvider.class);

        // Build the intent to call the service
        Intent intent = new Intent(context.getApplicationContext(), PhotoFrameWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

        // Update the widgets via the service
        context.startService(intent);

        Log.i("PhotoFrameWidgetProvidr", "onUpdate completed");
    }
}

