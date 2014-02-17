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

// Don't edit this file!  It is auto-generated by frameworks/rs/api/gen_runtime.

package android.renderscript.cts;

import android.renderscript.Allocation;
import android.renderscript.RSRuntimeException;
import android.renderscript.Element;

public class TestPown extends RSBaseCompute {

    private ScriptC_TestPown script;
    private ScriptC_TestPownRelaxed scriptRelaxed;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        script = new ScriptC_TestPown(mRS);
        scriptRelaxed = new ScriptC_TestPownRelaxed(mRS);
    }

    public class ArgumentsFloatIntFloat {
        public float inX;
        public int inY;
        public float out;

        public int ulf;
        public int ulfRelaxed;
    }

    private void checkPownFloatIntFloat() {
        Allocation inX = CreateRandomAllocation(mRS, Element.DataType.FLOAT_32, 1, 0x5aab6c366fd179f9L);
        Allocation inY = CreateRandomAllocation(mRS, Element.DataType.SIGNED_32, 1, 0x5aab6c366fd179f9L);
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            script.set_gAllocInY(inY);
            script.forEach_testPownFloatIntFloat(inX, out);
            verifyResultsPownFloatIntFloat(inX, inY, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloatIntFloat: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            scriptRelaxed.set_gAllocInY(inY);
            scriptRelaxed.forEach_testPownFloatIntFloat(inX, out);
            verifyResultsPownFloatIntFloat(inX, inY, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloatIntFloat: " + e.toString());
        }
    }

    private void verifyResultsPownFloatIntFloat(Allocation inX, Allocation inY, Allocation out, boolean relaxed) {
        float[] arrayInX = new float[INPUTSIZE * 1];
        inX.copyTo(arrayInX);
        int[] arrayInY = new int[INPUTSIZE * 1];
        inY.copyTo(arrayInY);
        float[] arrayOut = new float[INPUTSIZE * 1];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 1 ; j++) {
                // Extract the inputs.
                ArgumentsFloatIntFloat args = new ArgumentsFloatIntFloat();
                args.inX = arrayInX[i];
                args.inY = arrayInY[i];
                // Figure out what the outputs should have been.
                CoreMathVerifier.computePown(args);
                int ulf = relaxed ? args.ulfRelaxed : args.ulf;
                // Figure out what the outputs should have been.
                boolean valid = true;
                int neededUlf = 0;
                neededUlf = (int) (Math.abs(args.out - arrayOut[i * 1 + j]) / Math.ulp(args.out) + 0.5);
                if (neededUlf > ulf) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append(String.format("Input inX: %x %.16f", Float.floatToRawIntBits(args.inX), args.inX));
                    message.append("\n");
                    message.append(String.format("Input inY: %d", args.inY));
                    message.append("\n");
                    message.append(String.format("Expected output out: %x %.16f", Float.floatToRawIntBits(args.out), args.out));
                    message.append("\n");
                    message.append(String.format("Actual   output out: %x %.16f", Float.floatToRawIntBits(arrayOut[i * 1 + j]), arrayOut[i * 1 + j]));
                    neededUlf = (int) (Math.abs(args.out - arrayOut[i * 1 + j]) / Math.ulp(args.out) + 0.5);
                    if (neededUlf > ulf) {
                        message.append(String.format(" FAILED, ulf needed %d, specified %d", neededUlf, ulf));
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkPownFloatIntFloat" + (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkPownFloat2Int2Float2() {
        Allocation inX = CreateRandomAllocation(mRS, Element.DataType.FLOAT_32, 2, 0xc87bf6763d9c0b6bL);
        Allocation inY = CreateRandomAllocation(mRS, Element.DataType.SIGNED_32, 2, 0xc87bf6763d9c0b6bL);
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            script.set_gAllocInY(inY);
            script.forEach_testPownFloat2Int2Float2(inX, out);
            verifyResultsPownFloat2Int2Float2(inX, inY, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloat2Int2Float2: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            scriptRelaxed.set_gAllocInY(inY);
            scriptRelaxed.forEach_testPownFloat2Int2Float2(inX, out);
            verifyResultsPownFloat2Int2Float2(inX, inY, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloat2Int2Float2: " + e.toString());
        }
    }

    private void verifyResultsPownFloat2Int2Float2(Allocation inX, Allocation inY, Allocation out, boolean relaxed) {
        float[] arrayInX = new float[INPUTSIZE * 2];
        inX.copyTo(arrayInX);
        int[] arrayInY = new int[INPUTSIZE * 2];
        inY.copyTo(arrayInY);
        float[] arrayOut = new float[INPUTSIZE * 2];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 2 ; j++) {
                // Extract the inputs.
                ArgumentsFloatIntFloat args = new ArgumentsFloatIntFloat();
                args.inX = arrayInX[i * 2 + j];
                args.inY = arrayInY[i * 2 + j];
                // Figure out what the outputs should have been.
                CoreMathVerifier.computePown(args);
                int ulf = relaxed ? args.ulfRelaxed : args.ulf;
                // Figure out what the outputs should have been.
                boolean valid = true;
                int neededUlf = 0;
                neededUlf = (int) (Math.abs(args.out - arrayOut[i * 2 + j]) / Math.ulp(args.out) + 0.5);
                if (neededUlf > ulf) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append(String.format("Input inX: %x %.16f", Float.floatToRawIntBits(args.inX), args.inX));
                    message.append("\n");
                    message.append(String.format("Input inY: %d", args.inY));
                    message.append("\n");
                    message.append(String.format("Expected output out: %x %.16f", Float.floatToRawIntBits(args.out), args.out));
                    message.append("\n");
                    message.append(String.format("Actual   output out: %x %.16f", Float.floatToRawIntBits(arrayOut[i * 2 + j]), arrayOut[i * 2 + j]));
                    neededUlf = (int) (Math.abs(args.out - arrayOut[i * 2 + j]) / Math.ulp(args.out) + 0.5);
                    if (neededUlf > ulf) {
                        message.append(String.format(" FAILED, ulf needed %d, specified %d", neededUlf, ulf));
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkPownFloat2Int2Float2" + (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkPownFloat3Int3Float3() {
        Allocation inX = CreateRandomAllocation(mRS, Element.DataType.FLOAT_32, 3, 0x1e18711582944e7eL);
        Allocation inY = CreateRandomAllocation(mRS, Element.DataType.SIGNED_32, 3, 0x1e18711582944e7eL);
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            script.set_gAllocInY(inY);
            script.forEach_testPownFloat3Int3Float3(inX, out);
            verifyResultsPownFloat3Int3Float3(inX, inY, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloat3Int3Float3: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            scriptRelaxed.set_gAllocInY(inY);
            scriptRelaxed.forEach_testPownFloat3Int3Float3(inX, out);
            verifyResultsPownFloat3Int3Float3(inX, inY, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloat3Int3Float3: " + e.toString());
        }
    }

    private void verifyResultsPownFloat3Int3Float3(Allocation inX, Allocation inY, Allocation out, boolean relaxed) {
        float[] arrayInX = new float[INPUTSIZE * 4];
        inX.copyTo(arrayInX);
        int[] arrayInY = new int[INPUTSIZE * 4];
        inY.copyTo(arrayInY);
        float[] arrayOut = new float[INPUTSIZE * 4];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 3 ; j++) {
                // Extract the inputs.
                ArgumentsFloatIntFloat args = new ArgumentsFloatIntFloat();
                args.inX = arrayInX[i * 4 + j];
                args.inY = arrayInY[i * 4 + j];
                // Figure out what the outputs should have been.
                CoreMathVerifier.computePown(args);
                int ulf = relaxed ? args.ulfRelaxed : args.ulf;
                // Figure out what the outputs should have been.
                boolean valid = true;
                int neededUlf = 0;
                neededUlf = (int) (Math.abs(args.out - arrayOut[i * 4 + j]) / Math.ulp(args.out) + 0.5);
                if (neededUlf > ulf) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append(String.format("Input inX: %x %.16f", Float.floatToRawIntBits(args.inX), args.inX));
                    message.append("\n");
                    message.append(String.format("Input inY: %d", args.inY));
                    message.append("\n");
                    message.append(String.format("Expected output out: %x %.16f", Float.floatToRawIntBits(args.out), args.out));
                    message.append("\n");
                    message.append(String.format("Actual   output out: %x %.16f", Float.floatToRawIntBits(arrayOut[i * 4 + j]), arrayOut[i * 4 + j]));
                    neededUlf = (int) (Math.abs(args.out - arrayOut[i * 4 + j]) / Math.ulp(args.out) + 0.5);
                    if (neededUlf > ulf) {
                        message.append(String.format(" FAILED, ulf needed %d, specified %d", neededUlf, ulf));
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkPownFloat3Int3Float3" + (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkPownFloat4Int4Float4() {
        Allocation inX = CreateRandomAllocation(mRS, Element.DataType.FLOAT_32, 4, 0x73b4ebb4c78c9191L);
        Allocation inY = CreateRandomAllocation(mRS, Element.DataType.SIGNED_32, 4, 0x73b4ebb4c78c9191L);
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            script.set_gAllocInY(inY);
            script.forEach_testPownFloat4Int4Float4(inX, out);
            verifyResultsPownFloat4Int4Float4(inX, inY, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloat4Int4Float4: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, GetElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            scriptRelaxed.set_gAllocInY(inY);
            scriptRelaxed.forEach_testPownFloat4Int4Float4(inX, out);
            verifyResultsPownFloat4Int4Float4(inX, inY, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testPownFloat4Int4Float4: " + e.toString());
        }
    }

    private void verifyResultsPownFloat4Int4Float4(Allocation inX, Allocation inY, Allocation out, boolean relaxed) {
        float[] arrayInX = new float[INPUTSIZE * 4];
        inX.copyTo(arrayInX);
        int[] arrayInY = new int[INPUTSIZE * 4];
        inY.copyTo(arrayInY);
        float[] arrayOut = new float[INPUTSIZE * 4];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 4 ; j++) {
                // Extract the inputs.
                ArgumentsFloatIntFloat args = new ArgumentsFloatIntFloat();
                args.inX = arrayInX[i * 4 + j];
                args.inY = arrayInY[i * 4 + j];
                // Figure out what the outputs should have been.
                CoreMathVerifier.computePown(args);
                int ulf = relaxed ? args.ulfRelaxed : args.ulf;
                // Figure out what the outputs should have been.
                boolean valid = true;
                int neededUlf = 0;
                neededUlf = (int) (Math.abs(args.out - arrayOut[i * 4 + j]) / Math.ulp(args.out) + 0.5);
                if (neededUlf > ulf) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append(String.format("Input inX: %x %.16f", Float.floatToRawIntBits(args.inX), args.inX));
                    message.append("\n");
                    message.append(String.format("Input inY: %d", args.inY));
                    message.append("\n");
                    message.append(String.format("Expected output out: %x %.16f", Float.floatToRawIntBits(args.out), args.out));
                    message.append("\n");
                    message.append(String.format("Actual   output out: %x %.16f", Float.floatToRawIntBits(arrayOut[i * 4 + j]), arrayOut[i * 4 + j]));
                    neededUlf = (int) (Math.abs(args.out - arrayOut[i * 4 + j]) / Math.ulp(args.out) + 0.5);
                    if (neededUlf > ulf) {
                        message.append(String.format(" FAILED, ulf needed %d, specified %d", neededUlf, ulf));
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkPownFloat4Int4Float4" + (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    public void testPown() {
        checkPownFloatIntFloat();
        checkPownFloat2Int2Float2();
        checkPownFloat3Int3Float3();
        checkPownFloat4Int4Float4();
    }
}