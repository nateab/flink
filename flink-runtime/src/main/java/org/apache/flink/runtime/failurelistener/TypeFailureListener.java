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

import static org.apache.flink.util.ExceptionUtils.findClassFromStackTraceTop;

import java.util.Optional;

import org.apache.flink.core.failurelistener.FailureListener;
import org.apache.flink.core.failurelistener.FailureListenerContext;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FlinkUserCodeClassLoaders;

/**
 * Type implementation of {@link FailureListener} that aims to categorize failures to USER or SYSTEM
 * based on the class of the failure.
 */
public class TypeFailureListener implements FailureListener {

    @Override
    public void onFailure(Throwable cause, FailureListenerContext context) {
        Throwable err = context.getThrowable();
        if (ExceptionUtils.isJvmFatalOrOutOfMemoryError(err)) {
            context.addTag("SYSTEM");
        }
        if (ExceptionUtils.findThrowable(err, ArithmeticException.class).isPresent()) {
            context.addTag("USER");
        }
        // User ClassLoader ExceptionFilter for UDFs
        if (context.getUserClassLoader() != null) {
            // Class in the top of the stack, from which an exception is thrown, is loaded from user artifacts in a submitted JAR
            Optional<Class> classOnStackTop = findClassFromStackTraceTop(cause, context.getUserClassLoader());
            if (classOnStackTop.isPresent() && FlinkUserCodeClassLoaders.isUserCodeClassLoader(classOnStackTop.get()
                    .getClassLoader())) {
                context.addTag("UDF+USER");
            }
        }
        context.addTag("UNKNOWN");
    }
}
