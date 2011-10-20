/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.tradefed.testtype;

import com.android.cts.tradefed.build.CtsBuildHelper;
import com.android.cts.tradefed.device.DeviceInfoCollector;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import junit.framework.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A {@link Test} for running CTS tests.
 * <p/>
 * Supports running all the tests contained in a CTS plan, or individual test packages.
 */
public class CtsTest implements IDeviceTest, IResumableTest, IShardableTest, IBuildReceiver {
    private static final String LOG_TAG = "CtsTest";

    public static final String PLAN_OPTION = "plan";
    private static final String PACKAGE_OPTION = "package";
    private static final String CLASS_OPTION = "class";
    private static final String METHOD_OPTION = "method";

    public static final String PACKAGE_NAME_METRIC = "packageName";
    public static final String PACKAGE_DIGEST_METRIC = "packageDigest";

    private ITestDevice mDevice;

    @Option(name = PLAN_OPTION, description = "the test plan to run.",
            importance = Importance.IF_UNSET)
    private String mPlanName = null;

    @Option(name = PACKAGE_OPTION, shortName = 'p', description = "the test packages(s) to run.",
            importance = Importance.IF_UNSET)
    private Collection<String> mPackageNames = new ArrayList<String>();

    @Option(name = "exclude-package", description = "the test packages(s) to exclude from the run.")
    private Collection<String> mExcludedPackageNames = new ArrayList<String>();

    @Option(name = CLASS_OPTION, shortName = 'c', description = "run a specific test class.",
            importance = Importance.IF_UNSET)
    private String mClassName = null;

    @Option(name = METHOD_OPTION, shortName = 'm',
            description = "run a specific test method, from given --class.",
            importance = Importance.IF_UNSET)
    private String mMethodName = null;

    @Option(name = "collect-device-info", description =
        "flag to control whether to collect info from device. Turning this off will speed up test" +
        "execution for short test runs but will result in required data being omitted from the " +
        "test report.")
    private boolean mCollectDeviceInfo = true;

    @Option(name = "resume", description =
        "flag to attempt to automatically resume aborted test run on another connected device. ")
    private boolean mResume = false;

    @Option(name = "shards", description =
        "shard the tests to run into separately runnable chunks to execute on multiple devices " +
        "concurrently.")
    private int mShards = 1;

    @Option(name = "screenshot", description =
        "flag for taking a screenshot of the device when test execution is complete.")
    private boolean mScreenshot = false;

    /** data structure for a {@link IRemoteTest} and its known tests */
    class TestPackage {
        private final IRemoteTest mTestForPackage;
        private final Collection<TestIdentifier> mKnownTests;
        private final ITestPackageDef mPackageDef;

        TestPackage(ITestPackageDef packageDef, IRemoteTest testForPackage,
                Collection<TestIdentifier> knownTests) {
            mPackageDef = packageDef;
            mTestForPackage = testForPackage;
            mKnownTests = knownTests;
        }

        IRemoteTest getTestForPackage() {
            return mTestForPackage;
        }

        Collection<TestIdentifier> getKnownTests() {
            return mKnownTests;
        }

        ITestPackageDef getPackageDef() {
            return mPackageDef;
        }

        /**
         * Return the test run name that should be used for the TestPackage
         * @return
         */
        String getTestRunName() {
            return mPackageDef.getUri();
        }
    }

    /** list of remaining tests to execute */
    private List<TestPackage> mRemainingTestPkgs = null;

    private CtsBuildHelper mCtsBuild = null;
    private IBuildInfo mBuildInfo = null;

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Set the plan name to run.
     * <p/>
     * Exposed for unit testing
     */
    void setPlanName(String planName) {
        mPlanName = planName;
    }

    /**
     * Set the collect device info flag.
     * <p/>
     * Exposed for unit testing
     */
    void setCollectDeviceInfo(boolean collectDeviceInfo) {
        mCollectDeviceInfo = collectDeviceInfo;
    }

    /**
     * Adds a package name to the list of test packages to run.
     * <p/>
     * Exposed for unit testing
     */
    void addPackageName(String packageName) {
        mPackageNames.add(packageName);
    }

    /**
     * Adds a package name to the list of test packages to exclude.
     * <p/>
     * Exposed for unit testing
     */
    void addExcludedPackageName(String packageName) {
        mExcludedPackageNames.add(packageName);
    }

    /**
     * Set the test class name to run.
     * <p/>
     * Exposed for unit testing
     */
    void setClassName(String className) {
        mClassName = className;
    }

