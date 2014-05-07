/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.hardware.camera2.CameraCharacteristics.*;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.Face;
import android.hardware.camera2.Rational;
import android.hardware.camera2.Size;
import android.hardware.camera2.CameraMetadata.Key;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureListener;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Basic test for camera CaptureRequest key controls.
 * </p>
 * <p>
 * Several test categories are covered: manual sensor control, 3A control,
 * manual ISP control and other per-frame control and synchronization.
 * </p>
 */
public class CaptureRequestTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "CaptureRequestTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int NUM_FRAMES_VERIFIED = 15;
    private static final int NUM_FACE_DETECTION_FRAMES_VERIFIED = 60;
    /** 30ms exposure time must be supported by full capability devices. */
    private static final long DEFAULT_EXP_TIME_NS = 30000000L;
    private static final int DEFAULT_SENSITIVITY = 100;
    private static final int RGGB_COLOR_CHANNEL_COUNT = 4;
    private static final int MAX_SHADING_MAP_SIZE = 64 * 64 * RGGB_COLOR_CHANNEL_COUNT;
    private static final int MIN_SHADING_MAP_SIZE = 1 * 1 * RGGB_COLOR_CHANNEL_COUNT;
    private static final long IGORE_REQUESTED_EXPOSURE_TIME_CHECK = -1L;
    private static final long EXPOSURE_TIME_BOUNDARY_50HZ_NS = 10000000L; // 10ms
    private static final long EXPOSURE_TIME_BOUNDARY_60HZ_NS = 8333333L; // 8.3ms, Approximation.
    private static final long EXPOSURE_TIME_ERROR_MARGIN_NS = 100000L; // 100us, Approximation.
    private static final int SENSITIVITY_ERROR_MARGIN = 10; // 10
    private static final int DEFAULT_NUM_EXPOSURE_TIME_STEPS = 3;
    private static final int DEFAULT_NUM_SENSITIVITY_STEPS = 16;
    private static final int DEFAULT_SENSITIVITY_STEP_SIZE = 100;
    private static final int NUM_RESULTS_WAIT_TIMEOUT = 100;
    private static final int NUM_TEST_FOCUS_DISTANCES = 10;
    // 5 percent error margin for calibrated device
    private static final float FOCUS_DISTANCE_ERROR_PERCENT_CALIBRATED = 0.05f;
    // 25 percent error margin for uncalibrated device
    private static final float FOCUS_DISTANCE_ERROR_PERCENT_UNCALIBRATED = 0.25f;
    // 10 percent error margin for approximate device
    private static final float FOCUS_DISTANCE_ERROR_PERCENT_APPROXIMATE = 0.10f;
    private static final int ANTI_FLICKERING_50HZ = 1;
    private static final int ANTI_FLICKERING_60HZ = 2;

    // Linear tone mapping curve example.
    private static final float[] TONEMAP_CURVE_LINEAR = {0, 0, 1.0f, 1.0f};
    // Standard sRGB tone mapping, per IEC 61966-2-1:1999, with 16 control points.
    private static final float[] TONEMAP_CURVE_SRGB = {
            0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
            0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
            0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
            0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f
    };
    private final Rational ZERO_R = new Rational(0, 1);
    private final Rational ONE_R = new Rational(1, 1);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test black level lock when exposure value change.
     * <p>
     * When {@link CaptureRequest#BLACK_LEVEL_LOCK} is true in a request, the
     * camera device should lock the black level. When the exposure values are changed,
     * the camera may require reset black level Since changes to certain capture
     * parameters (such as exposure time) may require resetting of black level
     * compensation. However, the black level must remain locked after exposure
     * value changes (when requests have lock ON).
     * </p>
     */
    public void testBlackLevelLock() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                if (!mStaticInfo.isHardwareLevelFull()) {
                    continue;
                }

                SimpleCaptureListener listener = new SimpleCaptureListener();
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                // Start with default manual exposure time, with black level being locked.
                requestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, true);
                changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS, DEFAULT_SENSITIVITY);

                Size previewSz =
                        getMaxPreviewSize(mCamera.getId(), mCameraManager, PREVIEW_SIZE_BOUND);
                startPreview(requestBuilder, previewSz, listener);

                // No lock OFF state is allowed as the exposure is not changed.
                verifyBlackLevelLockResults(listener, NUM_FRAMES_VERIFIED, /*maxLockOffCnt*/0);

                // Double the exposure time and gain, with black level still being locked.
                changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS * 2, DEFAULT_SENSITIVITY * 2);
                startPreview(requestBuilder, previewSz, listener);

                // Allow at most one lock OFF state as the exposure is changed once.
                verifyBlackLevelLockResults(listener, NUM_FRAMES_VERIFIED, /*maxLockOffCnt*/1);

                stopPreview();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Basic lens shading map request test.
     * <p>
     * When {@link CaptureRequest#SHADING_MODE} is set to OFF, no lens shading correction will
     * be applied by the camera device, and an identity lens shading map data
     * will be provided if {@link CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE} is ON.
     * </p>
     * <p>
     * When {@link CaptureRequest#SHADING_MODE} is set to other modes, lens shading correction
     * will be applied by the camera device. The lens shading map data can be
     * requested by setting {@link CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE} to ON.
     * </p>
     */
    public void testLensShadingMap() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                if (!mStaticInfo.isHardwareLevelFull()) {
                    continue;
                }

                SimpleCaptureListener listener = new SimpleCaptureListener();
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                // Shading map mode OFF, lensShadingMapMode ON, camera device
                // should output unity maps.
                requestBuilder.set(CaptureRequest.SHADING_MODE, SHADING_MODE_OFF);
                requestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                        STATISTICS_LENS_SHADING_MAP_MODE_ON);

                Size mapSz = mStaticInfo.getCharacteristics().get(LENS_INFO_SHADING_MAP_SIZE);
                Size previewSz =
                        getMaxPreviewSize(mCamera.getId(), mCameraManager, PREVIEW_SIZE_BOUND);

                listener = new SimpleCaptureListener();
                startPreview(requestBuilder, previewSz, listener);

                verifyShadingMap(listener, NUM_FRAMES_VERIFIED, mapSz, SHADING_MODE_OFF);

                // Shading map mode FAST, lensShadingMapMode ON, camera device
                // should output valid maps.
                requestBuilder.set(CaptureRequest.SHADING_MODE, SHADING_MODE_FAST);

                listener = new SimpleCaptureListener();
                startPreview(requestBuilder, previewSz, listener);

                // Allow at most one lock OFF state as the exposure is changed once.
                verifyShadingMap(listener, NUM_FRAMES_VERIFIED, mapSz, SHADING_MODE_FAST);

                // Shading map mode HIGH_QUALITY, lensShadingMapMode ON, camera device
                // should output valid maps.
                requestBuilder.set(CaptureRequest.SHADING_MODE, SHADING_MODE_HIGH_QUALITY);

                listener = new SimpleCaptureListener();
                startPreview(requestBuilder, previewSz, listener);

                verifyShadingMap(listener, NUM_FRAMES_VERIFIED, mapSz, SHADING_MODE_HIGH_QUALITY);

                stopPreview();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test {@link CaptureRequest#CONTROL_AE_ANTIBANDING_MODE} control.
     * <p>
     * Test all available anti-banding modes, check if the exposure time adjustment is
     * correct.
     * </p>
     */
    public void testAntiBandingModes() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                if (!mStaticInfo.isHardwareLevelFull()) {
                    continue;
                }

                byte[] modes = mStaticInfo.getAeAvailableAntiBandingModesChecked();

                Size previewSz =
                        getMaxPreviewSize(mCamera.getId(), mCameraManager, PREVIEW_SIZE_BOUND);

                for (byte mode : modes) {
                    antiBandingTestByMode(previewSz, mode);
                }
            } finally {
                closeDevice();
            }
        }

    }

    /**
     * Test AE mode and lock.
     *
     * <p>
     * For AE lock, when it is locked, exposure parameters shouldn't be changed.
     * For AE modes, each mode should satisfy the per frame controls defined in
     * API specifications.
     * </p>
     */
    public void testAeModeAndLock() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                // Can only test full capability because test relies on per frame control
                // and synchronization.
                if (!mStaticInfo.isHardwareLevelFull()) {
                    continue;
                }

                Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.

                // Update preview surface with given size for all sub-tests.
                updatePreviewSurface(maxPreviewSz);

                // Test aeMode and lock
                byte[] aeModes = mStaticInfo.getAeAvailableModesChecked();
                for (byte mode : aeModes) {
                    aeModeAndLockTestByMode(mode);
                }
            } finally {
                closeDevice();
            }
        }
    }

    /** Test {@link CaptureRequest#FLASH_MODE} control.
     * <p>
     * For each {@link CaptureRequest#FLASH_MODE} mode, test the flash control
     * and {@link CaptureResult#FLASH_STATE} result.
     * </p>
     */
    public void testFlashControl() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                // Can only test full capability because test relies on per frame control
                // and synchronization.
                if (!mStaticInfo.isHardwareLevelFull()) {
                    continue;
                }

                SimpleCaptureListener listener = new SimpleCaptureListener();
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.

                startPreview(requestBuilder, maxPreviewSz, listener);

                // Flash control can only be used when the AE mode is ON or OFF.
                flashTestByAeMode(listener, CaptureRequest.CONTROL_AE_MODE_ON);
                flashTestByAeMode(listener, CaptureRequest.CONTROL_AE_MODE_OFF);

                stopPreview();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test face detection modes and results.
     */
    public void testFaceDetection() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                faceDetectionTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test tone map modes and controls.
     */
    public void testToneMapControl() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                toneMapTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test color correction modes and controls.
     */
    public void testColorCorrectionControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);

                colorCorrectionTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    public void testEdgeModeControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                edgeModesTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test focus distance control.
     */
    public void testFocusDistanceControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported() || !mStaticInfo.hasFocuser()) {
                    Log.i(TAG, "Camera " + id
                            + "Doesn't support per frame control or has no focuser");
                    continue;
                }

                focusDistanceTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    public void testNoiseReductionModeControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                noiseReductionModeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test AWB lock control. The color correction gain and transform shouldn't be changed
     * when AWB is locked.
     */
    public void testAwbModeAndLock() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                awbModeAndLockTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test different AF modes.
     */
    public void testAfModes() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                afModeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test video and optical stabilizations.
     */
    public void testCameraStabilizations() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                stabilizationTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test digitalZoom (center wise and non-center wise), validate the returned crop regions.
     */
    public void testDigitalZoom() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                digitalZoomTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test scene mode controls.
     */
    public void testSceneModes() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                sceneModeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test effect mode controls.
     */
    public void testEffectModes() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isPerFrameControlSupported()) {
                    Log.i(TAG, "Camera " + id + "Doesn't support per frame control");
                    continue;
                }

                effectModeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    // TODO: add 3A state machine test.

    private void noiseReductionModeTestByCamera() throws Exception {
        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        byte[] availableModes = mStaticInfo.getAvailableNoiseReductionModesChecked();
        SimpleCaptureListener resultListener = new SimpleCaptureListener();
        startPreview(requestBuilder, maxPrevSize, resultListener);

        for (byte mode : availableModes) {
            requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, (int)mode);
            resultListener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), resultListener, mHandler);

            verifyCaptureResultForKey(CaptureResult.NOISE_REDUCTION_MODE, (int)mode,
                    resultListener, NUM_FRAMES_VERIFIED);

            // Test that OFF and FAST mode should not slow down the frame rate.
            if (mode == CaptureRequest.NOISE_REDUCTION_MODE_OFF ||
                    mode == CaptureRequest.NOISE_REDUCTION_MODE_FAST) {
                verifyFpsNotSlowDown(requestBuilder, NUM_FRAMES_VERIFIED);
            }
        }

        stopPreview();
    }

    private void focusDistanceTestByCamera() throws Exception {
        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        float[] testDistances = getFocusDistanceTestValuesInOrder();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        SimpleCaptureListener resultListener = new SimpleCaptureListener();
        startPreview(requestBuilder, maxPrevSize, resultListener);

        CaptureRequest request;
        float[] resultDistances = new float[testDistances.length];
        for (int i = 0; i < testDistances.length; i++) {
            requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, testDistances[i]);
            request = requestBuilder.build();
            resultListener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(request, resultListener, mHandler);
            resultDistances[i] = verifyFocusDistanceControl(testDistances[i], request,
                    resultListener);
            if (VERBOSE) {
                Log.v(TAG, "Capture request focus distance: " + testDistances[i] + " result: "
                        + resultDistances[i]);
            }
        }

        // Verify the monotonicity
        mCollector.checkArrayMonotonicityAndNotAllEqual(CameraTestUtils.toObject(resultDistances),
                /*ascendingOrder*/true);

        // Test hyperfocal distance optionally
        float hyperFocalDistance = mStaticInfo.getHyperfocalDistanceChecked();
        if (hyperFocalDistance > 0) {
            requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, hyperFocalDistance);
            request = requestBuilder.build();
            resultListener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(request, resultListener, mHandler);

            // Then wait for the lens.state to be stationary.
            waitForResultValue(resultListener, CaptureResult.LENS_STATE,
                    CaptureResult.LENS_STATE_STATIONARY, NUM_RESULTS_WAIT_TIMEOUT);
            // Need get reasonably accurate value.
            CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            Float focusDistance = getValueNotNull(result, CaptureResult.LENS_FOCUS_DISTANCE);
            float errorMargin = FOCUS_DISTANCE_ERROR_PERCENT_UNCALIBRATED;
            int calibrationStatus = mStaticInfo.getFocusDistanceCalibrationChecked();
            if (calibrationStatus ==
                    CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED) {
                errorMargin = FOCUS_DISTANCE_ERROR_PERCENT_CALIBRATED;
            } else if (calibrationStatus ==
                    CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE) {
                errorMargin = FOCUS_DISTANCE_ERROR_PERCENT_APPROXIMATE;
            }
            mCollector.expectInRange("Focus distance for hyper focal should be close enough to" +
                    "requested value", focusDistance,
                    hyperFocalDistance * (1.0f - errorMargin),
                    hyperFocalDistance * (1.0f + errorMargin));
        }
    }

    /**
     * Verify focus distance control.
     *
     * @param distance The focus distance requested
     * @param request The capture request to control the manual focus distance
     * @param resultListener The capture listener to recieve capture result callbacks
     * @return the result focus distance
     */
    private float verifyFocusDistanceControl(float distance, CaptureRequest request,
            SimpleCaptureListener resultListener) {
        // Need make sure the result corresponding to the request is back, then check.
        CaptureResult result =
                resultListener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        // Then wait for the lens.state to be stationary.
        waitForResultValue(resultListener, CaptureResult.LENS_STATE,
                CaptureResult.LENS_STATE_STATIONARY, NUM_RESULTS_WAIT_TIMEOUT);
        // Then check the focus distance.
        result = resultListener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        Float resultDistance = getValueNotNull(result, CaptureResult.LENS_FOCUS_DISTANCE);
        if (mStaticInfo.getFocusDistanceCalibrationChecked() ==
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED) {
            // TODO: what's more to test for CALIBRATED devices?
        }

        float minValue = 0;
        float maxValue = mStaticInfo.getMinimumFocusDistanceChecked();
        mCollector.expectInRange("Result focus distance is out of range",
                resultDistance, minValue, maxValue);

        return resultDistance;
    }

    /**
     * Verify edge mode control results.
     */
    private void edgeModesTestByCamera() throws Exception {
        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        byte[] edgeModes = mStaticInfo.getAvailableEdgeModesChecked();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureListener resultListener = new SimpleCaptureListener();
        startPreview(requestBuilder, maxPrevSize, resultListener);

        for (byte mode : edgeModes) {
            requestBuilder.set(CaptureRequest.EDGE_MODE, (int)mode);
            resultListener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), resultListener, mHandler);

            verifyCaptureResultForKey(CaptureResult.EDGE_MODE, (int)mode, resultListener,
                    NUM_FRAMES_VERIFIED);

            // Test that OFF and FAST mode should not slow down the frame rate.
            if (mode == CaptureRequest.EDGE_MODE_OFF ||
                    mode == CaptureRequest.EDGE_MODE_FAST) {
                verifyFpsNotSlowDown(requestBuilder, NUM_FRAMES_VERIFIED);
            }
        }

        stopPreview();
    }

    /**
     * Test color correction controls.
     *
     * <p>Test different color correction modes. For TRANSFORM_MATRIX, only test
     * the unit gain and identity transform.</p>
     */
    private void colorCorrectionTestByCamera() throws Exception {
        CaptureRequest request;
        CaptureResult result;
        Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.
        updatePreviewSurface(maxPreviewSz);
        CaptureRequest.Builder manualRequestBuilder = createRequestForPreview();
        CaptureRequest.Builder previewRequestBuilder = createRequestForPreview();
        SimpleCaptureListener listener = new SimpleCaptureListener();

        startPreview(previewRequestBuilder, maxPreviewSz, listener);

        // Default preview result should give valid color correction metadata.
        result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        validateColorCorrectionResult(result);

        // TRANSFORM_MATRIX mode
        // Only test unit gain and identity transform
        float[] UNIT_GAIN = {1.0f, 1.0f, 1.0f, 1.0f};
        Rational[] IDENTITY_TRANSFORM = {
                ONE_R, ZERO_R, ZERO_R,
                ZERO_R, ONE_R, ZERO_R,
                ZERO_R, ZERO_R, ONE_R
        };
        manualRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, UNIT_GAIN);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, IDENTITY_TRANSFORM);
        request = manualRequestBuilder.build();
        mCamera.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        float[] gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
        Rational[] transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        validateColorCorrectionResult(result);
        mCollector.expectEquals("Color correction gain result/request mismatch",
                CameraTestUtils.toObject(UNIT_GAIN), CameraTestUtils.toObject(gains));
        mCollector.expectEquals("Color correction gain result/request mismatch",
                IDENTITY_TRANSFORM, transform);

        // FAST mode
        manualRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_FAST);
        request = manualRequestBuilder.build();
        mCamera.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        validateColorCorrectionResult(result);

        // HIGH_QUALITY mode
        manualRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_FAST);
        request = manualRequestBuilder.build();
        mCamera.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        validateColorCorrectionResult(result);
    }

    private void validateColorCorrectionResult(CaptureResult result) {
        float[] ZERO_GAINS = {0, 0, 0, 0};
        final int TRANSFORM_SIZE = 9;
        Rational[] zeroTransform = new Rational[TRANSFORM_SIZE];
        Arrays.fill(zeroTransform, ZERO_R);

        float[] resultGain;
        if ((resultGain = mCollector.expectKeyValueNotNull(result,
                CaptureResult.COLOR_CORRECTION_GAINS)) != null) {
            mCollector.expectEquals("Color correction gain size in incorrect",
                    ZERO_GAINS.length, resultGain.length);
            mCollector.expectKeyValueNotEquals(result,
                    CaptureResult.COLOR_CORRECTION_GAINS, ZERO_GAINS);
        }

        Rational[] resultTransform;
        if ((resultTransform = mCollector.expectKeyValueNotNull(result,
                CaptureResult.COLOR_CORRECTION_TRANSFORM)) != null) {
            mCollector.expectEquals("Color correction transform size is incorrect",
                    zeroTransform.length, resultTransform.length);
            mCollector.expectKeyValueNotEquals(result,
                    CaptureResult.COLOR_CORRECTION_TRANSFORM, zeroTransform);
        }
    }

    /**
     * Test flash mode control by AE mode.
     * <p>
     * Only allow AE mode ON or OFF, because other AE mode could run into conflict with
     * flash manual control. This function expects the camera to already have an active
     * repeating request and be sending results to the listener.
     * </p>
     *
     * @param listener The Capture listener that is used to wait for capture result
     * @param aeMode The AE mode for flash to test with
     */
    private void flashTestByAeMode(SimpleCaptureListener listener, int aeMode) throws Exception {
        CaptureRequest request;
        CaptureResult result;
        final int NUM_FLASH_REQUESTS_TESTED = 10;
        CaptureRequest.Builder requestBuilder = createRequestForPreview();

        if (aeMode == CaptureRequest.CONTROL_AE_MODE_ON) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
        } else if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
            changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS, DEFAULT_SENSITIVITY);
        } else {
            throw new IllegalArgumentException("This test only works when AE mode is ON or OFF");
        }

        // For camera that doesn't have flash unit, flash state should always be UNAVAILABLE.
        if (mStaticInfo.getFlashInfoChecked() == false) {
            for (int i = 0; i < NUM_FLASH_REQUESTS_TESTED; i++) {
                result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);
                mCollector.expectEquals("No flash unit available, flash state must be UNAVAILABLE"
                        + "for AE mode " + aeMode, CaptureResult.FLASH_STATE_UNAVAILABLE,
                        result.get(CaptureResult.FLASH_STATE));
            }

            return;
        }

        // Test flash SINGLE mode control. Wait for flash state to be READY first.
        waitForResultValue(listener, CaptureResult.FLASH_STATE, CaptureResult.FLASH_STATE_READY,
                NUM_RESULTS_WAIT_TIMEOUT);
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
        request = requestBuilder.build();
        mCamera.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request,
                NUM_RESULTS_WAIT_TIMEOUT);
        // Result mode must be SINGLE, state must be FIRED.
        mCollector.expectEquals("Flash mode result must be SINGLE",
                CaptureResult.FLASH_MODE_SINGLE, result.get(CaptureResult.FLASH_MODE));
        mCollector.expectEquals("Flash state result must be FIRED",
                CaptureResult.FLASH_STATE_FIRED, result.get(CaptureResult.FLASH_STATE));

        // Test flash TORCH mode control.
        CaptureRequest[] requests = new CaptureRequest[NUM_FLASH_REQUESTS_TESTED];
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        for (int i = 0; i < NUM_FLASH_REQUESTS_TESTED; i++) {
            requests[i] = requestBuilder.build();
            mCamera.capture(requests[i], listener, mHandler);
        }
        // Verify the results
        for (int i = 0; i < NUM_FLASH_REQUESTS_TESTED; i++) {
            result = listener.getCaptureResultForRequest(requests[i],
                    NUM_RESULTS_WAIT_TIMEOUT);

            // Result mode must be TORCH, state must be FIRED
            mCollector.expectEquals("Flash mode result must be TORCH",
                    CaptureResult.FLASH_MODE_TORCH, result.get(CaptureResult.FLASH_MODE));
            mCollector.expectEquals("Flash state result must be FIRED",
                    CaptureResult.FLASH_STATE_FIRED, result.get(CaptureResult.FLASH_STATE));
        }

        // Test flash OFF mode control
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        request = requestBuilder.build();
        mCamera.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request,
                NUM_RESULTS_WAIT_TIMEOUT);
        mCollector.expectEquals("Flash mode result must be OFF", CaptureResult.FLASH_MODE_OFF,
                result.get(CaptureResult.FLASH_MODE));
    }

    private void verifyAntiBandingMode(SimpleCaptureListener listener, int numFramesVerified,
            int mode, boolean isAeManual, long requestExpTime) throws Exception {
        // Skip the first a couple of frames as antibanding may not be fully up yet.
        final int NUM_FRAMES_SKIPPED = 5;
        for (int i = 0; i < NUM_FRAMES_SKIPPED; i++) {
            listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        }

        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            Long resultExpTime = result.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            assertNotNull("Exposure time shouldn't be null", resultExpTime);
            Integer flicker = result.get(CaptureResult.STATISTICS_SCENE_FLICKER);
            // Scene flicker result should be always available.
            assertNotNull("Scene flicker must not be null", flicker);
            assertTrue("Scene flicker is invalid", flicker >= STATISTICS_SCENE_FLICKER_NONE &&
                    flicker <= STATISTICS_SCENE_FLICKER_60HZ);

            if (isAeManual) {
                // First, round down not up, second, need close enough.
                validateExposureTime(requestExpTime, resultExpTime);
                return;
            }

            long expectedExpTime = resultExpTime; // Default, no exposure adjustment.
            if (mode == CONTROL_AE_ANTIBANDING_MODE_50HZ) {
                // result exposure time must be adjusted by 50Hz illuminant source.
                expectedExpTime =
                        getAntiFlickeringExposureTime(ANTI_FLICKERING_50HZ, resultExpTime);
            } else if (mode == CONTROL_AE_ANTIBANDING_MODE_60HZ) {
                // result exposure time must be adjusted by 60Hz illuminant source.
                expectedExpTime =
                        getAntiFlickeringExposureTime(ANTI_FLICKERING_60HZ, resultExpTime);
            } else if (mode == CONTROL_AE_ANTIBANDING_MODE_AUTO){
                /**
                 * Use STATISTICS_SCENE_FLICKER to tell the illuminant source
                 * and do the exposure adjustment.
                 */
                expectedExpTime = resultExpTime;
                if (flicker == STATISTICS_SCENE_FLICKER_60HZ) {
                    expectedExpTime =
                            getAntiFlickeringExposureTime(ANTI_FLICKERING_60HZ, resultExpTime);
                } else if (flicker == STATISTICS_SCENE_FLICKER_50HZ) {
                    expectedExpTime =
                            getAntiFlickeringExposureTime(ANTI_FLICKERING_50HZ, resultExpTime);
                }
            }

            if (Math.abs(resultExpTime - expectedExpTime) > EXPOSURE_TIME_ERROR_MARGIN_NS) {
                mCollector.addMessage(String.format("Result exposure time %dns diverges too much"
                        + " from expected exposure time %dns for mode %d when AE is auto",
                        resultExpTime, expectedExpTime, mode));
            }
        }
    }

    private void antiBandingTestByMode(Size size, int mode)
            throws Exception {
        if(VERBOSE) {
            Log.v(TAG, "Anti-banding test for mode " + mode + " for camera " + mCamera.getId());
        }
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        requestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, mode);

        // Test auto AE mode anti-banding behavior
        SimpleCaptureListener resultListener = new SimpleCaptureListener();
        startPreview(requestBuilder, size, resultListener);
        verifyAntiBandingMode(resultListener, NUM_FRAMES_VERIFIED, mode, /*isAeManual*/false,
                IGORE_REQUESTED_EXPOSURE_TIME_CHECK);

        // Test manual AE mode anti-banding behavior
        // 65ms, must be supported by full capability devices.
        final long TEST_MANUAL_EXP_TIME_NS = 65000000L;
        long manualExpTime = mStaticInfo.getExposureClampToRange(TEST_MANUAL_EXP_TIME_NS);
        changeExposure(requestBuilder, manualExpTime);
        resultListener = new SimpleCaptureListener();
        startPreview(requestBuilder, size, resultListener);
        verifyAntiBandingMode(resultListener, NUM_FRAMES_VERIFIED, mode, /*isAeManual*/true,
                manualExpTime);

        stopPreview();
    }

    /**
     * Test the all available AE modes and AE lock.
     * <p>
     * For manual AE mode, test iterates through different sensitivities and
     * exposure times, validate the result exposure time correctness. For
     * CONTROL_AE_MODE_ON_ALWAYS_FLASH mode, the AE lock and flash are tested.
     * For the rest of the AUTO mode, AE lock is tested.
     * </p>
     *
     * @param mode
     */
    private void aeModeAndLockTestByMode(int mode)
            throws Exception {
        switch (mode) {
            case CONTROL_AE_MODE_OFF:
                // Test manual exposure control.
                aeManualControlTest();
                break;
            case CONTROL_AE_MODE_ON:
            case CONTROL_AE_MODE_ON_AUTO_FLASH:
            case CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
            case CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                // Test AE lock for above AUTO modes.
                aeAutoModeTestLock(mode);
                break;
            default:
                throw new UnsupportedOperationException("Unhandled AE mode " + mode);
        }
    }

    /**
     * Test AE auto modes.
     * <p>
     * Use single request rather than repeating request to test AE lock per frame control.
     * </p>
     */
    private void aeAutoModeTestLock(int mode) throws Exception {
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, mode);
        configurePreviewOutput(requestBuilder);

        final int MAX_NUM_CAPTURES_BEFORE_LOCK = 5;
        for (int i = 1; i <= MAX_NUM_CAPTURES_BEFORE_LOCK; i++) {
            autoAeMultipleCapturesThenTestLock(requestBuilder, mode, i);
        }
    }

    /**
     * Issue multiple auto AE captures, then lock AE, validate the AE lock vs.
     * the last capture result before the AE lock.
     */
    private void autoAeMultipleCapturesThenTestLock(
            CaptureRequest.Builder requestBuilder, int aeMode, int numCapturesBeforeLock)
            throws Exception {
        if (numCapturesBeforeLock < 1) {
            throw new IllegalArgumentException("numCapturesBeforeLock must be no less than 1");
        }
        if (VERBOSE) {
            Log.v(TAG, "Camera " + mCamera.getId() + ": Testing auto AE mode and lock for mode "
                    + aeMode + " with " + numCapturesBeforeLock + " captures before lock");
        }

        SimpleCaptureListener listener =  new SimpleCaptureListener();
        CaptureResult latestResult = null;

        CaptureRequest request = requestBuilder.build();
        for (int i = 0; i < numCapturesBeforeLock; i++) {
            // Fire a capture, auto AE, lock off.
            mCamera.capture(request, listener, mHandler);
        }
        // Then fire a capture to lock the AE,
        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        mCamera.capture(requestBuilder.build(), listener, mHandler);

        // Get the latest exposure values of the last AE lock off requests.
        for (int i = 0; i < numCapturesBeforeLock; i++) {
            latestResult = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        }
        int sensitivity = getValueNotNull(latestResult, CaptureResult.SENSOR_SENSITIVITY);
        long expTime = getValueNotNull(latestResult, CaptureResult.SENSOR_EXPOSURE_TIME);

        // Get the AE lock on result and validate the exposure values.
        latestResult = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        int sensitivityLocked = getValueNotNull(latestResult, CaptureResult.SENSOR_SENSITIVITY);
        long expTimeLocked = getValueNotNull(latestResult, CaptureResult.SENSOR_EXPOSURE_TIME);
        mCollector.expectEquals("Locked exposure time shouldn't be changed for AE auto mode "
                + aeMode + "after " + numCapturesBeforeLock + " captures", expTime, expTimeLocked);
        mCollector.expectEquals("Locked sensitivity shouldn't be changed for AE auto mode " + aeMode
                + "after " + numCapturesBeforeLock + " captures", sensitivity, sensitivityLocked);
    }

    /**
     * Iterate through exposure times and sensitivities for manual AE control.
     * <p>
     * Use single request rather than repeating request to test manual exposure
     * value change per frame control.
     * </p>
     */
    private void aeManualControlTest()
            throws Exception {
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
        configurePreviewOutput(requestBuilder);
        SimpleCaptureListener listener =  new SimpleCaptureListener();

        long[] expTimes = getExposureTimeTestValues();
        int[] sensitivities = getSensitivityTestValues();
        // Submit single request at a time, then verify the result.
        for (int i = 0; i < expTimes.length; i++) {
            for (int j = 0; j < sensitivities.length; j++) {
                if (VERBOSE) {
                    Log.v(TAG, "Camera " + mCamera.getId() + ": Testing sensitivity "
                            + sensitivities[j] + ", exposure time " + expTimes[i] + "ns");
                }

                changeExposure(requestBuilder, expTimes[i], sensitivities[j]);
                mCamera.capture(requestBuilder.build(), listener, mHandler);

                CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
                long resultExpTime = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
                int resultSensitivity = getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);
                validateExposureTime(expTimes[i], resultExpTime);
                validateSensitivity(sensitivities[j], resultSensitivity);
                validateFrameDurationForCapture(result);
            }
        }
        // TODO: Add another case to test where we can submit all requests, then wait for
        // results, which will hide the pipeline latency. this is not only faster, but also
        // test high speed per frame control and synchronization.
    }


    /**
     * Verify black level lock control.
     */
    private void verifyBlackLevelLockResults(SimpleCaptureListener listener, int numFramesVerified,
            int maxLockOffCnt) throws Exception {
        int noLockCnt = 0;
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            Boolean blackLevelLock = result.get(CaptureResult.BLACK_LEVEL_LOCK);
            assertNotNull("Black level lock result shouldn't be null", blackLevelLock);

            // Count the lock == false result, which could possibly occur at most once.
            if (blackLevelLock == false) {
                noLockCnt++;
            }

            if(VERBOSE) {
                Log.v(TAG, "Black level lock result: " + blackLevelLock);
            }
        }
        assertTrue("Black level lock OFF occurs " + noLockCnt + " times,  expect at most "
                + maxLockOffCnt + " for camera " + mCamera.getId(), noLockCnt <= maxLockOffCnt);
    }

    /**
     * Verify shading map for different shading modes.
     */
    private void verifyShadingMap(SimpleCaptureListener listener, int numFramesVerified,
            Size mapSize, int shadingMode) throws Exception {
        int numElementsInMap = mapSize.getWidth() * mapSize.getHeight() * RGGB_COLOR_CHANNEL_COUNT;
        float[] unityMap = new float[numElementsInMap];
        Arrays.fill(unityMap, 1.0f);

        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            mCollector.expectEquals("Shading mode result doesn't match request",
                    shadingMode, result.get(CaptureResult.SHADING_MODE));
            float[] map = result.get(CaptureResult.STATISTICS_LENS_SHADING_MAP);
            assertNotNull("Map must not be null", map);
            assertTrue("Map size " + map.length + " must be " + numElementsInMap,
                    map.length == numElementsInMap);
            assertFalse(String.format(
                    "Map size %d should be less than %d", numElementsInMap, MAX_SHADING_MAP_SIZE),
                    numElementsInMap >= MAX_SHADING_MAP_SIZE);
            assertFalse(String.format("Map size %d should be no less than %d", numElementsInMap,
                    MIN_SHADING_MAP_SIZE), numElementsInMap < MIN_SHADING_MAP_SIZE);

            if (shadingMode == CaptureRequest.SHADING_MODE_FAST ||
                    shadingMode == CaptureRequest.SHADING_MODE_HIGH_QUALITY) {
                // shading mode is FAST or HIGH_QUALITY, expect to receive a map with all
                // elements >= 1.0f

                int badValueCnt = 0;
                // Detect the bad values of the map data.
                for (int j = 0; j < numElementsInMap; j++) {
                    if (Float.isNaN(map[j]) || map[j] < 1.0f) {
                        badValueCnt++;
                    }
                }
                assertEquals("Number of value in the map is " + badValueCnt + " out of "
                        + numElementsInMap, /*expected*/0, /*actual*/badValueCnt);
            } else if (shadingMode == CaptureRequest.SHADING_MODE_OFF) {
                // shading mode is OFF, expect to receive a unity map.
                assertTrue("Result map " + Arrays.toString(map) + " must be an unity map",
                        Arrays.equals(unityMap, map));
            }
        }
    }

    /**
     * Test face detection for a camera.
     */
    private void faceDetectionTestByCamera() throws Exception {
        // Can only test full capability because test relies on per frame control
        // and synchronization.
        if (!mStaticInfo.isHardwareLevelFull()) {
            return;
        }
        byte[] faceDetectModes = mStaticInfo.getAvailableFaceDetectModesChecked();

        SimpleCaptureListener listener;
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.
        for (byte mode : faceDetectModes) {
            requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, (int)mode);
            if (VERBOSE) {
                Log.v(TAG, "Start testing face detection mode " + mode);
            }

            // Create a new listener for each run to avoid the results from one run spill
            // into another run.
            listener = new SimpleCaptureListener();
            startPreview(requestBuilder, maxPreviewSz, listener);
            verifyFaceDetectionResults(listener, NUM_FACE_DETECTION_FRAMES_VERIFIED, mode);
        }

        stopPreview();
    }

    /**
     * Verify face detection results for different face detection modes.
     *
     * @param listener The listener to get capture result
     * @param numFramesVerified Number of results to be verified
     * @param faceDetectionMode Face detection mode to be verified against
     */
    private void verifyFaceDetectionResults(SimpleCaptureListener listener, int numFramesVerified,
            int faceDetectionMode) {
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            mCollector.expectEquals("Result face detection mode should match the request",
                    faceDetectionMode, result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE));

            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            List<Integer> faceIds = new ArrayList<Integer>(faces.length);
            List<Integer> faceScores = new ArrayList<Integer>(faces.length);
            if (faceDetectionMode == CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF) {
                mCollector.expectEquals("Number of detection faces should always 0 for OFF mode",
                        0, faces.length);
            } else if (faceDetectionMode == CaptureResult.STATISTICS_FACE_DETECT_MODE_SIMPLE) {
                for (Face face : faces) {
                    mCollector.expectNotNull("Face rectangle shouldn't be null", face.getBounds());
                    faceScores.add(face.getScore());
                    mCollector.expectTrue("Face id is expected to be -1 for SIMPLE mode",
                            face.getId() == Face.ID_UNSUPPORTED);
                }
            } else if (faceDetectionMode == CaptureResult.STATISTICS_FACE_DETECT_MODE_FULL) {
                if (VERBOSE) {
                    Log.v(TAG, "Number of faces detected: " + faces.length);
                }

                for (Face face : faces) {
                    Rect faceBound = null;
                    boolean faceRectAvailable =  mCollector.expectTrue("Face rectangle "
                            + "shouldn't be null", face.getBounds() != null);
                    if (!faceRectAvailable) {
                        continue;
                    }
                    faceBound = face.getBounds();

                    faceScores.add(face.getScore());
                    faceIds.add(face.getId());

                    mCollector.expectTrue("Face id is shouldn't be -1 for FULL mode",
                            face.getId() != Face.ID_UNSUPPORTED);
                    boolean leftEyeAvailable =
                            mCollector.expectTrue("Left eye position shouldn't be null",
                                    face.getLeftEyePosition() != null);
                    boolean rightEyeAvailable =
                            mCollector.expectTrue("Right eye position shouldn't be null",
                                    face.getRightEyePosition() != null);
                    boolean mouthAvailable =
                            mCollector.expectTrue("Mouth position shouldn't be null",
                            face.getMouthPosition() != null);
                    // Eyes/mouth position should be inside of the face rect.
                    if (leftEyeAvailable) {
                        Point leftEye = face.getLeftEyePosition();
                        mCollector.expectTrue("Left eye " + leftEye.toString() + "should be"
                                + "inside of face rect " + faceBound.toString(),
                                faceBound.contains(leftEye.x, leftEye.y));
                    }
                    if (rightEyeAvailable) {
                        Point rightEye = face.getRightEyePosition();
                        mCollector.expectTrue("Right eye " + rightEye.toString() + "should be"
                                + "inside of face rect " + faceBound.toString(),
                                faceBound.contains(rightEye.x, rightEye.y));
                    }
                    if (mouthAvailable) {
                        Point mouth = face.getMouthPosition();
                        mCollector.expectTrue("Mouth " + mouth.toString() +  " should be inside of"
                                + " face rect " + faceBound.toString(),
                                faceBound.contains(mouth.x, mouth.y));
                    }
                }
            }
            mCollector.expectValuesInRange("Face scores are invalid", faceIds,
                    Face.SCORE_MIN, Face.SCORE_MAX);
            mCollector.expectValuesUnique("Face ids are invalid", faceIds);
        }
    }

    /**
     * Test tone map mode and result by camera
     */
    private void toneMapTestByCamera() throws Exception {
        // Can only test full capability because test relies on per frame control
        // and synchronization.
        if (!mStaticInfo.isHardwareLevelFull()) {
            return;
        }

        SimpleCaptureListener listener;
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.

        byte[] toneMapModes = mStaticInfo.getAvailableToneMapModesChecked();
        for (byte mode : toneMapModes) {
            requestBuilder.set(CaptureRequest.TONEMAP_MODE, (int)mode);
            if (VERBOSE) {
                Log.v(TAG, "Testing tonemap mode " + mode);
            }

            if (mode == CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE) {
                requestBuilder.set(CaptureRequest.TONEMAP_CURVE_RED, TONEMAP_CURVE_LINEAR);
                requestBuilder.set(CaptureRequest.TONEMAP_CURVE_GREEN, TONEMAP_CURVE_LINEAR);
                requestBuilder.set(CaptureRequest.TONEMAP_CURVE_BLUE, TONEMAP_CURVE_LINEAR);
                // Create a new listener for each run to avoid the results from one run spill
                // into another run.
                listener = new SimpleCaptureListener();
                startPreview(requestBuilder, maxPreviewSz, listener);
                verifyToneMapModeResults(listener, NUM_FRAMES_VERIFIED, mode,
                        TONEMAP_CURVE_LINEAR);

                requestBuilder.set(CaptureRequest.TONEMAP_CURVE_RED, TONEMAP_CURVE_SRGB);
                requestBuilder.set(CaptureRequest.TONEMAP_CURVE_GREEN, TONEMAP_CURVE_SRGB);
                requestBuilder.set(CaptureRequest.TONEMAP_CURVE_BLUE, TONEMAP_CURVE_SRGB);
                // Create a new listener for each run to avoid the results from one run spill
                // into another run.
                listener = new SimpleCaptureListener();
                startPreview(requestBuilder, maxPreviewSz, listener);
                verifyToneMapModeResults(listener, NUM_FRAMES_VERIFIED, mode,
                        TONEMAP_CURVE_SRGB);
            } else {
                // Create a new listener for each run to avoid the results from one run spill
                // into another run.
                listener = new SimpleCaptureListener();
                startPreview(requestBuilder, maxPreviewSz, listener);
                verifyToneMapModeResults(listener, NUM_FRAMES_VERIFIED, mode,
                        /*inputToneCurve*/null);
            }
        }

        stopPreview();
    }

    /**
     * Verify tonemap results.
     * <p>
     * Assumes R,G,B channels use the same tone curve
     * </p>
     *
     * @param listener The capture listener used to get the capture results
     * @param numFramesVerified Number of results to be verified
     * @param tonemapMode Tonemap mode to verify
     * @param inputToneCurve Tonemap curve used by all 3 channels, ignored when
     * map mode is not CONTRAST_CURVE.
     */
    private void verifyToneMapModeResults(SimpleCaptureListener listener, int numFramesVerified,
            int tonemapMode, float[] inputToneCurve) {
        final int MIN_TONEMAP_CURVE_POINTS = 2;
        final Float ZERO = new Float(0);
        final Float ONE = new Float(1.0f);

        int maxCurvePoints = mStaticInfo.getMaxTonemapCurvePointChecked();
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            mCollector.expectEquals("Capture result tonemap mode should match request", tonemapMode,
                    result.get(CaptureRequest.TONEMAP_MODE));
            float[] mapRed = result.get(CaptureResult.TONEMAP_CURVE_RED);
            float[] mapGreen = result.get(CaptureResult.TONEMAP_CURVE_GREEN);
            float[] mapBlue = result.get(CaptureResult.TONEMAP_CURVE_BLUE);
            boolean redAvailable =
                    mCollector.expectTrue("Tonemap curve red shouldn't be null for mode "
                            + tonemapMode, mapRed != null);
            boolean greenAvailable =
                    mCollector.expectTrue("Tonemap curve red shouldn't be null for mode "
                            + tonemapMode, mapGreen != null);
            boolean blueAvailable =
                    mCollector.expectTrue("Tonemap curve red shouldn't be null for mode "
                            + tonemapMode, mapBlue != null);
            if (tonemapMode == CaptureResult.TONEMAP_MODE_CONTRAST_CURVE) {
                /**
                 * TODO: need figure out a good way to measure the difference
                 * between request and result, as they may have different array
                 * size.
                 */
            }

            // Tonemap curve result availability and basic sanity check for all modes.
            if (redAvailable) {
                mCollector.expectValuesInRange("Tonemap curve red values are out of range",
                        CameraTestUtils.toObject(mapRed), /*min*/ZERO, /*max*/ONE);
                mCollector.expectInRange("Tonemap curve red length is out of range",
                        mapRed.length, MIN_TONEMAP_CURVE_POINTS, maxCurvePoints * 2);
            }
            if (greenAvailable) {
                mCollector.expectValuesInRange("Tonemap curve green values are out of range",
                        CameraTestUtils.toObject(mapGreen), /*min*/ZERO, /*max*/ONE);
                mCollector.expectInRange("Tonemap curve green length is out of range",
                        mapGreen.length, MIN_TONEMAP_CURVE_POINTS, maxCurvePoints * 2);
            }
            if (blueAvailable) {
                mCollector.expectValuesInRange("Tonemap curve blue values are out of range",
                        CameraTestUtils.toObject(mapBlue), /*min*/ZERO, /*max*/ONE);
                mCollector.expectInRange("Tonemap curve blue length is out of range",
                        mapBlue.length, MIN_TONEMAP_CURVE_POINTS, maxCurvePoints * 2);
            }
        }
    }

    /**
     * Test awb mode control.
     * <p>
     * Test each supported AWB mode, verify the AWB mode in capture result
     * matches request. When AWB is locked, the color correction gains and
     * transform should remain unchanged.
     * </p>
     */
    private void awbModeAndLockTestByCamera() throws Exception {
        byte[] awbModes = mStaticInfo.getAwbAvailableModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        startPreview(requestBuilder, maxPreviewSize, /*listener*/null);

        for (byte mode : awbModes) {
            SimpleCaptureListener listener;
            requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, (int)mode);
            listener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);

            // Verify AWB mode in capture result.
            verifyCaptureResultForKey(CaptureResult.CONTROL_AWB_MODE, (int)mode, listener,
                    NUM_FRAMES_VERIFIED);

            // Verify color correction transform and gains stay unchanged after a lock.
            requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
            listener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForResultValue(listener, CaptureResult.CONTROL_AWB_STATE,
                    CaptureResult.CONTROL_AWB_STATE_LOCKED, NUM_RESULTS_WAIT_TIMEOUT);
            verifyAwbCaptureResultUnchanged(listener, NUM_FRAMES_VERIFIED);
        }
    }

    private void verifyAwbCaptureResultUnchanged(SimpleCaptureListener listener,
            int numFramesVerified) {
        CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        float[] lockedGains = getValueNotNull(result, CaptureResult.COLOR_CORRECTION_GAINS);
        Rational[] lockedTransform =
                getValueNotNull(result, CaptureResult.COLOR_CORRECTION_TRANSFORM);

        for (int i = 0; i < numFramesVerified; i++) {
            result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            validateColorCorrectionResult(result);

            float[] gains = getValueNotNull(result, CaptureResult.COLOR_CORRECTION_GAINS);
            Rational[] transform =
                    getValueNotNull(result, CaptureResult.COLOR_CORRECTION_TRANSFORM);
            mCollector.expectEquals("Color correction gains should remain unchanged after awb lock",
                    toObject(lockedGains), toObject(gains));
            mCollector.expectEquals("Color correction transform should remain unchanged after"
                    + " awb lock", lockedTransform, transform);
        }
    }

    /**
     * Test AF mode control.
     * <p>
     * Test all supported AF modes, verify the AF mode in capture result matches
     * request. When AF mode is one of the CONTROL_AF_MODE_CONTINUOUS_* mode,
     * verify if the AF can converge to PASSIVE_FOCUSED or PASSIVE_UNFOCUSED
     * state within certain amount of frames.
     * </p>
     */
    private void afModeTestByCamera() throws Exception {
        byte[] afModes = mStaticInfo.getAfAvailableModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        startPreview(requestBuilder, maxPreviewSize, /*listener*/null);

        for (byte mode : afModes) {
            SimpleCaptureListener listener;
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, (int)mode);
            listener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);

            // Verify AF mode in capture result.
            verifyCaptureResultForKey(CaptureResult.CONTROL_AF_MODE, (int)mode, listener,
                    NUM_FRAMES_VERIFIED);

            // Verify AF can finish a scan for CONTROL_AF_MODE_CONTINUOUS_* modes
            if ((int)mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                    (int)mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                List<Integer> afStateList = new ArrayList<Integer>();
                afStateList.add(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED);
                afStateList.add(CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED);
                waitForAnyResultValue(listener, CaptureResult.CONTROL_AF_STATE, afStateList,
                        NUM_RESULTS_WAIT_TIMEOUT);
            }
        }
    }

    /**
     * Test video and optical stabilizations if they are supported by a given camera.
     */
    private void stabilizationTestByCamera() throws Exception {
        // video stabilization test.
        byte[] videoStabModes = mStaticInfo.getAvailableVideoStabilizationModesChecked();
        byte[] opticalStabModes = mStaticInfo.getAvailableOpticalStabilizationChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureListener listener = new SimpleCaptureListener();
        startPreview(requestBuilder, maxPreviewSize, listener);

        for ( byte mode : videoStabModes) {
            listener = new SimpleCaptureListener();
            requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, (int) mode);
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            // TODO: enable below code when b/14059883 is fixed.
            /*
            verifyCaptureResultForKey(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE, (int)mode,
                    listener, NUM_FRAMES_VERIFIED);
            */
        }

        for (int mode : opticalStabModes) {
            listener = new SimpleCaptureListener();
            requestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, (int) mode);
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            verifyCaptureResultForKey(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE, (int)mode,
                    listener, NUM_FRAMES_VERIFIED);
        }

        stopPreview();
    }

    private void digitalZoomTestByCamera() throws Exception {
        final int ZOOM_STEPS = 30;
        final PointF[] TEST_ZOOM_CENTERS = new PointF[] {
                new PointF(0.5f, 0.5f),   // Center point
                new PointF(0.25f, 0.25f), // top left corner zoom, minimal zoom: 2x
                new PointF(0.75f, 0.25f), // top right corner zoom, minimal zoom: 2x
                new PointF(0.25f, 0.75f), // bottom left corner zoom, minimal zoom: 2x
                new PointF(0.75f, 0.75f), // bottom right corner zoom, minimal zoom: 2x
        };
        final float maxZoom = mStaticInfo.getAvailableMaxDigitalZoomChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureListener listener = new SimpleCaptureListener();
        startPreview(requestBuilder, maxPreviewSize, listener);
        CaptureRequest[] requests = new CaptureRequest[ZOOM_STEPS];
        Rect[] cropRegions = new Rect[ZOOM_STEPS];

        for (PointF center : TEST_ZOOM_CENTERS) {
            for (int i = 0; i < ZOOM_STEPS; i++) {
                float zoomFactor = (float) (1.0f + (maxZoom - 1.0) * i / ZOOM_STEPS);
                cropRegions[i] = getCropRegionForZoom(zoomFactor, center,
                        mStaticInfo.getAvailableMaxDigitalZoomChecked(),
                        mStaticInfo.getActiveArraySizeChecked());
                if (VERBOSE) {
                    Log.v(TAG, "Testing Zoom for factor " + zoomFactor + " and center " +
                            center.toString() + " The cropRegion is " + cropRegions[i].toString());
                }
                requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegions[i]);
                requests[i] = requestBuilder.build();
                mCamera.capture(requests[i], listener, mHandler);
            }

            // Validate capture result
            CaptureResult result;
            for (int i = 0; i < ZOOM_STEPS; i++) {
                 result = listener.getCaptureResultForRequest(
                         requests[i], NUM_RESULTS_WAIT_TIMEOUT);
                 Rect cropRegion = getValueNotNull(result, CaptureResult.SCALER_CROP_REGION);
                 mCollector.expectEquals(" Request and result crop region should match",
                         cropRegions[i], cropRegion);
            }
        }

        stopPreview();
    }

    private void sceneModeTestByCamera() throws Exception {
        byte[] sceneModes = mStaticInfo.getAvailableSceneModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureListener listener = new SimpleCaptureListener();
        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        startPreview(requestBuilder, maxPreviewSize, listener);

        for(byte mode : sceneModes) {
            requestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, (int)mode);
            listener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            // Enable below check  when b/14059883 is fixed.
            /*
            verifyCaptureResultForKey(CaptureResult.CONTROL_SCENE_MODE,
                    CaptureRequest.CONTROL_SCENE_MODE, listener, NUM_FRAMES_VERIFIED);
            */
            // This also serves as purpose of showing preview for NUM_FRAMES_VERIFIED
            verifyCaptureResultForKey(CaptureResult.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_USE_SCENE_MODE, listener, NUM_FRAMES_VERIFIED);
        }
    }

    private void effectModeTestByCamera() throws Exception {
        byte[] effectModes = mStaticInfo.getAvailableEffectModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        SimpleCaptureListener listener = new SimpleCaptureListener();
        startPreview(requestBuilder, maxPreviewSize, listener);

        for(byte mode : effectModes) {
            requestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, (int)mode);
            listener = new SimpleCaptureListener();
            mCamera.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            // Enable below check  when b/14059883 is fixed.
            /*
            verifyCaptureResultForKey(CaptureResult.CONTROL_SCENE_MODE,
                    CaptureRequest.CONTROL_SCENE_MODE, listener, NUM_FRAMES_VERIFIED);
            */
            // This also serves as purpose of showing preview for NUM_FRAMES_VERIFIED
            verifyCaptureResultForKey(CaptureResult.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO, listener, NUM_FRAMES_VERIFIED);
        }
    }

    //----------------------------------------------------------------
    //---------Below are common functions for all tests.--------------
    //----------------------------------------------------------------

    /**
     * Enable exposure manual control and change exposure and sensitivity and
     * clamp the value into the supported range.
     */
    private void changeExposure(CaptureRequest.Builder requestBuilder,
            long expTime, int sensitivity) {
        // Check if the max analog sensitivity is available and no larger than max sensitivity.
        // The max analog sensitivity is not actually used here. This is only an extra sanity check.
        mStaticInfo.getMaxAnalogSensitivityChecked();

        expTime = mStaticInfo.getExposureClampToRange(expTime);
        sensitivity = mStaticInfo.getSensitivityClampToRange(sensitivity);

        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expTime);
        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
    }
    /**
     * Enable exposure manual control and change exposure time and
     * clamp the value into the supported range.
     *
     * <p>The sensitivity is set to default value.</p>
     */
    private void changeExposure(CaptureRequest.Builder requestBuilder, long expTime) {
        changeExposure(requestBuilder, expTime, DEFAULT_SENSITIVITY);
    }

    /**
     * Enable exposure manual control and change sensitivity and
     * clamp the value into the supported range.
     *
     * <p>The exposure time is set to default value.</p>
     */
    private void changeExposure(CaptureRequest.Builder requestBuilder, int sensitivity) {
        changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS, sensitivity);
    }

    /**
     * Get the exposure time array that contains multiple exposure time steps in
     * the exposure time range.
     */
    private long[] getExposureTimeTestValues() {
        long[] testValues = new long[DEFAULT_NUM_EXPOSURE_TIME_STEPS + 1];
        long maxExpTime = mStaticInfo.getExposureMaximumOrDefault(DEFAULT_EXP_TIME_NS);
        long minxExpTime = mStaticInfo.getExposureMinimumOrDefault(DEFAULT_EXP_TIME_NS);

        long range = maxExpTime - minxExpTime;
        double stepSize = range / (double)DEFAULT_NUM_EXPOSURE_TIME_STEPS;
        for (int i = 0; i < testValues.length; i++) {
            testValues[i] = minxExpTime + (long)(stepSize * i);
            testValues[i] = mStaticInfo.getExposureClampToRange(testValues[i]);
        }

        return testValues;
    }

    /**
     * Generate test focus distances in range of [0, minFocusDistance] in increasing order.
     */
    private float[] getFocusDistanceTestValuesInOrder() {
        float[] testValues = new float[NUM_TEST_FOCUS_DISTANCES + 1];
        float minValue = 0;
        float maxValue = mStaticInfo.getMinimumFocusDistanceChecked();

        float range = maxValue - minValue;
        float stepSize = range / NUM_TEST_FOCUS_DISTANCES;
        for (int i = 0; i < testValues.length; i++) {
            testValues[i] = minValue + stepSize * i;
        }

        return testValues;
    }

    /**
     * Get the sensitivity array that contains multiple sensitivity steps in the
     * sensitivity range.
     * <p>
     * Sensitivity number of test values is determined by
     * {@value #DEFAULT_SENSITIVITY_STEP_SIZE} and sensitivity range, and
     * bounded by {@value #DEFAULT_NUM_SENSITIVITY_STEPS}.
     * </p>
     */
    private int[] getSensitivityTestValues() {
        int maxSensitivity = mStaticInfo.getSensitivityMaximumOrDefault(
                DEFAULT_SENSITIVITY);
        int minSensitivity = mStaticInfo.getSensitivityMinimumOrDefault(
                DEFAULT_SENSITIVITY);

        int range = maxSensitivity - minSensitivity;
        int stepSize = DEFAULT_SENSITIVITY_STEP_SIZE;
        int numSteps = range / stepSize;
        // Bound the test steps to avoid supper long test.
        if (numSteps > DEFAULT_NUM_SENSITIVITY_STEPS) {
            numSteps = DEFAULT_NUM_SENSITIVITY_STEPS;
            stepSize = range / numSteps;
        }
        int[] testValues = new int[numSteps + 1];
        for (int i = 0; i < testValues.length; i++) {
            testValues[i] = minSensitivity + stepSize * i;
            testValues[i] = mStaticInfo.getSensitivityClampToRange(testValues[i]);
        }

        return testValues;
    }

    /**
     * Validate the AE manual control exposure time.
     *
     * <p>Exposure should be close enough, and only round down if they are not equal.</p>
     *
     * @param request Request exposure time
     * @param result Result exposure time
     */
    private void validateExposureTime(long request, long result) {
        long expTimeDelta = request - result;
        // First, round down not up, second, need close enough.
        mCollector.expectTrue("Exposture time is invalid for AE manaul control test, request: "
                + request + " result: " + result,
                expTimeDelta < EXPOSURE_TIME_ERROR_MARGIN_NS && expTimeDelta >= 0);
    }

    /**
     * Validate AE manual control sensitivity.
     *
     * @param request Request sensitivity
     * @param result Result sensitivity
     */
    private void validateSensitivity(int request, int result) {
        int sensitivityDelta = request - result;
        // First, round down not up, second, need close enough.
        mCollector.expectTrue("Sensitivity is invalid for AE manaul control test, request: "
                + request + " result: " + result,
                sensitivityDelta < SENSITIVITY_ERROR_MARGIN && sensitivityDelta >= 0);
    }

    /**
     * Validate frame duration for a given capture.
     *
     * <p>Frame duration should be longer than exposure time.</p>
     *
     * @param result The capture result for a given capture
     */
    private void validateFrameDurationForCapture(CaptureResult result) {
        long expTime = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
        long frameDuration = getValueNotNull(result, CaptureResult.SENSOR_FRAME_DURATION);
        if (VERBOSE) {
            Log.v(TAG, "frame duration: " + frameDuration + " Exposure time: " + expTime);
        }

        mCollector.expectTrue(String.format("Frame duration (%d) should be longer than exposure"
                + " time (%d) for a given capture", frameDuration, expTime),
                frameDuration >= expTime);
    }

    private <T> T getValueNotNull(CaptureResult result, Key<T> key) {
        T value = result.get(key);
        assertNotNull("Value of Key " + key.getName() + " shouldn't be null", value);
        return value;
    }

    /**
     * Basic verification for the control mode capture result.
     *
     * @param key The capture result key to be verified against
     * @param requestMode The request mode for this result
     * @param listener The capture listener to get capture results
     * @param numFramesVerified The number of capture results to be verified
     */
    private <T> void verifyCaptureResultForKey(Key<T> key, T requestMode,
            SimpleCaptureListener listener, int numFramesVerified) {
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            T resultMode = getValueNotNull(result, key);
            if (VERBOSE) {
                Log.v(TAG, "Expect value: " + requestMode.toString() + " result value: "
                        + resultMode.toString());
            }
            mCollector.expectEquals("Key " + key.getName() + " result should match request",
                    requestMode, resultMode);
        }
    }

    /**
     * Verify if the fps is slow down for given input request with certain
     * controls inside.
     * <p>
     * This method selects a max preview size for each fps range, and then
     * configure the preview stream. Preview is started with the max preview
     * size, and then verify if the result frame duration is in the frame
     * duration range.
     * </p>
     *
     * @param requestBuilder The request builder that contains post-processing
     *            controls that could impact the output frame rate, such as
     *            {@link CaptureRequest.NOISE_REDUCTION_MODE}. The value of
     *            these controls must be set to some values such that the frame
     *            rate is not slow down.
     * @param numFramesVerified The number of frames to be verified
     */
    private void verifyFpsNotSlowDown(CaptureRequest.Builder requestBuilder,
            int numFramesVerified)  throws Exception {
        int[] fpsRanges = mStaticInfo.getAeAvailableTargetFpsRangesChecked();
        final int FPS_RANGE_SIZE = 2;
        int[] fpsRange = new int[FPS_RANGE_SIZE];
        SimpleCaptureListener resultListener;

        for (int i = 0; i < fpsRanges.length; i += FPS_RANGE_SIZE) {
            fpsRange[0] = fpsRanges[i];
            fpsRange[1] = fpsRanges[i + 1];
            Size previewSz = getMaxPreviewSizeForFpsRange(fpsRange);
            // If unable to find a preview size, then log the failure, and skip this run.
            if (!mCollector.expectTrue(String.format(
                    "Unable to find a preview size supporting given fps range [%d, %d]",
                    fpsRange[0], fpsRange[1]), previewSz != null)) {
                continue;
            }

            if (VERBOSE) {
                Log.v(TAG, String.format("Test fps range [%d, %d] for preview size %s",
                        fpsRange[0], fpsRange[1], previewSz.toString()));
            }
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            resultListener = new SimpleCaptureListener();
            startPreview(requestBuilder, previewSz, resultListener);
            long[] frameDurationRange =
                    new long[]{(long) (1e9 / fpsRange[1]), (long) (1e9 / fpsRange[0])};
            for (int j = 0; j < numFramesVerified; j++) {
                CaptureResult result =
                        resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
                long frameDuration = getValueNotNull(result, CaptureResult.SENSOR_FRAME_DURATION);
                mCollector.expectInRange(
                        "Frame duration must be in the range of " + Arrays.toString(frameDurationRange),
                        frameDuration,
                        (long) (frameDurationRange[0] * (1 - FRAME_DURATION_ERROR_MARGIN)),
                        (long) (frameDurationRange[1] * (1 + FRAME_DURATION_ERROR_MARGIN)));
            }
        }

        mCamera.stopRepeating();
    }

    /**
     * Calculate the anti-flickering corrected exposure time.
     * <p>
     * If the input exposure time is very short (shorter than flickering
     * boundary), which indicate the scene is bright and very likely at outdoor
     * environment, skip the correction, as it doesn't make much sense by doing so.
     * </p>
     * <p>
     * For long exposure time (larger than the flickering boundary), find the
     * exposure time that is closest to the flickering boundary.
     * </p>
     *
     * @param flickeringMode The flickering mode
     * @param exposureTime The input exposureTime to be corrected
     * @return anti-flickering corrected exposure time
     */
    private long getAntiFlickeringExposureTime(int flickeringMode, long exposureTime) {
        if (flickeringMode != ANTI_FLICKERING_50HZ && flickeringMode != ANTI_FLICKERING_60HZ) {
            throw new IllegalArgumentException("Input anti-flickering mode must be 50 or 60Hz");
        }
        long flickeringBoundary = EXPOSURE_TIME_BOUNDARY_50HZ_NS;
        if (flickeringMode == ANTI_FLICKERING_60HZ) {
            flickeringBoundary = EXPOSURE_TIME_BOUNDARY_60HZ_NS;
        }

        if (exposureTime <= flickeringBoundary) {
            return exposureTime;
        }

        // Find the closest anti-flickering corrected exposure time
        long correctedExpTime = exposureTime + (flickeringBoundary / 2);
        correctedExpTime = correctedExpTime - (correctedExpTime % flickeringBoundary);
        return correctedExpTime;
    }
}