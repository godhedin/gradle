/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.health.memory;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryStatusBroadcaster {
    private static final Logger LOGGER = Logging.getLogger(MemoryStatusBroadcaster.class);

    public static final int STATUS_INTERVAL = 5;

    private final ScheduledExecutorService scheduledExecutorService;
    private final ListenerBroadcast<JvmMemoryStatusListener> jvmBroadcast;
    private final ListenerBroadcast<OsMemoryStatusListener> osBroadcast;
    private final MemoryInfo memoryInfo;
    private final boolean osMemoryStatusSupported;

    public MemoryStatusBroadcaster(MemoryInfo memoryInfo, ScheduledExecutorService scheduledExecutorService, ListenerManager listenerManager) {
        this.memoryInfo = memoryInfo;
        this.scheduledExecutorService = scheduledExecutorService;
        this.jvmBroadcast = listenerManager.createAnonymousBroadcaster(JvmMemoryStatusListener.class);
        this.osBroadcast = listenerManager.createAnonymousBroadcaster(OsMemoryStatusListener.class);
        this.osMemoryStatusSupported = supportsOsMemoryStatus();
    }

    public void start() {
        scheduledExecutorService.scheduleAtFixedRate(getMemoryCheck(), 0, STATUS_INTERVAL, TimeUnit.SECONDS);
        LOGGER.debug("Memory status broadcaster started");
        if (!osMemoryStatusSupported) {
            LOGGER.warn("This JVM does not support getting OS system memory, so no memory status updates will be broadcast");
        }
    }

    private Runnable getMemoryCheck() {
        return new Runnable() {
            @Override
            public void run() {
                if (osMemoryStatusSupported) {
                    OsMemoryStatus os = memoryInfo.getOsSnapshot();
                    LOGGER.debug("Emitting OS memory status event {}", os);
                    osBroadcast.getSource().onOsMemoryStatus(os);
                }
                JvmMemoryStatus jvm = memoryInfo.getJvmSnapshot();
                LOGGER.debug("Emitting JVM memory status event {}", jvm);
                jvmBroadcast.getSource().onJvmMemoryStatus(jvm);
            }
        };
    }

    private boolean supportsOsMemoryStatus() {
        try {
            memoryInfo.getOsSnapshot();
            return true;
        } catch (UnsupportedOperationException ex) {
            return false;
        }
    }
}
