/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.executiongraph.failover.flip1;

import java.util.Collections;

import org.apache.flink.core.failurelistener.FailureListener;
import org.apache.flink.core.failurelistener.FailureListenerContext;
import org.apache.flink.runtime.JobException;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.executiongraph.Execution;
import org.apache.flink.runtime.failurelistener.FailureListenerContextImpl;
import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;
import org.apache.flink.runtime.scheduler.strategy.SchedulingExecutionVertex;
import org.apache.flink.runtime.scheduler.strategy.SchedulingTopology;
import org.apache.flink.runtime.throwable.ThrowableClassifier;
import org.apache.flink.runtime.throwable.ThrowableType;
import org.apache.flink.util.IterableUtils;
import org.apache.flink.util.concurrent.FutureUtils;
import org.apache.flink.util.concurrent.FutureUtils.ConjunctFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * This handler deals with task failures to return a {@link FailureHandlingResult} which contains
 * tasks to restart to recover from failures.
 */
public class ExecutionFailureHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final SchedulingTopology schedulingTopology;

    /** Strategy to judge which tasks should be restarted. */
    private final FailoverStrategy failoverStrategy;

    /** Strategy to judge whether and when a restarting should be done. */
    private final RestartBackoffTimeStrategy restartBackoffTimeStrategy;

    /** Number of all restarts happened since this job is submitted. */
    private long numberOfRestarts;

    private ConjunctFuture<Collection<Map<String, String>>> failureFuturesCollection;
    private int currentFailureAttempt;
    private final Set<FailureListener> failureListeners;
    private final ComponentMainThreadExecutor mainThreadExecutor;

    private final Executor ioExecutor;

    /**
     * Creates the handler to deal with task failures.
     *
     * @param schedulingTopology contains the topology info for failover
     * @param failoverStrategy helps to decide tasks to restart on task failures
     * @param restartBackoffTimeStrategy helps to decide whether to restart failed tasks and the
     *     restarting delay
     */
    public ExecutionFailureHandler(
            final SchedulingTopology schedulingTopology,
            final FailoverStrategy failoverStrategy,
            final RestartBackoffTimeStrategy restartBackoffTimeStrategy,
            final ComponentMainThreadExecutor mainThreadExecutor,
            final Executor ioExecutor) {

        this.schedulingTopology = checkNotNull(schedulingTopology);
        this.failoverStrategy = checkNotNull(failoverStrategy);
        this.restartBackoffTimeStrategy = checkNotNull(restartBackoffTimeStrategy);
        this.mainThreadExecutor = mainThreadExecutor;
        this.ioExecutor = ioExecutor;
        this.failureListeners = new HashSet<>();
    }

    /**
     * Return result of failure handling. Can be a set of task vertices to restart and a delay of
     * the restarting. Or that the failure is not recoverable and the reason for it.
     *
     * @param failedExecution is the failed execution
     * @param cause of the task failure
     * @param timestamp of the task failure
     * @return result of the failure handling
     */
    public CompletableFuture<FailureHandlingResult> getFailureHandlingResult(
            Execution failedExecution, Throwable cause, long timestamp) {
        return handleFailure(
                failedExecution,
                cause,
                timestamp,
                failoverStrategy.getTasksNeedingRestart(failedExecution.getVertex().getID(), cause),
                false);
    }

    /**
     * Return result of failure handling on a global failure. Can be a set of task vertices to
     * restart and a delay of the restarting. Or that the failure is not recoverable and the reason
     * for it.
     *
     * @param cause of the task failure
     * @param timestamp of the task failure
     * @return result of the failure handling
     */
    public CompletableFuture<FailureHandlingResult> getGlobalFailureHandlingResult(
            final Throwable cause, long timestamp) {
        return handleFailure(
                null,
                cause,
                timestamp,
                IterableUtils.toStream(schedulingTopology.getVertices())
                        .map(SchedulingExecutionVertex::getId)
                        .collect(Collectors.toSet()),
                true);
    }

    /** @param failureListener the failure listener to be registered */
    public void registerFailureListener(FailureListener failureListener) {
        failureListeners.add(failureListener);
    }

    private CompletableFuture<FailureHandlingResult> handleFailure(
            @Nullable final Execution failedExecution,
            final Throwable cause,
            long timestamp,
            final Set<ExecutionVertexID> verticesToRestart,
            final boolean globalFailure) {
        /**
         * When multiple tasks fail for the same attempt ONLY the first exception should be looked
         * into for deciding the next step. To achieve that we use the same Future for the same
         * attempt and update it when a new attempt comes in. Note: failedExecution may be null
         * (global) but no further attempts will be conducted {@link
         * FailureHandlingResult#unrecoverable}.
         */
        boolean isFirstAttemptFailure = isFirstAttemptFailure(failedExecution);
        if (isFirstAttemptFailure) {
            final Collection<CompletableFuture<Map<String, String>>> resultFutures =
                    new ArrayList<>();
            FailureListenerContext cntx =
                    new FailureListenerContextImpl(
                            cause, globalFailure, ioExecutor, getExecClassLoader(failedExecution));
            for (FailureListener listener : failureListeners) {
                resultFutures.add(listener.onFailure(cause, cntx).thenApply(listerOut -> {
                    if (listener.getOutputKeys().containsAll(listerOut.keySet())) {
                        return listerOut;
                    }
                    log.warn(
                            "Ignoring Listener {} violating key output {}",
                            listener.getClass(),
                            listerOut.keySet());
                    return Collections.emptyMap();
                }));
            }
            this.failureFuturesCollection = FutureUtils.combineAll(resultFutures);
            this.currentFailureAttempt =
                    failedExecution != null ? failedExecution.getAttemptId().getAttemptNumber() : 0;
        }

        return failureFuturesCollection.thenApplyAsync(
                results -> {
                    final Map<String, String> mergedLabels = new HashMap<>();
                    results.forEach(result -> mergedLabels.putAll(result));
                    return handleFailure(
                            failedExecution,
                            cause,
                            timestamp,
                            verticesToRestart,
                            globalFailure,
                            isFirstAttemptFailure,
                            mergedLabels);
                },
                mainThreadExecutor);
    }

    private FailureHandlingResult handleFailure(
            @Nullable final Execution failedExecution,
            final Throwable cause,
            long timestamp,
            final Set<ExecutionVertexID> verticesToRestart,
            final boolean globalFailure,
            final boolean isFirstAttemptFailure,
            final Map<String, String> labels) {

        if (isUnrecoverableError(cause)) {
            return FailureHandlingResult.unrecoverable(
                    failedExecution,
                    new JobException("The failure is not recoverable", cause),
                    labels.values(),
                    timestamp,
                    globalFailure);
        }

        if (restartBackoffTimeStrategy.canRestart()) {
            if (isFirstAttemptFailure) {
                restartBackoffTimeStrategy.notifyFailure(cause);
                numberOfRestarts++;
            }
            return FailureHandlingResult.restartable(
                    failedExecution,
                    cause,
                    labels.values(),
                    timestamp,
                    verticesToRestart,
                    restartBackoffTimeStrategy.getBackoffTime(),
                    globalFailure);
        } else {
            if (isFirstAttemptFailure) {
                restartBackoffTimeStrategy.notifyFailure(cause);
            }
            return FailureHandlingResult.unrecoverable(
                    failedExecution,
                    new JobException(
                            "Recovery is suppressed by " + restartBackoffTimeStrategy, cause),
                    labels.values(),
                    timestamp,
                    globalFailure);
        }
    }

    public static boolean isUnrecoverableError(Throwable cause) {
        Optional<Throwable> unrecoverableError =
                ThrowableClassifier.findThrowableOfThrowableType(
                        cause, ThrowableType.NonRecoverableError);
        return unrecoverableError.isPresent();
    }

    /** @return the UserClassLoader found in the given Execution param */
    private static ClassLoader getExecClassLoader(Execution exec) {
        if (exec == null
                || exec.getVertex() == null
                || exec.getVertex().getExecutionGraphAccessor() == null) {
            return null;
        }
        return exec.getVertex().getExecutionGraphAccessor().getUserClassLoader();
    }

    public boolean isFirstAttemptFailure(Execution failedExecution) {
        if (failureFuturesCollection == null
                || (failedExecution != null
                        && failedExecution.getAttemptId().getAttemptNumber()
                                != currentFailureAttempt)) {
            return true;
        }
        return false;
    }

    public long getNumberOfRestarts() {
        return numberOfRestarts;
    }
}
