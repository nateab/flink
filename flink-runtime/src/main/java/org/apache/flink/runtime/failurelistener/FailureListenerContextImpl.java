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

package org.apache.flink.runtime.failurelistener;

import org.apache.flink.core.failurelistener.FailureListenerContext;

import java.util.Collection;
import java.util.HashSet;

import static org.apache.flink.util.Preconditions.checkNotNull;

/** Base implementation of {@link FailureListenerContext} class. */
public class FailureListenerContextImpl implements FailureListenerContext {
    private final Throwable throwable;
    private final boolean globalFailure;
    private final ClassLoader userClassLoader;
    private final Collection<String> failureTags;

    public FailureListenerContextImpl(Throwable throwable, boolean globalFailure) {
        this(throwable, globalFailure, null);
    }

    public FailureListenerContextImpl(
            Throwable throwable, boolean globalFailure, ClassLoader classLoader) {
        this.throwable = checkNotNull(throwable);
        this.globalFailure = globalFailure;
        this.userClassLoader = classLoader;
        this.failureTags = new HashSet();
    }

    @Override
    public Throwable getThrowable() {
        return this.throwable;
    }

    @Override
    public boolean isGlobalFailure() {
        return globalFailure;
    }

    @Override
    public ClassLoader getUserClassLoader() {
        return this.userClassLoader;
    }

    @Override
    public void addTag(String tag) {
        this.failureTags.add(tag);
    }

    @Override
    public Collection<String> getTags() {
        return this.failureTags;
    }
}