    /**
     * Set the test method name to run.
     * <p/>
     * Exposed for unit testing
     */
    void setMethodName(String methodName) {
        mMethodName = methodName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResumable() {
        return mResume;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mCtsBuild = CtsBuildHelper.createBuildHelper(build);
        mBuildInfo = build;
    }

    /**
     * Set the CTS build container.
     * <p/>
     * Exposed so unit tests can mock the provided build.
     *
     * @param buildHelper
     */
    void setBuildHelper(CtsBuildHelper buildHelper) {
        mCtsBuild = buildHelper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (getDevice() == null) {
            throw new IllegalArgumentException("missing device");
        }

        if (mRemainingTestPkgs == null) {
            checkFields();
            mRemainingTestPkgs = buildTestsToRun();
        }

        ResultFilter filter = new ResultFilter(listener, mRemainingTestPkgs);

        try {
            // always collect the device info, even for resumed runs, since test will likely be
            // running on a different device
            collectDeviceInfo(getDevice(), mCtsBuild, listener);

            while (!mRemainingTestPkgs.isEmpty()) {
                TestPackage knownTests = mRemainingTestPkgs.get(0);

                IRemoteTest test = knownTests.getTestForPackage();
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest)test).setDevice(getDevice());
                }
                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver)test).setBuild(mBuildInfo);
                }

                forwardPackageDetails(knownTests.getPackageDef(), listener);
                test.run(filter);
                mRemainingTestPkgs.remove(0);
            }

            if (mScreenshot) {
                InputStreamSource screenshotSource = getDevice().getScreenshot();
                try {
                    listener.testLog("screenshot", LogDataType.PNG, screenshotSource);
                } finally {
                    screenshotSource.cancel();
                }
            }
        } finally {
            filter.reportUnexecutedTests();
        }
    }

    /**
     * Build the list of test packages to run
     *
     * @return
     */
    private List<TestPackage> buildTestsToRun() {
        List<TestPackage> testPkgList = new LinkedList<TestPackage>();
        try {
            ITestPackageRepo testRepo = createTestCaseRepo();
            Collection<ITestPackageDef> testPkgDefs = getTestPackagesToRun(testRepo);

            for (ITestPackageDef testPkgDef : testPkgDefs) {
                addTestPackage(testPkgList, testPkgDef);
            }
            if (testPkgList.isEmpty()) {
                Log.logAndDisplay(LogLevel.WARN, LOG_TAG, "No tests to run");
            }
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("failed to find CTS plan file", e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("failed to parse CTS plan file", e);
        }
        return testPkgList;
    }

    /**
     * Adds a test package to the list of packages to test
     *
     * @param testList
     * @param testPkgDef
     */
    private void addTestPackage(List<TestPackage> testList, ITestPackageDef testPkgDef) {
        IRemoteTest testForPackage = testPkgDef.createTest(mCtsBuild.getTestCasesDir());
        if (testForPackage != null) {
            Collection<TestIdentifier> knownTests = testPkgDef.getTests();
            testList.add(new TestPackage(testPkgDef, testForPackage, knownTests));
        }
    }

    /**
     * Return the list of test package defs to run
     *
     * @return the list of test package defs to run
     * @throws ParseException
     * @throws FileNotFoundException
     */
    private Collection<ITestPackageDef> getTestPackagesToRun(ITestPackageRepo testRepo)
            throws ParseException, FileNotFoundException {
        // use LinkedHashSet to have predictable iteration order
        Set<ITestPackageDef> testPkgDefs = new LinkedHashSet<ITestPackageDef>();
        if (mPlanName != null) {
            Log.i(LOG_TAG, String.format("Executing CTS test plan %s", mPlanName));
            File ctsPlanFile = mCtsBuild.getTestPlanFile(mPlanName);
            ITestPlan parser = createPlan(mPlanName);
            parser.parse(createXmlStream(ctsPlanFile));
            for (String uri : parser.getTestUris()) {
                if (!mExcludedPackageNames.contains(uri)) {
                    ITestPackageDef testPackage = testRepo.getTestPackage(uri);
                    testPackage.setExcludedTestFilter(parser.getExcludedTestFilter(uri));
                    testPkgDefs.add(testPackage);
                }
            }
        } else if (mPackageNames.size() > 0){
            Log.i(LOG_TAG, String.format("Executing CTS test packages %s", mPackageNames));
            for (String uri : mPackageNames) {
                ITestPackageDef testPackage = testRepo.getTestPackage(uri);
                if (testPackage != null) {
                    testPkgDefs.add(testPackage);
                } else {
                    throw new IllegalArgumentException(String.format(
                            "Could not find test package %s. " +
                            "Use 'list packages' to see available packages." , uri));
                }
            }
        } else if (mClassName != null) {
            Log.i(LOG_TAG, String.format("Executing CTS test class %s", mClassName));
            // try to find package to run from class name
            String packageUri = testRepo.findPackageForTest(mClassName);
            if (packageUri != null) {
                ITestPackageDef testPackageDef = testRepo.getTestPackage(packageUri);
                testPackageDef.setClassName(mClassName, mMethodName);
                testPkgDefs.add(testPackageDef);
            } else {
                Log.logAndDisplay(LogLevel.WARN, LOG_TAG, String.format(
                        "Could not find package for test class %s", mClassName));
            }
        } else {
            // should never get here - was checkFields() not called?
            throw new IllegalStateException("nothing to run?");
        }
        return testPkgDefs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        if (mShards <= 1) {
            return null;
        }
        checkFields();
        List<TestPackage> allTests = buildTestsToRun();

        if (allTests.size() <= 1) {
            Log.w(LOG_TAG, "no tests to shard!");
            return null;
        }

        // treat shardQueue as a circular queue, to sequentially distribute tests among shards
        Queue<IRemoteTest> shardQueue = new LinkedList<IRemoteTest>();
        // don't create more shards than the number of tests we have!
        for (int i = 0; i < mShards && i < allTests.size(); i++) {
            CtsTest shard = new CtsTest();
            shard.mRemainingTestPkgs = new LinkedList<TestPackage>();
            shardQueue.add(shard);
        }
        while (!allTests.isEmpty()) {
            TestPackage testPair = allTests.remove(0);
            CtsTest shard = (CtsTest)shardQueue.poll();
            shard.mRemainingTestPkgs.add(testPair);
            shardQueue.add(shard);
        }
        return shardQueue;
    }

    /**
     * Runs the device info collector instrumentation on device, and forwards it to test listeners
     * as run metrics.
     * <p/>
     * Exposed so unit tests can mock.
     *
     * @param listeners
     * @throws DeviceNotAvailableException
     * @throws FileNotFoundException
     */
    void collectDeviceInfo(ITestDevice device, CtsBuildHelper ctsBuild,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mCollectDeviceInfo) {
            DeviceInfoCollector.collectDeviceInfo(device, ctsBuild.getTestCasesDir(), listener);
        }
    }

    /**
     * Factory method for creating a {@link ITestPackageRepo}.
     * <p/>
     * Exposed for unit testing
     */
    ITestPackageRepo createTestCaseRepo() {
        return new TestPackageRepo(mCtsBuild.getTestCasesDir());
    }

    /**
     * Factory method for creating a {@link TestPlan}.
     * <p/>
     * Exposed for unit testing
     */
    ITestPlan createPlan(String planName) {
        return new TestPlan(planName);
    }

    /**
     * Factory method for creating a {@link InputStream} from a plan xml file.
     * <p/>
     * Exposed for unit testing
     */
    InputStream createXmlStream(File xmlFile) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(xmlFile));
    }

    private void checkFields() {
        // for simplicity of command line usage, make --plan, --package, and --class mutually
        // exclusive
        boolean mutualExclusiveArgs = xor(mPlanName != null, mPackageNames.size() > 0,
                mClassName != null);

        if (!mutualExclusiveArgs) {
            throw new IllegalArgumentException(String.format(
                    "Ambiguous or missing arguments. " +
                    "One and only of --%s --%s(s) or --%s to run can be specified",
                    PLAN_OPTION, PACKAGE_OPTION, CLASS_OPTION));
        }
        if (mMethodName != null && mClassName == null) {
            throw new IllegalArgumentException(String.format(
                    "Must specify --%s when --%s is used", CLASS_OPTION, METHOD_OPTION));
        }
        if (mCtsBuild == null) {
            throw new IllegalArgumentException("missing CTS build");
        }
        if ("CTS".equals(mPlanName)) {
            CLog.i("Switching to CTS-TF plan instead of CTS plan for faster execution of vm-tests");
            mPlanName = "CTS-TF";
        }
    }

    /**
     * Helper method to perform exclusive or on list of boolean arguments
     *
     * @param args set of booleans on which to perform exclusive or
     * @return <code>true</code> if one and only one of <var>args</code> is <code>true</code>.
     *         Otherwise return <code>false</code>.
     */
    private boolean xor(boolean... args) {
        boolean currentVal = args[0];
        for (int i=1; i < args.length; i++) {
            if (currentVal && args[i]) {
                return false;
            }
            currentVal |= args[i];
        }
        return currentVal;
    }

    /**
     * Forward the digest and package name to the listener as a metric
     *
     * @param listener
     */
    private void forwardPackageDetails(ITestPackageDef def, ITestInvocationListener listener) {
        Map<String, String> metrics = new HashMap<String, String>(2);
        metrics.put(PACKAGE_NAME_METRIC, def.getName());
        metrics.put(PACKAGE_DIGEST_METRIC, def.getDigest());
        listener.testRunStarted(def.getUri(), 0);
        listener.testRunEnded(0, metrics);
    }
}
