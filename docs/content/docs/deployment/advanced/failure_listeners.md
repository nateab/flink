---
title: "Pluggable Failure Listeners"
nav-title: failure-listeners
nav-parent_id: advanced
nav-pos: 3
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
Flink enables users to extend the default failure handling behavior using the plugin framework.

## Custom failure listeners
Flink provides a pluggable failure listener interface for users to register their custom logic.
The goal is to give flexibility to developers who can now implement their own listener plugins to categorize job failures, expose custom metrics, make calls to external notification systems, and more.
Failure listeners are triggered every time an exception is reported at runtime by the job manager.
Every failure listener may optionally return a string tag associated with the failure that is then exposed via the job manager's Rest interface (e.g., a 'System' tag implying the failure considered as a system error).
For instance, when a Flink runtime failure occurs caused by network error, we can increment the appropriate counter.
With accurate metrics, we now have better insight of platform level metrics e.g., network failures, platform reliability, etc.
The default CountingFailureListener just records the failure count and then emits the metric "numJobFailure" for the job.


### Implement a plugin for your custom listener

To implement a custom plugin for your use-case:

- Add your own FailureListener by implementing the `org.apache.flink.core.failurelistener.FailureListener` interface.

- Add your own FailureListenerFactory by implementing the `org.apache.flink.core.failurelistener.FailureListenerFactory` interface.

- Add a service entry. Create a file `META-INF/services/org.apache.flink.core.failurelistener.FailureListenerFactory`
  which contains the class name of your exception classifier factory class (see the [Java Service Loader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html) docs for more details).


Then, create a jar which includes your `FailureListener`, `FailureListenerFactory`, `META-INF/services/` and all the external dependencies.
Make a directory in `plugins/` of your Flink distribution with an arbitrary name, e.g. "exception-classification", and put the jar into this directory.
See [Flink Plugin]({% link deployment/filesystems/plugins.md %}) for more details.

As a plugin will be loaded in different classloader, log4j is not able to initialized correctly in your plugin. In this case,
you should add the config below in `flink-conf.yaml`:
- `plugin.classloader.parent-first-patterns.additional: org.slf4j`
