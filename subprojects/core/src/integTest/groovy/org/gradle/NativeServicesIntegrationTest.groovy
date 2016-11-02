/*
 * Copyright 2014 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.nativeintegration.jansi.JansiStorageLocator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue

class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def jansiLibraryLocator = new JansiStorageLocator()
    def nativeDir = new File(executer.gradleUserHomeDir, 'native')
    def jansiStorageSourceTarget
    def jansiDir
    def library

    def setup() {
        jansiStorageSourceTarget = jansiLibraryLocator.locate(nativeDir)
        jansiDir = jansiLibraryLocator.makeVersionSpecificDir(nativeDir)
        library = new File(jansiDir, jansiStorageSourceTarget.jansiLibrary.path)

        executer.requireGradleDistribution().withNoExplicitTmpDir()
    }

    def "native services libs are unpacked to gradle user home dir"() {
        given:
        executer.withArguments('-q')

        when:
        succeeds("help")

        then:
        nativeDir.directory
    }

    @Issue("GRADLE-3573")
    def "jansi library is unpacked to gradle user home dir and isn't overwritten if existing"() {
        String tmpDirJvmOpt = "-Djava.io.tmpdir=$tmpDir.testDirectory.absolutePath"
        executer.withBuildJvmOpts(tmpDirJvmOpt)

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        long lastModified = library.lastModified()

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        lastModified == library.lastModified()
    }

    private void assertNoFilesInTmp() {
        assert tmpDir.testDirectory.listFiles().length == 0
    }
}
