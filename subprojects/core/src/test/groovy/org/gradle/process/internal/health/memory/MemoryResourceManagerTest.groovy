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

package org.gradle.process.internal.health.memory

import org.gradle.internal.event.DefaultListenerManager
import spock.lang.Specification

class MemoryResourceManagerTest extends Specification {

    def listenerManager = new DefaultListenerManager()
    def osMemoryBroadcast = listenerManager.getBroadcaster(OsMemoryStatusListener);
    def memoryResourceManager = new DefaultMemoryResourceManager(listenerManager, 0.25)

    def withFreeOsMemory(MemoryAmount free) {
        osMemoryBroadcast.onOsMemoryStatus(new OsMemoryStatusSnapshot(MemoryAmount.of('8g').bytes, free.bytes))
    }

    def "does not attempt to release memory when claiming 0 memory and free system memory is below threshold"() {
        given:
        def holder = Mock(MemoryResourceHolder)
        memoryResourceManager.addMemoryHolder(holder)

        and:
        withFreeOsMemory(MemoryAmount.of('8g'))

        when:
        memoryResourceManager.requestFreeMemory(0)

        then:
        0 * holder.attemptToRelease(_)
    }

    def "attempt to release memory when claiming 0 memory and free system memory is above threshold"() {
        given:
        def holder = Mock(MemoryResourceHolder)
        memoryResourceManager.addMemoryHolder(holder)

        and:
        withFreeOsMemory(MemoryAmount.of('1g'))

        when:
        memoryResourceManager.requestFreeMemory(0)

        then:
        1 * holder.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes
    }

    def "loop over all memory holders when claiming more memory than releasable"() {
        given:
        def holder1 = Mock(MemoryResourceHolder)
        def holder2 = Mock(MemoryResourceHolder)
        memoryResourceManager.addMemoryHolder(holder1)
        memoryResourceManager.addMemoryHolder(holder2)

        and:
        withFreeOsMemory(MemoryAmount.of(1))

        when:
        memoryResourceManager.requestFreeMemory(MemoryAmount.ofGigaBytes(5).bytes)

        then:
        1 * holder1.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes
        1 * holder2.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes
    }

    def "stop looping over memory holders once claimed memory has been released"() {
        given:
        def holder1 = Mock(MemoryResourceHolder)
        def holder2 = Mock(MemoryResourceHolder)
        memoryResourceManager.addMemoryHolder(holder1)
        memoryResourceManager.addMemoryHolder(holder2)

        and:
        withFreeOsMemory(MemoryAmount.of('1g'))

        when:
        memoryResourceManager.requestFreeMemory(MemoryAmount.ofGigaBytes(3).bytes)

        then:
        1 * holder1.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(4).bytes
        0 * holder2.attemptToRelease(_)
    }
}
