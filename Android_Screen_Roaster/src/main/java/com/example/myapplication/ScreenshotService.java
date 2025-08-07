package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotService extends Service {
    private static final String TAG = "ScreenshotService";
    private static final String CHANNEL_ID = "screenshot_service_channel";
    private static final int NOTIFICATION_ID = 1;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;

    public interface ScreenshotCallback {
        void onScreenshotTaken(String filePath);
        void onScreenshotFailed(String error);
    }

    private static ScreenshotCallback callback;

    public static void setCallback(ScreenshotCallback cb) {
        callback = cb;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        getScreenMetrics();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("resultCode") && intent.hasExtra("data")) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            
            startForeground(NOTIFICATION_ID, createNotification());
            startProjection(resultCode, data);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screenshot Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service for taking screenshots");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Roaster")
                .setContentText("Ready to capture screenshots")
                .setSmallIcon(R.drawable.ic_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void getScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    private void startProjection(int resultCode, Intent data) {
        MediaProjectionManager projectionManager = 
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        
        if (mediaProjection != null) {
            setupImageReader();
            setupVirtualDisplay();
            
            // Take screenshot after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(this::captureScreenshot, 500);
        } else {
            if (callback != null) {
                callback.onScreenshotFailed("Failed to start media projection");
            }
            stopSelf();
        }
    }

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                processImage();
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void setupVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
        );
    }

    private void captureScreenshot() {
        if (imageReader != null) {
            // The image will be available in the ImageReader callback
            Log.d(TAG, "Screenshot capture initiated");
        }
    }

    private void processImage() {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * screenWidth;

                Bitmap bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                );
                bitmap.copyPixelsFromBuffer(buffer);

                // Crop the bitmap to remove padding
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                
                String filePath = saveBitmap(croppedBitmap);
                
                if (filePath != null && callback != null) {
                    callback.onScreenshotTaken(filePath);
                } else if (callback != null) {
                    callback.onScreenshotFailed("Failed to save screenshot");
                }

                bitmap.recycle();
                croppedBitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            if (callback != null) {
                callback.onScreenshotFailed("Error processing screenshot: " + e.getMessage());
            }
        } finally {
            if (image != null) {
                image.close();
            }
            cleanup();
        }
    }

    private String saveBitmap(Bitmap bitmap) {
        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File screenshotsDir = new File(picturesDir, "Screenshots");
            
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "Screenshot_" + timestamp + ".png";
            File file = new File(screenshotsDir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap", e);
            return null;
        }
    }

    private void cleanup() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        stopSelf();
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}