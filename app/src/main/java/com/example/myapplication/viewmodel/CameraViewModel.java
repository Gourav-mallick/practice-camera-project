package com.example.myapplication.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.util.FrameRateCounter;

public class CameraViewModel  extends ViewModel {

    private final MutableLiveData<Integer> fps = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isStreaming = new MutableLiveData<>(false);

    private final FrameRateCounter frameRateCounter = new FrameRateCounter(
            updatedFps -> fps.postValue(updatedFps)
    );

    public LiveData<Integer> getFps() {
        return fps;
    }

    public LiveData<Boolean> getIsStreaming() {
        return isStreaming;
    }

    public void onFrame() {
        frameRateCounter.onFrame();
    }

    public void startStreaming() {
        frameRateCounter.reset();
        isStreaming.setValue(true);
    }

    public void stopStreaming() {
        isStreaming.setValue(false);
        fps.setValue(0);
    }
}
