package com.example.myapplication.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;

import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;

import java.util.Arrays;

import com.google.common.util.concurrent.ListenableFuture;
import com.example.myapplication.R;
import com.example.myapplication.viewmodel.CameraViewModel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private PreviewView previewView;
    private Button btnStop;
    private Button btnBack;
    private TextView tvFps;

    private CameraViewModel cameraViewModel;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        setupCameraActivityViews();

        cameraViewModel = new ViewModelProvider(this).get(CameraViewModel.class);

        //create another backgraound thread for computing fps ,so main thread lag na kre (preventing frezz)
        cameraExecutor = Executors.newSingleThreadExecutor();

        cameraViewModel.getFps().observe(this, fps -> tvFps.setText(fps + " FPS"));

        cameraViewModel.getIsStreaming().observe(this, streaming -> btnStop.setEnabled(streaming));

        btnStop.setOnClickListener(v -> stopCamera());
        btnBack.setOnClickListener(v -> finish());

        if (hasCameraPermission()) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupCameraActivityViews(){
        previewView = findViewById(R.id.previewView);
        btnStop = findViewById(R.id.btnStop);
        btnBack = findViewById(R.id.btnBack);
        tvFps = findViewById(R.id.tvFps);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Initialize
                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    cameraViewModel.onFrame();
                    imageProxy.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                getCameraMetaData(camera.getCameraInfo());

                cameraViewModel.startStreaming();

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera failed to start: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    @androidx.annotation.OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void getCameraMetaData(CameraInfo cameraInfo) {
        if (cameraInfo == null) {
            Log.e("CameraLog", "CameraInfo is null. Cannot retrieve metadata.");
            return;
        }

        Camera2CameraInfo camera2Info = Camera2CameraInfo.from(cameraInfo);

        // 1. Resolution and Format
        StreamConfigurationMap configMap = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (configMap != null) {
            Log.d("CameraLog", "Supported Formats: " + Arrays.toString(configMap.getOutputFormats()));
            Log.d("CameraLog", "Supported Resolutions: " + Arrays.toString(configMap.getOutputSizes(SurfaceTexture.class)));
        }

        // 2. Frame Rate (FPS)
        Range<Integer>[] fpsRanges = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        Log.d("CameraLog", "Available FPS Ranges: " + Arrays.toString(fpsRanges));

        // 3. Sensor Size
        SizeF sensorSize = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        Size pixelArraySize = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Log.d("CameraLog", "Sensor Physical Size: " + sensorSize + " mm, Pixel Array: " + pixelArraySize);

        // 4. Lens Compatibility
        Integer hardwareLevel = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        int[] capabilities = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        Log.d("CameraLog", "Hardware Level: " + hardwareLevel + ", Capabilities: " + Arrays.toString(capabilities));
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraViewModel.stopStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraExecutor.shutdown();
    }
}