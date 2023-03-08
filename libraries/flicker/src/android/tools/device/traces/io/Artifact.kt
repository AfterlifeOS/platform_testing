/*
 * Copyright (C) 2023 The Android Open Source Project
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

/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.tools.device.traces.io

import android.tools.common.CrossPlatform
import android.tools.common.IScenario
import android.tools.common.io.BUFFER_SIZE
import android.tools.common.io.FLICKER_IO_TAG
import android.tools.common.io.ResultArtifactDescriptor
import android.tools.common.io.RunStatus
import android.tools.device.traces.deleteIfExists
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Artifact(
    runStatus: RunStatus,
    scenario: IScenario,
    outputDir: File,
    files: Map<ResultArtifactDescriptor, File>
) {
    final var file: File
        private set

    init {
        require(!scenario.isEmpty) { "Scenario shouldn't be empty" }
        // Ensure output directory exists
        outputDir.mkdirs()

        val newFileName = "${runStatus.prefix}__$scenario.zip"
        file = outputDir.resolve(newFileName)
        CrossPlatform.log.d(FLICKER_IO_TAG, "Writing artifact file $file")
        createZipFile(file).use { zipOutputStream ->
            files.forEach { (descriptor, artifact) ->
                addFile(zipOutputStream, artifact, nameInArchive = descriptor.fileNameInArtifact)
            }
        }
    }

    final val runStatus: RunStatus
        get() = RunStatus.fromFileName(file.name)

    fun updateStatus(newStatus: RunStatus) {
        val currFile = file
        require(RunStatus.fromFileName(currFile.name) != RunStatus.UNDEFINED) {
            "File name should start with a value from `RunStatus`, instead it was $currFile"
        }
        val newFile = getNewFilePath(newStatus)
        if (currFile != newFile) {
            IoUtils.moveFile(currFile, newFile)
            file = newFile
        }
    }

    private fun createZipFile(file: File): ZipOutputStream {
        return ZipOutputStream(BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE))
    }

    /** updates the artifact status to [newStatus] */
    private fun getNewFilePath(newStatus: RunStatus): File {
        val currTestName = file.name.dropWhile { it != '_' }
        return file.resolveSibling("${newStatus.prefix}__$currTestName")
    }

    private fun addFile(zipOutputStream: ZipOutputStream, artifact: File, nameInArchive: String) {
        CrossPlatform.log.v(FLICKER_IO_TAG, "Adding $artifact with name $nameInArchive to zip")
        val fi = FileInputStream(artifact)
        val inputStream = BufferedInputStream(fi, BUFFER_SIZE)
        inputStream.use {
            val entry = ZipEntry(nameInArchive)
            zipOutputStream.putNextEntry(entry)
            val data = ByteArray(BUFFER_SIZE)
            var count: Int = it.read(data, 0, BUFFER_SIZE)
            while (count != -1) {
                zipOutputStream.write(data, 0, count)
                count = it.read(data, 0, BUFFER_SIZE)
            }
        }
        zipOutputStream.closeEntry()
        artifact.deleteIfExists()
    }

    fun deleteIfExists() {
        file.deleteIfExists()
    }
}
