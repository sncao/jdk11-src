/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */



package org.graalvm.compiler.hotspot.test;

import java.util.Arrays;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.GraalCompilerOptions;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationTask;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.java.BciBlockMapping.BciBlock;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class GraalOSRTestBase extends GraalCompilerTest {

    protected void testOSR(OptionValues options, String methodName) {
        testOSR(options, methodName, null);
    }

    protected void testOSR(OptionValues options, String methodName, Object receiver, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        testOSR(options, method, receiver, args);
    }

    protected void testOSR(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        // invalidate any existing compiled code
        method.reprofile();
        compileOSR(options, method);
        Result result = executeExpected(method, receiver, args);
        checkResult(result);
    }

    protected static void compile(DebugContext debug, ResolvedJavaMethod method, int bci) {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        long jvmciEnv = 0L;
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) method, bci, jvmciEnv);
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime.getCompiler();
        CompilationTask task = new CompilationTask(runtime, compiler, request, true, true, debug.getOptions());
        if (method instanceof HotSpotResolvedJavaMethod) {
            HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
            GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
            if (((HotSpotResolvedJavaMethod) method).hasCodeAtLevel(bci, config.compilationLevelFullOptimization)) {
                return;
            }
        }
        HotSpotCompilationRequestResult result = task.runCompilation(debug);
        if (result.getFailure() != null) {
            throw new GraalError(result.getFailureMessage());
        }
    }

    /**
     * Returns the target BCI of the first bytecode backedge. This is where HotSpot triggers
     * on-stack-replacement in case the backedge counter overflows.
     */
    static int getBackedgeBCI(DebugContext debug, ResolvedJavaMethod method) {
        Bytecode code = new ResolvedJavaMethodBytecode(method);
        BytecodeStream stream = new BytecodeStream(code.getCode());
        OptionValues options = debug.getOptions();
        BciBlockMapping bciBlockMapping = BciBlockMapping.create(stream, code, options, debug);

        for (BciBlock block : bciBlockMapping.getBlocks()) {
            if (block.getStartBci() != -1) {
                int bci = block.getEndBci();
                for (BciBlock succ : block.getSuccessors()) {
                    if (succ.getStartBci() != -1) {
                        int succBci = succ.getStartBci();
                        if (succBci < bci) {
                            // back edge
                            return succBci;
                        }
                    }
                }
            }
        }
        TTY.println("Cannot find loop back edge with bytecode loops at:%s", Arrays.toString(bciBlockMapping.getLoopHeaders()));
        TTY.println(new BytecodeDisassembler().disassemble(code));
        return -1;
    }

    protected static void checkResult(Result result) {
        Assert.assertNull("Unexpected exception", result.exception);
        Assert.assertNotNull(result.returnValue);
        Assert.assertTrue(result.returnValue instanceof ReturnValue);
        Assert.assertEquals(ReturnValue.SUCCESS, result.returnValue);
    }

    protected void compileOSR(OptionValues options, ResolvedJavaMethod method) {
        OptionValues goptions = options;
        // Silence diagnostics for permanent bailout errors as they
        // are expected for some OSR tests.
        if (!GraalCompilerOptions.CompilationBailoutAction.hasBeenSet(options)) {
            goptions = new OptionValues(options, GraalCompilerOptions.CompilationBailoutAction, ExceptionAction.Silent);
        }
        // ensure eager resolving
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES, goptions);
        DebugContext debug = graph.getDebug();
        int bci = getBackedgeBCI(debug, method);
        assert bci != -1;
        compile(debug, method, bci);
    }

    protected enum ReturnValue {
        SUCCESS,
        FAILURE,
        SIDE
    }

    public GraalOSRTestBase() {
        super();
    }

    public GraalOSRTestBase(Class<? extends Architecture> arch) {
        super(arch);
    }

    public GraalOSRTestBase(Backend backend) {
        super(backend);
    }

}
