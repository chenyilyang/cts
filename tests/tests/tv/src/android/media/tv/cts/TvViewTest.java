/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.cts.util.PollingCheck;
import android.database.Cursor;
import android.test.ActivityInstrumentationTestCase2;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.net.Uri;
import android.util.ArrayMap;
import android.util.SparseIntArray;

import com.android.cts.tv.R;

import java.util.List;
import java.util.Map;

/**
 * Test {@link TvView}.
 */
public class TvViewTest extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT = 15000L;

    private TvView mTvView;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private TvInputManager mManager;
    private TvInputInfo mStubInfo;
    private final MockListener mListener = new MockListener();

    private static class MockListener extends TvView.TvInputListener {
        private final Map<String, Boolean> mVideoAvailableMap = new ArrayMap<>();
        private final Map<String, SparseIntArray> mSelectedTrackGenerationMap = new ArrayMap<>();
        private final Map<String, Integer> mTracksGenerationMap = new ArrayMap<>();
        private Object mLock = new Object();

        public boolean isVideoAvailable(String inputId) {
            synchronized (mLock) {
                Boolean available = mVideoAvailableMap.get(inputId);
                return available == null ? true : available.booleanValue();
            }
        }

        public int getSelectedTrackGeneration(String inputId, int type) {
            synchronized (mLock) {
                SparseIntArray selectedTrackGenerationMap =
                        mSelectedTrackGenerationMap.get(inputId);
                if (selectedTrackGenerationMap == null) {
                    return 0;
                }
                return selectedTrackGenerationMap.get(type, 0);
            }
        }

        public int getTrackGeneration(String inputId) {
            synchronized (mLock) {
                Integer tracksGeneration = mTracksGenerationMap.get(inputId);
                return tracksGeneration == null ? 0 : tracksGeneration.intValue();
            }
        }

        @Override
        public void onVideoAvailable(String inputId) {
            synchronized (mLock) {
                mVideoAvailableMap.put(inputId, true);
            }
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            synchronized (mLock) {
                mVideoAvailableMap.put(inputId, false);
            }
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            synchronized (mLock) {
                SparseIntArray selectedTrackGenerationMap =
                        mSelectedTrackGenerationMap.get(inputId);
                if (selectedTrackGenerationMap == null) {
                    selectedTrackGenerationMap = new SparseIntArray();
                    mSelectedTrackGenerationMap.put(inputId, selectedTrackGenerationMap);
                }
                int currentGeneration = selectedTrackGenerationMap.get(type, 0);
                selectedTrackGenerationMap.put(type, currentGeneration + 1);
            }
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> trackList) {
            synchronized (mLock) {
                Integer tracksGeneration = mTracksGenerationMap.get(inputId);
                mTracksGenerationMap.put(inputId,
                        tracksGeneration == null ? 1 : (tracksGeneration + 1));
            }
        }
    }

    /**
     * Instantiates a new TV view test.
     */
    public TvViewTest() {
        super(TvViewStubActivity.class);
    }

    /**
     * Find the TV view specified by id.
     *
     * @param id the id
     * @return the TV view
     */
    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mInstrumentation = getInstrumentation();
        mTvView = findTvViewById(R.id.tvview);
        mManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTunerTvInputService.class.getName())) {
                mStubInfo = info;
                break;
            }
        }
        assertNotNull(mStubInfo);
        mTvView.setTvInputListener(mListener);
    }

    public void testConstructor() throws Exception {
        new TvView(mActivity);

        new TvView(mActivity, null);

        new TvView(mActivity, null, 0);
    }

    private void tryTuneAllChannels(Runnable runOnEachChannel) throws Throwable {
        StubTunerTvInputService.insertChannels(mActivity.getContentResolver(), mStubInfo);

        Uri uri = TvContract.buildChannelsUriForInput(mStubInfo.getId());
        String[] projection = { TvContract.Channels._ID };
        Cursor cursor = mActivity.getContentResolver().query(
                uri, projection, null, null, null);
        try {
            while (cursor != null && cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                Uri channelUri = TvContract.buildChannelUri(channelId);
                mTvView.tune(mStubInfo.getId(), channelUri);
                mInstrumentation.waitForIdleSync();
                new PollingCheck(TIME_OUT) {
                    @Override
                    protected boolean check() {
                        return mListener.isVideoAvailable(mStubInfo.getId());
                    }
                }.run();

                if (runOnEachChannel != null) {
                    runOnEachChannel.run();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();

        StubTunerTvInputService.deleteChannels(mActivity.getContentResolver(), mStubInfo);
    }

    public void testSimpleTune() throws Throwable {
        tryTuneAllChannels(null);
    }

    private void selectTrackAndVerify(final int type, final TvTrackInfo track) {
        final int previousGeneration = mListener.getSelectedTrackGeneration(
                mStubInfo.getId(), type);
        mTvView.selectTrack(type, track == null ? null : track.getId());
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mListener.getSelectedTrackGeneration(
                        mStubInfo.getId(), type) > previousGeneration;
            }
        }.run();
        assertEquals(mTvView.getSelectedTrack(type), track == null ? null : track.getId());
    }

    public void testTrackChange() throws Throwable {
        tryTuneAllChannels(new Runnable() {
            @Override
            public void run() {
                new PollingCheck(TIME_OUT) {
                    @Override
                    protected boolean check() {
                        return mTvView.getTracks(TvTrackInfo.TYPE_AUDIO) != null;
                    }
                }.run();
                final int[] types = { TvTrackInfo.TYPE_AUDIO, TvTrackInfo.TYPE_VIDEO,
                    TvTrackInfo.TYPE_SUBTITLE };
                for (int type : types) {
                    final int typeF = type;
                    for (TvTrackInfo track : mTvView.getTracks(type)) {
                        selectTrackAndVerify(type, track);
                    }
                    selectTrackAndVerify(TvTrackInfo.TYPE_SUBTITLE, null);
                }
            }
        });
    }
}