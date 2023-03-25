/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.core.failurelistener;

import org.apache.flink.annotation.PublicEvolving;

import javax.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * Context class used by the {@link FailureListener} that including the exception and other
 * information associated with the failure.
 */
@PublicEvolving
public interface FailureListenerContext {

    /** @return the exception that caused the failure */
    Throwable getThrowable();

    /** @return true if it was a global failure i.e., involved all task, false otherwise */
    boolean isGlobalFailure();

    /**
     * @return the user {@link ClassLoader} used for code generation, UDF loading and other
     *     operations requiring reflections on user code
     */
    @Nullable
    ClassLoader getUserClassLoader();

    /** @return an Executor pool to run async operations that can potentially be IO-heavy. */
    Executor ioExecutor();
}
