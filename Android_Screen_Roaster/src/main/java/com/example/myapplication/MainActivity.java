package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ScreenshotService.ScreenshotCallback, ScreenshotAdapter.OnScreenshotActionListener {

    private static final int REQUEST_MEDIA_PROJECTION = 1000;
    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private MediaProjectionManager mediaProjectionManager;
    private RecyclerView recyclerViewScreenshots;
    private ScreenshotAdapter screenshotAdapter;
    private LinearLayout emptyStateLayout;
    private List<Screenshot> screenshots;

    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupRecyclerView();
        setupMediaProjection();
        setupPermissionLaunchers();
        loadScreenshots();

        ScreenshotService.setCallback(this);
    }

    private void initializeViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerViewScreenshots = findViewById(R.id.recyclerViewScreenshots);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        FloatingActionButton fabCapture = findViewById(R.id.fabCapture);
        fabCapture.setOnClickListener(v -> requestScreenshotPermission());
    }

    private void setupRecyclerView() {
        screenshots = new ArrayList<>();
        screenshotAdapter = new ScreenshotAdapter(this, screenshots);
        screenshotAdapter.setOnScreenshotActionListener(this);
        
        recyclerViewScreenshots.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewScreenshots.setAdapter(screenshotAdapter);
    }

    private void setupMediaProjection() {
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void setupPermissionLaunchers() {
        mediaProjectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            startScreenshotService(result.getResultCode(), data);
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show();
                    }
                }
        );

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        requestMediaProjection();
                    } else {
                        Toast.makeText(this, "Storage permission is required to save screenshots", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void requestScreenshotPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ doesn't need storage permission for app-specific directories
            requestMediaProjection();
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            requestMediaProjection();
        }
    }

    private void requestMediaProjection() {
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        mediaProjectionLauncher.launch(captureIntent);
    }

    private void startScreenshotService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, ScreenshotService.class);
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("data", data);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void loadScreenshots() {
        screenshots.clear();
        
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File screenshotsDir = new File(picturesDir, "Screenshots");
        
        if (screenshotsDir.exists() && screenshotsDir.isDirectory()) {
            File[] files = screenshotsDir.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
            
            if (files != null && files.length > 0) {
                // Sort files by last modified date (newest first)
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                
                for (File file : files) {
                    screenshots.add(new Screenshot(file.getAbsolutePath()));
                }
            }
        }
        
        updateUI();
    }

    private void updateUI() {
        if (screenshots.isEmpty()) {
            recyclerViewScreenshots.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerViewScreenshots.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
        
        screenshotAdapter.updateScreenshots(screenshots);
    }

    @Override
    public void onScreenshotTaken(String filePath) {
        runOnUiThread(() -> {
            Toast.makeText(this, getString(R.string.screenshot_saved), Toast.LENGTH_SHORT).show();
            
            // Add the new screenshot to the list
            screenshots.add(0, new Screenshot(filePath));
            updateUI();
        });
    }

    @Override
    public void onScreenshotFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, getString(R.string.screenshot_failed) + ": " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onScreenshotDeleted(Screenshot screenshot) {
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadScreenshots();
    }
}