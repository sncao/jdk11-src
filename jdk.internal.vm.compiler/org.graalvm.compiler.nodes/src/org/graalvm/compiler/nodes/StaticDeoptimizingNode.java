/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

public interface StaticDeoptimizingNode extends ValueNodeInterface {

    DeoptimizationReason getReason();

    void setReason(DeoptimizationReason reason);

    DeoptimizationAction getAction();

    void setAction(DeoptimizationAction action);

    Speculation getSpeculation();

    /**
     * Describes how much information is gathered when deoptimization triggers.
     *
     * This enum is {@link Comparable} and orders its element from highest priority to lowest
     * priority.
     */
    enum GuardPriority {
        Speculation,
        Profile,
        None;

        public boolean isHigherPriorityThan(GuardPriority other) {
            return this.compareTo(other) < 0;
        }

        public boolean isLowerPriorityThan(GuardPriority other) {
            return this.compareTo(other) > 0;
        }

        public static GuardPriority highest() {
            return Speculation;
        }
    }

    default GuardPriority computePriority() {
        assert getSpeculation() != null;
        if (!getSpeculation().equals(SpeculationLog.NO_SPECULATION)) {
            return GuardNode.GuardPriority.Speculation;
        }
        switch (getAction()) {
            case InvalidateReprofile:
            case InvalidateRecompile:
                return GuardNode.GuardPriority.Profile;
            case RecompileIfTooManyDeopts:
            case InvalidateStopCompiling:
            case None:
                return GuardNode.GuardPriority.None;
        }
        throw GraalError.shouldNotReachHere();
    }

    static DeoptimizationAction mergeActions(DeoptimizationAction a1, DeoptimizationAction a2) {
        if (a1 == a2) {
            return a1;
        }
        if (a1 == DeoptimizationAction.InvalidateRecompile && a2 == DeoptimizationAction.InvalidateReprofile ||
                        a1 == DeoptimizationAction.InvalidateReprofile && a2 == DeoptimizationAction.InvalidateRecompile) {
            return DeoptimizationAction.InvalidateReprofile;
        }
        return null;
    }
}
