/*
 * Copyright 2013 The Android Open Source Project
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
import static com.android.ex.camera2.blocking.BlockingStateListener.*;
import static com.android.ex.camera2.blocking.BlockingSessionListener.*;
import static org.mockito.Mockito.*;
import static android.hardware.camera2.CaptureRequest.*;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingSessionListener;
import com.android.ex.camera2.blocking.BlockingStateListener;
import com.android.ex.camera2.utils.StateWaiter;

import org.mockito.ArgumentMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <p>Basic test for CameraDevice APIs.</p>
 */
public class CameraDeviceTest extends Camera2AndroidTestCase {
    private static final String TAG = "CameraDeviceTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int ERROR_LISTENER_WAIT_TIMEOUT_MS = 1000;
    private static final int REPEATING_CAPTURE_EXPECTED_RESULT_COUNT = 5;
    private static final int MAX_NUM_IMAGES = 5;
    private static final int MIN_FPS_REQUIRED_FOR_STREAMING = 20;

    private CameraCaptureSession mSession;

    private BlockingStateListener mCameraMockListener;
    private int mLatestDeviceState = STATE_UNINITIALIZED;
    private BlockingSessionListener mSessionMockListener;
    private StateWaiter mSessionWaiter;
    private int mLatestSessionState = -1; // uninitialized

    private static int[] mTemplates = new int[] {
            CameraDevice.TEMPLATE_PREVIEW,
            CameraDevice.TEMPLATE_RECORD,
            CameraDevice.TEMPLATE_STILL_CAPTURE,
            CameraDevice.TEMPLATE_VIDEO_SNAPSHOT
    };

    @Override
    public void setContext(Context context) {
        super.setContext(context);

        /**
         * Workaround for mockito and JB-MR2 incompatibility
         *
         * Avoid java.lang.IllegalArgumentException: dexcache == null
         * https://code.google.com/p/dexmaker/issues/detail?id=2
         */
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        /**
         * Create error listener in context scope, to catch asynchronous device error.
         * Use spy object here since we want to use the SimpleDeviceListener callback
         * implementation (spy doesn't stub the functions unless we ask it to do so).
         */
        mCameraMockListener = spy(new BlockingStateListener());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        /**
         * Due to the asynchronous nature of camera device error callback, we
         * have to make sure device doesn't run into error state before. If so,
         * fail the rest of the tests. This is especially needed when error
         * callback is fired too late.
         */
        verify(mCameraMockListener, never())
                .onError(
                    any(CameraDevice.class),
                    anyInt());
        verify(mCameraMockListener, never())
                .onDisconnected(
                    any(CameraDevice.class));

        mCameraListener = mCameraMockListener;
        createDefaultImageReader(DEFAULT_CAPTURE_SIZE, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                new ImageDropperListener());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * <p>
     * Test camera capture request preview capture template.
     * </p>
     *
     * <p>
     * The request template returned by the camera device must include a
     * necessary set of metadata keys, and their values must be set correctly.
     * It mainly requires below settings:
     * </p>
     * <ul>
     * <li>All 3A settings are auto.</li>
     * <li>All sensor settings are not null.</li>
     * <li>All ISP processing settings should be non-manual, and the camera
     * device should make sure the stable frame rate is guaranteed for the given
     * settings.</li>
     * </ul>
     */
    public void testCameraDevicePreviewTemplate() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            captureTemplateTestByCamera(mCameraIds[i], CameraDevice.TEMPLATE_PREVIEW);
        }

