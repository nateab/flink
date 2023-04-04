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

package org.apache.flink.runtime.failurelistener;

import org.apache.flink.core.failurelistener.FailureListener;
import org.apache.flink.core.failurelistener.FailureListenerContext;
import org.apache.flink.types.DeserializationException;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FlinkUserCodeClassLoaders;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.apache.flink.runtime.io.network.api.serialization.NonSpanningWrapper.BROKEN_SERIALIZATION_ERROR_MESSAGE;
import static org.apache.flink.util.ExceptionUtils.findClassFromStackTraceTop;

/**
 * Type implementation of {@link FailureListener} that aims to categorize failures to USER or SYSTEM
 * based on the class of the failure.
 */
public class TypeFailureListener implements FailureListener {

    private static final String typeLabelKey = "type_label";
    private static final Set<String> labelKeys = Collections.singleton(typeLabelKey);
    private static final String SERIALIZE_MESSAGE = "serializ";

    @Override
    public Set<String> getOutputKeys() {
        return labelKeys;
    }

    @Override
    public CompletableFuture<Map<String, String>> onFailure(
            Throwable cause, FailureListenerContext context) {
        return CompletableFuture.supplyAsync(
                () -> {
                    Map<String, String> labels = new HashMap();
                    Throwable err = context.getThrowable();
                    if (ExceptionUtils.isJvmFatalOrOutOfMemoryError(err)) {
                        labels.put(typeLabelKey, "SYSTEM");
                    }
                    if (ExceptionUtils.findThrowable(err, ArithmeticException.class).isPresent()) {
                        labels.put(typeLabelKey, "USER");
                    }
                    // User ClassLoader ExceptionFilter for UDFs
                    if (context.getUserClassLoader() != null) {
                        // Class in the top of the stack, from which an exception is thrown, is
                        // loaded from user artifacts in a submitted JAR
                        Optional<Class> classOnStackTop =
                                findClassFromStackTraceTop(cause, context.getUserClassLoader());
                        if (classOnStackTop.isPresent()
                                && FlinkUserCodeClassLoaders.isUserCodeClassLoader(
                                        classOnStackTop.get().getClassLoader())) {
                            labels.put(typeLabelKey, "UDF_USER");
                        }
                    }
                    // This is meant to capture any exception that has "serializ" in the error
                    // message, such as "(de)serialize", "(de)serialization", or "(de)serializable"
                    Optional<Throwable> serializationException =
                            ExceptionUtils.findThrowableWithMessage(cause, SERIALIZE_MESSAGE);
                    if (serializationException.isPresent()) {
                        // check if system error, otherwise it is user error
                        if (serializationException.get().getMessage().contains(BROKEN_SERIALIZATION_ERROR_MESSAGE)){
                            labels.put(typeLabelKey, "SERIALIZATION_SYSTEM");
                        } else {
                            labels.put(typeLabelKey, "SERIALIZATION_USER");
                        }
                    }
                    labels.put(typeLabelKey, "UNKNOWN");
                    return labels;
                },
                context.ioExecutor());
    }
}
