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

/** Failure listener enabling custom logic for each type of failure tracked in the job manager. */
@PublicEvolving
public interface FailureListener {
    /**
     * Method to handle a failure as part of the listener.
     *
     * @param cause the exception that caused this failure
     * @param context the context that includes extra information (e.g., if it was a global failure)
     *     along with the Tags associated with the failure
     */
    void onFailure(Throwable cause, FailureListenerContext context);
}
