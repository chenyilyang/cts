/*
 * Copyright (C) 2015 The Android Open Source Project
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

public class TestModf extends RSBaseCompute {

    private ScriptC_TestModf script;
    private ScriptC_TestModfRelaxed scriptRelaxed;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        script = new ScriptC_TestModf(mRS);
        scriptRelaxed = new ScriptC_TestModfRelaxed(mRS);
    }

    public class ArgumentsFloatFloatFloat {
        public float inV;
        public Target.Floaty outIntegralPart;
        public Target.Floaty out;
    }

    private void checkModfFloatFloatFloat() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 1, 0xd655dc05ccaef45l, false);
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            script.set_gAllocOutIntegralPart(outIntegralPart);
            script.forEach_testModfFloatFloatFloat(inV, out);
            verifyResultsModfFloatFloatFloat(inV, outIntegralPart, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloatFloatFloat: " + e.toString());
        }
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            scriptRelaxed.set_gAllocOutIntegralPart(outIntegralPart);
            scriptRelaxed.forEach_testModfFloatFloatFloat(inV, out);
            verifyResultsModfFloatFloatFloat(inV, outIntegralPart, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloatFloatFloat: " + e.toString());
        }
    }

    private void verifyResultsModfFloatFloatFloat(Allocation inV, Allocation outIntegralPart, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 1];
        inV.copyTo(arrayInV);
        float[] arrayOutIntegralPart = new float[INPUTSIZE * 1];
        outIntegralPart.copyTo(arrayOutIntegralPart);
        float[] arrayOut = new float[INPUTSIZE * 1];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 1 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inV = arrayInV[i];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeModf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 1 + j])) {
                    valid = false;
                }
                if (!args.out.couldBe(arrayOut[i * 1 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            args.inV, Float.floatToRawIntBits(args.inV), args.inV));
                    message.append("\n");
                    message.append("Expected output outIntegralPart: ");
                    message.append(args.outIntegralPart.toString());
                    message.append("\n");
                    message.append("Actual   output outIntegralPart: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOutIntegralPart[i * 1 + j], Float.floatToRawIntBits(arrayOutIntegralPart[i * 1 + j]), arrayOutIntegralPart[i * 1 + j]));
                    if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 1 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    message.append("Expected output out: ");
                    message.append(args.out.toString());
                    message.append("\n");
                    message.append("Actual   output out: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOut[i * 1 + j], Float.floatToRawIntBits(arrayOut[i * 1 + j]), arrayOut[i * 1 + j]));
                    if (!args.out.couldBe(arrayOut[i * 1 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkModfFloatFloatFloat" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkModfFloat2Float2Float2() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 2, 0x2a1dc519fa16305fl, false);
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            script.set_gAllocOutIntegralPart(outIntegralPart);
            script.forEach_testModfFloat2Float2Float2(inV, out);
            verifyResultsModfFloat2Float2Float2(inV, outIntegralPart, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloat2Float2Float2: " + e.toString());
        }
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            scriptRelaxed.set_gAllocOutIntegralPart(outIntegralPart);
            scriptRelaxed.forEach_testModfFloat2Float2Float2(inV, out);
            verifyResultsModfFloat2Float2Float2(inV, outIntegralPart, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloat2Float2Float2: " + e.toString());
        }
    }

    private void verifyResultsModfFloat2Float2Float2(Allocation inV, Allocation outIntegralPart, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 2];
        inV.copyTo(arrayInV);
        float[] arrayOutIntegralPart = new float[INPUTSIZE * 2];
        outIntegralPart.copyTo(arrayOutIntegralPart);
        float[] arrayOut = new float[INPUTSIZE * 2];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 2 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inV = arrayInV[i * 2 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeModf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 2 + j])) {
                    valid = false;
                }
                if (!args.out.couldBe(arrayOut[i * 2 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            args.inV, Float.floatToRawIntBits(args.inV), args.inV));
                    message.append("\n");
                    message.append("Expected output outIntegralPart: ");
                    message.append(args.outIntegralPart.toString());
                    message.append("\n");
                    message.append("Actual   output outIntegralPart: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOutIntegralPart[i * 2 + j], Float.floatToRawIntBits(arrayOutIntegralPart[i * 2 + j]), arrayOutIntegralPart[i * 2 + j]));
                    if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 2 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    message.append("Expected output out: ");
                    message.append(args.out.toString());
                    message.append("\n");
                    message.append("Actual   output out: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOut[i * 2 + j], Float.floatToRawIntBits(arrayOut[i * 2 + j]), arrayOut[i * 2 + j]));
                    if (!args.out.couldBe(arrayOut[i * 2 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkModfFloat2Float2Float2" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkModfFloat3Float3Float3() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 3, 0x7e82a339fbf43200l, false);
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            script.set_gAllocOutIntegralPart(outIntegralPart);
            script.forEach_testModfFloat3Float3Float3(inV, out);
            verifyResultsModfFloat3Float3Float3(inV, outIntegralPart, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloat3Float3Float3: " + e.toString());
        }
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            scriptRelaxed.set_gAllocOutIntegralPart(outIntegralPart);
            scriptRelaxed.forEach_testModfFloat3Float3Float3(inV, out);
            verifyResultsModfFloat3Float3Float3(inV, outIntegralPart, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloat3Float3Float3: " + e.toString());
        }
    }

    private void verifyResultsModfFloat3Float3Float3(Allocation inV, Allocation outIntegralPart, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 4];
        inV.copyTo(arrayInV);
        float[] arrayOutIntegralPart = new float[INPUTSIZE * 4];
        outIntegralPart.copyTo(arrayOutIntegralPart);
        float[] arrayOut = new float[INPUTSIZE * 4];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 3 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inV = arrayInV[i * 4 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeModf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 4 + j])) {
                    valid = false;
                }
                if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            args.inV, Float.floatToRawIntBits(args.inV), args.inV));
                    message.append("\n");
                    message.append("Expected output outIntegralPart: ");
                    message.append(args.outIntegralPart.toString());
                    message.append("\n");
                    message.append("Actual   output outIntegralPart: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOutIntegralPart[i * 4 + j], Float.floatToRawIntBits(arrayOutIntegralPart[i * 4 + j]), arrayOutIntegralPart[i * 4 + j]));
                    if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 4 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    message.append("Expected output out: ");
                    message.append(args.out.toString());
                    message.append("\n");
                    message.append("Actual   output out: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOut[i * 4 + j], Float.floatToRawIntBits(arrayOut[i * 4 + j]), arrayOut[i * 4 + j]));
                    if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkModfFloat3Float3Float3" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkModfFloat4Float4Float4() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 4, 0xd2e78159fdd233a1l, false);
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            script.set_gAllocOutIntegralPart(outIntegralPart);
            script.forEach_testModfFloat4Float4Float4(inV, out);
            verifyResultsModfFloat4Float4Float4(inV, outIntegralPart, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloat4Float4Float4: " + e.toString());
        }
        try {
            Allocation outIntegralPart = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            scriptRelaxed.set_gAllocOutIntegralPart(outIntegralPart);
            scriptRelaxed.forEach_testModfFloat4Float4Float4(inV, out);
            verifyResultsModfFloat4Float4Float4(inV, outIntegralPart, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testModfFloat4Float4Float4: " + e.toString());
        }
    }

    private void verifyResultsModfFloat4Float4Float4(Allocation inV, Allocation outIntegralPart, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 4];
        inV.copyTo(arrayInV);
        float[] arrayOutIntegralPart = new float[INPUTSIZE * 4];
        outIntegralPart.copyTo(arrayOutIntegralPart);
        float[] arrayOut = new float[INPUTSIZE * 4];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 4 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inV = arrayInV[i * 4 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeModf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 4 + j])) {
                    valid = false;
                }
                if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            args.inV, Float.floatToRawIntBits(args.inV), args.inV));
                    message.append("\n");
                    message.append("Expected output outIntegralPart: ");
                    message.append(args.outIntegralPart.toString());
                    message.append("\n");
                    message.append("Actual   output outIntegralPart: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOutIntegralPart[i * 4 + j], Float.floatToRawIntBits(arrayOutIntegralPart[i * 4 + j]), arrayOutIntegralPart[i * 4 + j]));
                    if (!args.outIntegralPart.couldBe(arrayOutIntegralPart[i * 4 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    message.append("Expected output out: ");
                    message.append(args.out.toString());
                    message.append("\n");
                    message.append("Actual   output out: ");
                    message.append(String.format("%14.8g {%8x} %15a",
                            arrayOut[i * 4 + j], Float.floatToRawIntBits(arrayOut[i * 4 + j]), arrayOut[i * 4 + j]));
                    if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkModfFloat4Float4Float4" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    public void testModf() {
        checkModfFloatFloatFloat();
        checkModfFloat2Float2Float2();
        checkModfFloat3Float3Float3();
        checkModfFloat4Float4Float4();
    }
}
