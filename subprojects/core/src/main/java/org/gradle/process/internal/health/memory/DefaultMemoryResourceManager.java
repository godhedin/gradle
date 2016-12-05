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

import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.event.ListenerManager;

import java.util.ArrayList;
import java.util.List;

public class DefaultMemoryResourceManager implements MemoryResourceManager {

    private static final Logger LOGGER = Logging.getLogger(MemoryResourceManager.class);
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024; // 384M

    private final ListenerManager listenerManager;
    private final double minFreeMemoryPercentage;
    private final Object lock = new Object();
    private final List<MemoryResourceHolder> holders = new ArrayList<MemoryResourceHolder>();
    private OsMemoryStatus osMemoryStatus;

    public DefaultMemoryResourceManager(ListenerManager listenerManager, double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.listenerManager = listenerManager;
        this.minFreeMemoryPercentage = minFreeMemoryPercentage;
        listenerManager.addListener(new StatusListener());
    }

    @Override
    public void addListener(JvmMemoryStatusListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void addListener(OsMemoryStatusListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void removeListener(JvmMemoryStatusListener listener) {
        listenerManager.removeListener(listener);
    }

    @Override
    public void removeListener(OsMemoryStatusListener listener) {
        listenerManager.removeListener(listener);
    }

    @Override
    public void addMemoryHolder(MemoryResourceHolder holder) {
        synchronized (lock) {
            holders.add(holder);
        }
    }

    @Override
    public void removeMemoryHolder(MemoryResourceHolder holder) {
        synchronized (lock) {
            holders.remove(holder);
        }
    }

    @Override
    public void requestFreeMemory(long memoryAmountBytes) {
        if (osMemoryStatus != null) {
            long requestedFreeMemory = getMemoryThresholdInBytes() + (memoryAmountBytes > 0 ? memoryAmountBytes : 0);
            long freeMemory = osMemoryStatus.getFreePhysicalMemory();
            doRequestFreeMemory(requestedFreeMemory, freeMemory);
        } else {
            LOGGER.warn("Free memory requested but no memory status event received yet, cannot proceed");
        }
    }

    private void doRequestFreeMemory(long requestedFreeMemory, long freeMemory) {
        long toReleaseMemory = requestedFreeMemory;
        LOGGER.debug("{} memory requested, {} free", requestedFreeMemory, freeMemory);
        if (freeMemory < requestedFreeMemory) {
            synchronized (lock) {
                for (MemoryResourceHolder holder : holders) {
                    long released = holder.attemptToRelease(toReleaseMemory);
                    toReleaseMemory -= released;
                    freeMemory += released;
                    if (freeMemory >= requestedFreeMemory) {
                        break;
                    }
                }
            }
        }
        LOGGER.debug("{} memory requested, {} released, {} free", requestedFreeMemory, requestedFreeMemory - toReleaseMemory, freeMemory);
    }

    private long getMemoryThresholdInBytes() {
        return Math.max(MIN_THRESHOLD_BYTES, (long) (osMemoryStatus.getTotalPhysicalMemory() * minFreeMemoryPercentage));
    }

    private class StatusListener implements OsMemoryStatusListener {
        @Override
        public void onOsMemoryStatus(OsMemoryStatus newStatus) {
            osMemoryStatus = newStatus;
            long freeMemory = osMemoryStatus.getFreePhysicalMemory();
            long memoryThresholdInBytes = getMemoryThresholdInBytes();
            if (freeMemory < memoryThresholdInBytes) {
                doRequestFreeMemory(memoryThresholdInBytes, freeMemory);
            }
        }
    }
}