        // TODO: test the frame rate sustainability in preview use case test.
    }

    /**
     * <p>
     * Test camera capture request still capture template.
     * </p>
     *
     * <p>
     * The request template returned by the camera device must include a
     * necessary set of metadata keys, and their values must be set correctly.
     * It mainly requires below settings:
     * </p>
     * <ul>
     * <li>All 3A settings are auto.</li>
     * <li>All sensor settings are not null.</li>
     * <li>All ISP processing settings should be non-manual, and the camera
     * device should make sure the high quality takes priority to the stable
     * frame rate for the given settings.</li>
     * </ul>
     */
    public void testCameraDeviceStillTemplate() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            captureTemplateTestByCamera(mCameraIds[i], CameraDevice.TEMPLATE_STILL_CAPTURE);
        }
    }

    /**
     * <p>
     * Test camera capture video recording template.
     * </p>
     *
     * <p>
     * The request template returned by the camera device must include a
     * necessary set of metadata keys, and their values must be set correctly.
     * It has the similar requirement as preview, with one difference:
     * </p>
     * <ul>
     * <li>Frame rate should be stable, for example, wide fps range like [7, 30]
     * is a bad setting.</li>
     */
    public void testCameraDeviceRecordingTemplate() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            captureTemplateTestByCamera(mCameraIds[i], CameraDevice.TEMPLATE_RECORD);
        }

        // TODO: test the frame rate sustainability in recording use case test.
    }

    /**
     *<p>Test camera capture video snapshot template.</p>
     *
     * <p>The request template returned by the camera device must include a necessary set of
     * metadata keys, and their values must be set correctly. It has the similar requirement
     * as recording, with an additional requirement: the settings should maximize image quality
     * without compromising stable frame rate.</p>
     */
    public void testCameraDeviceVideoSnapShotTemplate() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            captureTemplateTestByCamera(mCameraIds[i], CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
        }

        // TODO: test the frame rate sustainability in video snapshot use case test.
    }

    /**
     *<p>Test camera capture request zero shutter lag template.</p>
     *
     * <p>The request template returned by the camera device must include a necessary set of
     * metadata keys, and their values must be set correctly. It has the similar requirement
     * as preview, with an additional requirement: </p>
     */
    public void testCameraDeviceZSLTemplate() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            captureTemplateTestByCamera(mCameraIds[i], CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        }
    }

    /**
     * <p>
     * Test camera capture request manual template.
     * </p>
     *
     * <p>
     * The request template returned by the camera device must include a
     * necessary set of metadata keys, and their values must be set correctly. It
     * mainly requires below settings:
     * </p>
     * <ul>
     * <li>All 3A settings are manual.</li>
     * <li>ISP processing parameters are set to preview quality.</li>
     * <li>The manual capture parameters (exposure, sensitivity, and so on) are
     * set to reasonable defaults.</li>
     * </ul>
     */
    public void testCameraDeviceManualTemplate() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            captureTemplateTestByCamera(mCameraIds[i], CameraDevice.TEMPLATE_MANUAL);
        }
    }

    public void testCameraDeviceCreateCaptureBuilder() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i], mCameraMockListener);
                /**
                 * Test: that each template type is supported, and that its required fields are
                 * present.
                 */
                for (int j = 0; j < mTemplates.length; j++) {
                    CaptureRequest.Builder capReq = mCamera.createCaptureRequest(mTemplates[j]);
                    assertNotNull("Failed to create capture request", capReq);
                    assertNotNull("Missing field: SENSOR_EXPOSURE_TIME",
                            capReq.get(CaptureRequest.SENSOR_EXPOSURE_TIME));
                    assertNotNull("Missing field: SENSOR_SENSITIVITY",
                            capReq.get(CaptureRequest.SENSOR_SENSITIVITY));
                }
            }
            finally {
                try {
                    closeSession();
                } finally {
                    closeDevice(mCameraIds[i], mCameraMockListener);
                }
            }
        }
    }

    public void testCameraDeviceSetErrorListener() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i], mCameraMockListener);
                /**
                 * Test: that the error listener can be set without problems.
                 * Also, wait some time to check if device doesn't run into error.
                 */
                SystemClock.sleep(ERROR_LISTENER_WAIT_TIMEOUT_MS);
                verify(mCameraMockListener, never())
                        .onError(
                                any(CameraDevice.class),
                                anyInt());
            }
            finally {
                try {
                    closeSession();
                } finally {
                    closeDevice(mCameraIds[i], mCameraMockListener);
                }
            }
        }
    }

    public void testCameraDeviceCapture() throws Exception {
        runCaptureTest(/*burst*/false, /*repeating*/false, /*flush*/false);
    }

    public void testCameraDeviceCaptureBurst() throws Exception {
        runCaptureTest(/*burst*/true, /*repeating*/false, /*flush*/false);
    }

    public void testCameraDeviceRepeatingRequest() throws Exception {
        runCaptureTest(/*burst*/false, /*repeating*/true, /*flush*/false);
    }

    public void testCameraDeviceRepeatingBurst() throws Exception {
        runCaptureTest(/*burst*/true, /*repeating*/true, /*flush*/false);
    }

    /**
     * Test {@link CameraDevice#flush} API.
     *
     * <p>
     * Flush is the fastest way to idle the camera device for reconfiguration
     * with {@link #configureOutputs}, at the cost of discarding in-progress
     * work. Once the flush is complete, the idle callback will be called.
     * </p>
     */
    public void testCameraDeviceFlush() throws Exception {
        runCaptureTest(/*burst*/false, /*repeating*/true, /*flush*/true);
        runCaptureTest(/*burst*/true, /*repeating*/true, /*flush*/true);
        /**
         * TODO: this is only basic test of flush. we probably should also test below cases:
         *
         * 1. Performance. Make sure flush is faster than stopRepeating, we can test each one
         * a couple of times, then compare the average. Also, for flush() alone, we should make
         * sure it doesn't take too long time (e.g. <100ms for full devices, <500ms for limited
         * devices), after the flush, we should be able to get all results back very quickly.
         * This can be done in performance test.
         *
         * 2. Make sure all in-flight request comes back after flush, e.g. submit a couple of
         * long exposure single captures, then flush, then check if we can get the pending
         * request back quickly.
         *
         * 3. Also need check onCaptureSequenceCompleted for repeating burst after flush().
         */
    }

    /**
     * Test invalid capture (e.g. null or empty capture request).
     */
    public void testInvalidCapture() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i], mCameraMockListener);
                waitForDeviceState(STATE_OPENED, CAMERA_OPEN_TIMEOUT_MS);

                prepareCapture();

                invalidRequestCaptureTestByCamera();

                closeSession();
            }
            finally {
                try {

                } finally {
                    closeDevice(mCameraIds[i], mCameraMockListener);
                }
            }
        }
    }

    /**
     * Test to ensure that we can call camera2 API methods inside callbacks.
     *
     * Tests:
     *  onOpened -> createCaptureSession, createCaptureRequest
     *  onConfigured -> getDevice, abortCaptures,
     *     createCaptureRequest, capture, setRepeatingRequest, stopRepeating
     *  onCaptureCompleted -> createCaptureRequest, getDevice, abortCaptures,
     *     capture, setRepeatingRequest, stopRepeating, session+device.close
     */
    public void testChainedOperation() throws Throwable {

        // Set up single dummy target
        createDefaultImageReader(DEFAULT_CAPTURE_SIZE, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                /*listener*/ null);
        final ArrayList<Surface> outputs = new ArrayList<>();
        outputs.add(mReaderSurface);

        // A queue for the chained listeners to push results to
        // A success Throwable indicates no errors; other Throwables detail a test failure;
        // nulls indicate timeouts.
        final Throwable success = new Throwable("Success");
        final LinkedBlockingQueue<Throwable> results = new LinkedBlockingQueue<>();

        // Define listeners
        // A cascade of Device->Session->Capture listeners, each of which invokes at least one
        // method on the camera device or session.

        class ChainedCaptureListener extends CameraCaptureSession.CaptureListener {
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                    TotalCaptureResult result) {
                try {
                    CaptureRequest.Builder request2 =
                            session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    request2.addTarget(mReaderSurface);

                    // Some calls to the camera for coverage
                    session.abortCaptures();
                    session.capture(request2.build(),
                            /*listener*/ null, /*handler*/ null);
                    session.setRepeatingRequest(request2.build(),
                            /*listener*/ null, /*handler*/ null);
                    session.stopRepeating();

                    CameraDevice camera = session.getDevice();
                    session.close();
                    camera.close();

                    results.offer(success);
                } catch (Throwable t) {
                    results.offer(t);
                }
            }

            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                    CaptureFailure failure) {
                try {
                    CameraDevice camera = session.getDevice();
                    session.close();
                    camera.close();
                    fail("onCaptureFailed invoked with failure reason: " + failure.getReason());
                } catch (Throwable t) {
                    results.offer(t);
                }
            }
        }

        class ChainedSessionListener extends CameraCaptureSession.StateListener {
            private final ChainedCaptureListener mCaptureListener = new ChainedCaptureListener();

            public void onConfigured(CameraCaptureSession session) {
                try {
                    CaptureRequest.Builder request =
                            session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    request.addTarget(mReaderSurface);
                    // Some calls to the camera for coverage
                    session.getDevice();
                    session.abortCaptures();
                    // The important call for the next level of chaining
                    session.capture(request.build(), mCaptureListener, mHandler);
                    // Some more calls
                    session.setRepeatingRequest(request.build(),
                            /*listener*/ null, /*handler*/ null);
                    session.stopRepeating();
                    results.offer(success);
                } catch (Throwable t) {
                    results.offer(t);
                }
            }

            public void onConfigureFailed(CameraCaptureSession session) {
                try {
                    CameraDevice camera = session.getDevice();
                    session.close();
                    camera.close();
                    fail("onConfigureFailed was invoked");
                } catch (Throwable t) {
                    results.offer(t);
                }
            }
        }

        class ChainedCameraListener extends CameraDevice.StateListener {
            private final ChainedSessionListener mSessionListener = new ChainedSessionListener();

            public CameraDevice cameraDevice;

            public void onOpened(CameraDevice camera) {

                cameraDevice = camera;
                try {
                    // Some calls for coverage
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    // The important call for next level of chaining
                    camera.createCaptureSession(outputs, mSessionListener, mHandler);
                    results.offer(success);
                } catch (Throwable t) {
                    try {
                        camera.close();
                        results.offer(t);
                    } catch (Throwable t2) {
                        Log.e(TAG,
                                "Second failure reached; discarding first exception with trace " +
                                Log.getStackTraceString(t));
                        results.offer(t2);
                    }
                }
            }

            public void onDisconnected(CameraDevice camera) {
                try {
                    camera.close();
                    fail("onDisconnected invoked");
                } catch (Throwable t) {
                    results.offer(t);
                }
            }

            public void onError(CameraDevice camera, int error) {
                try {
                    camera.close();
                    fail("onError invoked with error code: " + error);
                } catch (Throwable t) {
                    results.offer(t);
                }
            }
        }

        // Actual test code

        for (int i = 0; i < mCameraIds.length; i++) {
            Throwable result;

            // Start chained cascade
            ChainedCameraListener cameraListener = new ChainedCameraListener();
            mCameraManager.openCamera(mCameraIds[i], cameraListener, mHandler);

            // Check if open succeeded
            result = results.poll(CAMERA_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result != success) {
                if (cameraListener.cameraDevice != null) cameraListener.cameraDevice.close();
                if (result == null) {
                    fail("Timeout waiting for camera open");
                } else {
                    throw result;
                }
            }

            // Check if configure succeeded
            result = results.poll(SESSION_CONFIGURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result != success) {
                if (cameraListener.cameraDevice != null) cameraListener.cameraDevice.close();
                if (result == null) {
                    fail("Timeout waiting for session configure");
                } else {
                    throw result;
                }
            }

            // Check if capture succeeded
            result = results.poll(CAPTURE_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result != success) {
                if (cameraListener.cameraDevice != null) cameraListener.cameraDevice.close();
                if (result == null) {
                    fail("Timeout waiting for capture completion");
                } else {
                    throw result;
                }
            }
        }
    }

    private void invalidRequestCaptureTestByCamera() throws Exception {
        if (VERBOSE) Log.v(TAG, "invalidRequestCaptureTestByCamera");

        List<CaptureRequest> emptyRequests = new ArrayList<CaptureRequest>();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest unConfiguredRequest = requestBuilder.build();
        List<CaptureRequest> unConfiguredRequests = new ArrayList<CaptureRequest>();
        unConfiguredRequests.add(unConfiguredRequest);

        try {
            // Test: CameraCaptureSession capture should throw IAE for null request.
            mSession.capture(/*request*/null, /*listener*/null, mHandler);
            mCollector.addMessage(
                    "Session capture should throw IllegalArgumentException for null request");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession capture should throw IAE for request
            // without surface configured.
            mSession.capture(unConfiguredRequest, /*listener*/null, mHandler);
            mCollector.addMessage("Session capture should throw " +
                    "IllegalArgumentException for request without surface configured");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession setRepeatingRequest should throw IAE for null request.
            mSession.setRepeatingRequest(/*request*/null, /*listener*/null, mHandler);
            mCollector.addMessage("Session setRepeatingRequest should throw " +
                    "IllegalArgumentException for null request");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession setRepeatingRequest should throw IAE for for request
            // without surface configured.
            mSession.setRepeatingRequest(unConfiguredRequest, /*listener*/null, mHandler);
            mCollector.addMessage("Capture zero burst should throw IllegalArgumentException " +
                    "for request without surface configured");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession captureBurst should throw IAE for null request list.
            mSession.captureBurst(/*requests*/null, /*listener*/null, mHandler);
            mCollector.addMessage("Session captureBurst should throw " +
                    "IllegalArgumentException for null request list");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession captureBurst should throw IAE for empty request list.
            mSession.captureBurst(emptyRequests, /*listener*/null, mHandler);
            mCollector.addMessage("Session captureBurst should throw " +
                    " IllegalArgumentException for empty request list");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession captureBurst should throw IAE for request
            // without surface configured.
            mSession.captureBurst(unConfiguredRequests, /*listener*/null, mHandler);
            fail("Session captureBurst should throw IllegalArgumentException " +
                    "for null request list");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession setRepeatingBurst should throw IAE for null request list.
            mSession.setRepeatingBurst(/*requests*/null, /*listener*/null, mHandler);
            mCollector.addMessage("Session setRepeatingBurst should throw " +
                    "IllegalArgumentException for null request list");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession setRepeatingBurst should throw IAE for empty request list.
            mSession.setRepeatingBurst(emptyRequests, /*listener*/null, mHandler);
            mCollector.addMessage("Session setRepeatingBurst should throw " +
                    "IllegalArgumentException for empty request list");
        } catch (IllegalArgumentException e) {
            // Pass.
        }

        try {
            // Test: CameraCaptureSession setRepeatingBurst should throw IAE for request
            // without surface configured.
            mSession.setRepeatingBurst(unConfiguredRequests, /*listener*/null, mHandler);
            mCollector.addMessage("Session setRepeatingBurst should throw " +
                    "IllegalArgumentException for request without surface configured");
        } catch (IllegalArgumentException e) {
            // Pass.
        }
    }

    private class IsCaptureResultNotEmpty
            extends ArgumentMatcher<TotalCaptureResult> {
        @Override
        public boolean matches(Object obj) {
            /**
             * Do the simple verification here. Only verify the timestamp for now.
             * TODO: verify more required capture result metadata fields.
             */
            TotalCaptureResult result = (TotalCaptureResult) obj;
            Long timeStamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            if (timeStamp != null && timeStamp.longValue() > 0L) {
                return true;
            }
            return false;
        }
    }

    /**
     * Run capture test with different test configurations.
     *
     * @param burst If the test uses {@link CameraDevice#captureBurst} or
     * {@link CameraDevice#setRepeatingBurst} to capture the burst.
     * @param repeating If the test uses {@link CameraDevice#setRepeatingBurst} or
     * {@link CameraDevice#setRepeatingRequest} for repeating capture.
     * @param flush If the test uses {@link CameraDevice#flush} to stop the repeating capture.
     * It has no effect if repeating is false.
     */
    private void runCaptureTest(boolean burst, boolean repeating, boolean flush) throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i], mCameraMockListener);
                waitForDeviceState(STATE_OPENED, CAMERA_OPEN_TIMEOUT_MS);

                prepareCapture();

                if (!burst) {
                    // Test: that a single capture of each template type succeeds.
                    for (int j = 0; j < mTemplates.length; j++) {
                        captureSingleShot(mCameraIds[i], mTemplates[j], repeating, flush);
                    }
                }
                else {
                    // Test: burst of one shot
                    captureBurstShot(mCameraIds[i], mTemplates, 1, repeating, flush);

                    int[] templates = new int[] {
                            CameraDevice.TEMPLATE_STILL_CAPTURE,
                            CameraDevice.TEMPLATE_STILL_CAPTURE,
                            CameraDevice.TEMPLATE_STILL_CAPTURE,
                            CameraDevice.TEMPLATE_STILL_CAPTURE,
                            CameraDevice.TEMPLATE_STILL_CAPTURE
                            };

                    // Test: burst of 5 shots of the same template type
                    captureBurstShot(mCameraIds[i], templates, templates.length, repeating, flush);

                    // Test: burst of 5 shots of different template types
                    captureBurstShot(
                            mCameraIds[i], mTemplates, mTemplates.length, repeating, flush);
                }
                verify(mCameraMockListener, never())
                        .onError(
                                any(CameraDevice.class),
                                anyInt());
            }
            finally {
                try {
                    closeSession();
                } finally {
                    closeDevice(mCameraIds[i], mCameraMockListener);
                }
            }
        }
    }

    private void captureSingleShot(
            String id,
            int template,
            boolean repeating, boolean flush) throws Exception {

        assertEquals("Bad initial state for preparing to capture",
                mLatestSessionState, SESSION_READY);

        CaptureRequest.Builder requestBuilder = mCamera.createCaptureRequest(template);
        assertNotNull("Failed to create capture request", requestBuilder);
        requestBuilder.addTarget(mReaderSurface);
        CameraCaptureSession.CaptureListener mockCaptureListener =
                mock(CameraCaptureSession.CaptureListener.class);

        if (VERBOSE) {
            Log.v(TAG, String.format("Capturing shot for device %s, template %d",
                    id, template));
        }

        startCapture(requestBuilder.build(), repeating, mockCaptureListener, mHandler);
        waitForSessionState(SESSION_ACTIVE, SESSION_ACTIVE_TIMEOUT_MS);

        int expectedCaptureResultCount = repeating ? REPEATING_CAPTURE_EXPECTED_RESULT_COUNT : 1;
        verifyCaptureResults(mockCaptureListener, expectedCaptureResultCount);

        if (repeating) {
            if (flush) {
                mSession.abortCaptures();
            } else {
                mSession.stopRepeating();
            }
        }
        waitForSessionState(SESSION_READY, SESSION_READY_TIMEOUT_MS);
    }

    private void captureBurstShot(
            String id,
            int[] templates,
            int len,
            boolean repeating,
            boolean flush) throws Exception {

        assertEquals("Bad initial state for preparing to capture",
                mLatestSessionState, SESSION_READY);

        assertTrue("Invalid args to capture function", len <= templates.length);
        List<CaptureRequest> requests = new ArrayList<CaptureRequest>();
        for (int i = 0; i < len; i++) {
            CaptureRequest.Builder requestBuilder = mCamera.createCaptureRequest(templates[i]);
            assertNotNull("Failed to create capture request", requestBuilder);
            requestBuilder.addTarget(mReaderSurface);
            requests.add(requestBuilder.build());
        }
        CameraCaptureSession.CaptureListener mockCaptureListener =
                mock(CameraCaptureSession.CaptureListener.class);

        if (VERBOSE) {
            Log.v(TAG, String.format("Capturing burst shot for device %s", id));
        }

        if (!repeating) {
            mSession.captureBurst(requests, mockCaptureListener, mHandler);
        }
        else {
            mSession.setRepeatingBurst(requests, mockCaptureListener, mHandler);
        }
        waitForSessionState(SESSION_ACTIVE, SESSION_READY_TIMEOUT_MS);

        int expectedResultCount = len;
        if (repeating) {
            expectedResultCount *= REPEATING_CAPTURE_EXPECTED_RESULT_COUNT;
        }

        verifyCaptureResults(mockCaptureListener, expectedResultCount);

        if (repeating) {
            if (flush) {
                mSession.abortCaptures();
            } else {
                mSession.stopRepeating();
            }
        }
        waitForSessionState(SESSION_READY, SESSION_READY_TIMEOUT_MS);
    }

    /**
     * Precondition: Device must be in known OPENED state (has been waited for).
     *
     * <p>Creates a new capture session and waits until it is in the {@code SESSION_READY} state.
     * </p>
     *
     * <p>Any existing capture session will be closed as a result of calling this.</p>
     * */
    private void prepareCapture() throws Exception {
        if (VERBOSE) Log.v(TAG, "prepareCapture");

        assertTrue("Bad initial state for preparing to capture",
                mLatestDeviceState == STATE_OPENED);

        if (mSession != null) {
            if (VERBOSE) Log.v(TAG, "prepareCapture - closing existing session");
            closeSession();
        }

        // Create a new session listener each time, it's not reusable across cameras
        mSessionMockListener = spy(new BlockingSessionListener());
        mSessionWaiter = mSessionMockListener.getStateWaiter();

        List<Surface> outputSurfaces = new ArrayList<>(Arrays.asList(mReaderSurface));
        mCamera.createCaptureSession(outputSurfaces, mSessionMockListener, mHandler);

        mSession = mSessionMockListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        waitForSessionState(SESSION_CONFIGURED, SESSION_CONFIGURE_TIMEOUT_MS);
        waitForSessionState(SESSION_READY, SESSION_READY_TIMEOUT_MS);
}

    private void waitForDeviceState(int state, long timeoutMs) {
        mCameraMockListener.waitForState(state, timeoutMs);
        mLatestDeviceState = state;
    }

    private void waitForSessionState(int state, long timeoutMs) {
        mSessionWaiter.waitForState(state, timeoutMs);
        mLatestSessionState = state;
    }

    private void verifyCaptureResults(
            CameraCaptureSession.CaptureListener mockListener,
            int expectResultCount) {
        // Should receive expected number of capture results.
        verify(mockListener,
                timeout(CAPTURE_WAIT_TIMEOUT_MS).atLeast(expectResultCount))
                        .onCaptureCompleted(
                                eq(mSession),
                                isA(CaptureRequest.class),
                                argThat(new IsCaptureResultNotEmpty()));
        // Should not receive any capture failed callbacks.
        verify(mockListener, never())
                        .onCaptureFailed(
                                eq(mSession),
                                isA(CaptureRequest.class),
                                isA(CaptureFailure.class));
        // Should receive expected number of capture shutter calls
        verify(mockListener,
                atLeast(expectResultCount))
                        .onCaptureStarted(
                               eq(mSession),
                               isA(CaptureRequest.class),
                               anyLong());
    }

    private void checkFpsRange(CaptureRequest.Builder request, int template,
            CameraCharacteristics props) {
        CaptureRequest.Key<Range<Integer>> fpsRangeKey = CONTROL_AE_TARGET_FPS_RANGE;
        Range<Integer> fpsRange;
        if ((fpsRange = mCollector.expectKeyValueNotNull(request, fpsRangeKey)) == null) {
            return;
        }

        int minFps = fpsRange.getLower();
        int maxFps = fpsRange.getUpper();
        Range<Integer>[] availableFpsRange = props
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        boolean foundRange = false;
        for (int i = 0; i < availableFpsRange.length; i += 1) {
            if (minFps == availableFpsRange[i].getLower()
                    && maxFps == availableFpsRange[i].getUpper()) {
                foundRange = true;
                break;
            }
        }
        if (!foundRange) {
            mCollector.addMessage(String.format("Unable to find the fps range (%d, %d)",
                    minFps, maxFps));
            return;
        }

        if (template != CameraDevice.TEMPLATE_MANUAL &&
                template != CameraDevice.TEMPLATE_STILL_CAPTURE) {
            if (maxFps < MIN_FPS_REQUIRED_FOR_STREAMING) {
                mCollector.addMessage("Max fps should be at least "
                        + MIN_FPS_REQUIRED_FOR_STREAMING);
                return;
            }

            // Need give fixed frame rate for video recording template.
            if (template == CameraDevice.TEMPLATE_RECORD) {
                if (maxFps != minFps) {
                    mCollector.addMessage("Video recording frame rate should be fixed");
                }
            }
        }
    }

    private void checkAfMode(CaptureRequest.Builder request, int template,
            CameraCharacteristics props) {
        boolean hasFocuser =
                props.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) > 0f;

        if (!hasFocuser) {
            return;
        }

        int targetAfMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        int[] availableAfMode = props.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (template == CameraDevice.TEMPLATE_PREVIEW ||
                template == CameraDevice.TEMPLATE_STILL_CAPTURE ||
                template == CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG) {
            // Default to CONTINUOUS_PICTURE if it is available, otherwise AUTO.
            for (int i = 0; i < availableAfMode.length; i++) {
                if (availableAfMode[i] == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                    targetAfMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                    break;
                }
            }
        } else if (template == CameraDevice.TEMPLATE_RECORD ||
                template == CameraDevice.TEMPLATE_VIDEO_SNAPSHOT) {
            // Default to CONTINUOUS_VIDEO if it is available, otherwise AUTO.
            for (int i = 0; i < availableAfMode.length; i++) {
                if (availableAfMode[i] == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                    targetAfMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                    break;
                }
            }
        } else if (template == CameraDevice.TEMPLATE_MANUAL) {
            targetAfMode = CaptureRequest.CONTROL_AF_MODE_OFF;
        }

        mCollector.expectKeyValueEquals(request, CONTROL_AF_MODE, targetAfMode);
        mCollector.expectKeyValueNotNull(request, LENS_FOCUS_DISTANCE);
    }

    /**
     * <p>Check if the request settings are suitable for a given request template.</p>
     *
     * <p>This function doesn't fail the test immediately, it updates the
     * test pass/fail status and appends the failure message to the error collector each key.</p>
     *
     * @param request The request to be checked.
     * @param template The capture template targeted by this request.
     * @param props The CameraCharacteristics this request is checked against with.
     */
    private void checkRequestForTemplate(CaptureRequest.Builder request, int template,
            CameraCharacteristics props) {
        // 3A settings--control.mode.
        if (template != CameraDevice.TEMPLATE_MANUAL) {
            mCollector.expectKeyValueEquals(request, CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO);
        }

        // 3A settings--AE/AWB/AF.
        int maxRegionsAe = props.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        int maxRegionsAwb = props.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB);
        int maxRegionsAf = props.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

        checkAfMode(request, template, props);
        checkFpsRange(request, template, props);

        if (template == CameraDevice.TEMPLATE_MANUAL) {
            mCollector.expectKeyValueEquals(request, CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
            mCollector.expectKeyValueEquals(request, CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF);
            mCollector.expectKeyValueEquals(request, CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF);
        } else {
            mCollector.expectKeyValueEquals(request, CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            mCollector.expectKeyValueNotEquals(request, CONTROL_AE_ANTIBANDING_MODE,
                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF);
            mCollector.expectKeyValueEquals(request, CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            mCollector.expectKeyValueEquals(request, CONTROL_AE_LOCK, false);
            mCollector.expectKeyValueEquals(request, CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            mCollector.expectKeyValueEquals(request, CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            mCollector.expectKeyValueEquals(request, CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO);
            mCollector.expectKeyValueEquals(request, CONTROL_AWB_LOCK, false);

            // Check 3A regions.
            if (VERBOSE) {
                Log.v(TAG, String.format("maxRegions is: {AE: %s, AWB: %s, AF: %s}",
                        maxRegionsAe, maxRegionsAwb, maxRegionsAf));
            }
            if (maxRegionsAe > 0) {
                mCollector.expectKeyValueNotNull(request, CONTROL_AE_REGIONS);
            }
            if (maxRegionsAwb > 0) {
                mCollector.expectKeyValueNotNull(request, CONTROL_AWB_REGIONS);
            }
            if (maxRegionsAf > 0) {
                mCollector.expectKeyValueNotNull(request, CONTROL_AF_REGIONS);
            }
        }

        // Sensor settings.
        float[] availableApertures =
                props.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        if (availableApertures.length > 1) {
            mCollector.expectKeyValueNotNull(request, LENS_APERTURE);
        }

        float[] availableFilters =
                props.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES);
        if (availableFilters.length > 1) {
            mCollector.expectKeyValueNotNull(request, LENS_FILTER_DENSITY);
        }

        float[] availableFocalLen =
                props.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (availableFocalLen.length > 1) {
            mCollector.expectKeyValueNotNull(request, LENS_FOCAL_LENGTH);
        }

        int[] availableOIS =
                props.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if (availableOIS.length > 1) {
            mCollector.expectKeyValueNotNull(request, LENS_OPTICAL_STABILIZATION_MODE);
        }

        mCollector.expectKeyValueEquals(request, BLACK_LEVEL_LOCK, false);
        mCollector.expectKeyValueNotNull(request, SENSOR_FRAME_DURATION);
        mCollector.expectKeyValueNotNull(request, SENSOR_EXPOSURE_TIME);
        mCollector.expectKeyValueNotNull(request, SENSOR_SENSITIVITY);

        // ISP-processing settings.
        mCollector.expectKeyValueEquals(
                request, STATISTICS_FACE_DETECT_MODE,
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
        mCollector.expectKeyValueEquals(request, FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        mCollector.expectKeyValueEquals(
                request, STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF);

        if (template == CameraDevice.TEMPLATE_STILL_CAPTURE) {
            // Not enforce high quality here, as some devices may not effectively have high quality
            // mode.
            mCollector.expectKeyValueNotEquals(
                    request, COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);

            List<Integer> availableEdgeModes =
                    Arrays.asList(toObject(mStaticInfo.getAvailableEdgeModesChecked()));
            if (availableEdgeModes.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY)) {
                mCollector.expectKeyValueEquals(request, EDGE_MODE,
                        CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            } else if (availableEdgeModes.contains(CaptureRequest.EDGE_MODE_FAST)) {
                mCollector.expectKeyValueEquals(request, EDGE_MODE, CaptureRequest.EDGE_MODE_FAST);
            } else {
                mCollector.expectKeyValueEquals(request, EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            }

            List<Integer> availableNoiseReductionModes =
                    Arrays.asList(toObject(mStaticInfo.getAvailableNoiseReductionModesChecked()));
            if (availableNoiseReductionModes
                    .contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                mCollector.expectKeyValueEquals(
                        request, NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            } else if (availableNoiseReductionModes
                    .contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST)) {
                mCollector.expectKeyValueEquals(
                        request, NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST);
            } else {
                mCollector.expectKeyValueEquals(
                        request, NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            }

            List<Integer> availableToneMapModes =
                    Arrays.asList(toObject(mStaticInfo.getAvailableToneMapModesChecked()));
            if (availableToneMapModes.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)) {
                mCollector.expectKeyValueEquals(request, TONEMAP_MODE,
                        CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
            } else {
                mCollector.expectKeyValueEquals(request, TONEMAP_MODE,
                        CaptureRequest.TONEMAP_MODE_FAST);
            }

            // Still capture template should have android.statistics.lensShadingMapMode ON when
            // DNG capability is supported.
            List<Integer> availableCaps = mStaticInfo.getAvailableCapabilitiesChecked();
            if (availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_DNG)) {
                mCollector.expectKeyValueEquals(request, STATISTICS_LENS_SHADING_MAP_MODE,
                        STATISTICS_LENS_SHADING_MAP_MODE_ON);
            }
        } else {
            mCollector.expectKeyValueNotNull(request, EDGE_MODE);
            mCollector.expectKeyValueNotNull(request, NOISE_REDUCTION_MODE);
            mCollector.expectKeyValueNotEquals(request, TONEMAP_MODE,
                    CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE);
        }

        mCollector.expectKeyValueEquals(request, CONTROL_CAPTURE_INTENT, template);

        // TODO: use the list of keys from CameraCharacteristics to avoid expecting
        //       keys which are not available by this CameraDevice.
    }

    private void captureTemplateTestByCamera(String cameraId, int template) throws Exception {
        try {
            openDevice(cameraId, mCameraMockListener);

            assertTrue("Camera template " + template + " is out of range!",
                    template >= CameraDevice.TEMPLATE_PREVIEW
                            && template <= CameraDevice.TEMPLATE_MANUAL);

            mCollector.setCameraId(cameraId);
            CaptureRequest.Builder request = mCamera.createCaptureRequest(template);
            assertNotNull("Failed to create capture request for template " + template, request);

            CameraCharacteristics props = mStaticInfo.getCharacteristics();
            checkRequestForTemplate(request, template, props);
        }
        finally {
            try {
                closeSession();
            } finally {
                closeDevice(cameraId, mCameraMockListener);
            }
        }
    }

    /**
     * Start capture with given {@link #CaptureRequest}.
     *
     * @param request The {@link #CaptureRequest} to be captured.
     * @param repeating If the capture is single capture or repeating.
     * @param listener The {@link #CaptureListener} camera device used to notify callbacks.
     * @param handler The handler camera device used to post callbacks.
     */
    protected void startCapture(CaptureRequest request, boolean repeating,
            CameraCaptureSession.CaptureListener listener, Handler handler)
                    throws CameraAccessException {
        if (VERBOSE) Log.v(TAG, "Starting capture from session");

        if (repeating) {
            mSession.setRepeatingRequest(request, listener, handler);
        } else {
            mSession.capture(request, listener, handler);
        }
    }

    /**
     * Close a {@link #CameraCaptureSession capture session}; blocking until
     * the close finishes with a transition to {@link CameraCaptureSession.StateListener#onClosed}.
     */
    protected void closeSession() {
        if (mSession == null) {
            return;
        }

        mSession.close();
        waitForSessionState(SESSION_CLOSED, SESSION_CLOSE_TIMEOUT_MS);
        mSession = null;

        mSessionMockListener = null;
        mSessionWaiter = null;
    }
}
