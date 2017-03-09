@file:JvmName("FilesUtils")
package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.mb
import io.slychat.messenger.core.persistence.UploadPart
import java.util.*

//S3 limitation; each part except the last must be >= 5mb
internal val MIN_PART_SIZE = 5.mb

internal fun getRemoteFileSize(cipher: Cipher, fileSize: Long, chunkSize: Int): Long {
    require(fileSize > 0) { "fileSize must be > 0, got $fileSize" }
    require(chunkSize > 0) { "chunkSize must be > 0, got $chunkSize" }

    val plainChunkSize = cipher.getInputSizeForOutput(chunkSize)

    val chunkCount = fileSize / plainChunkSize
    val rem = fileSize % plainChunkSize
    val extra = if (rem > 0)
        cipher.getEncryptedSize(rem.toInt())
    else
        0

    val evenChunks = chunkSize * chunkCount

    return evenChunks + extra
}

/**
 * This will calculate the UploadParts for a given file.
 *
 * First, we extract how large each plaintext chunk will be based on the given cipher and the encrypted chunk size.
 * Then we calculate how many encrypted chunks fit into the minimum part size.
 * Then we generate upload parts for each of these full parts, followed by calculating the final part using the
 * remaining even chunks and any remaining bytes that didn't fit into a full encrypted chunk.
 */
internal fun calcUploadParts(cipher: Cipher, fileSize: Long, encryptedChunkSize: Int, minPartSize: Int): List<UploadPart> {
    require(encryptedChunkSize <= minPartSize) { "encryptedChunkSize ($encryptedChunkSize) should be <= minPartSize ($minPartSize)" }
    require((minPartSize % encryptedChunkSize) == 0) { "encryptedChunkSize ($encryptedChunkSize) must be a multiple of minPartSize ($minPartSize)" }

    val chunksPerPart = (minPartSize / encryptedChunkSize).toLong()

    val plainChunkSize = cipher.getInputSizeForOutput(encryptedChunkSize)

    if (fileSize < plainChunkSize) {
        return listOf(UploadPart(
            1,
            0,
            fileSize,
            cipher.getEncryptedSize(fileSize.toInt()).toLong(),
            false
        ))
    }

    val evenChunks = fileSize / plainChunkSize
    //if > 0, then this is the bytes that make up the final chunk
    val lastChunkSize = fileSize % plainChunkSize

    val evenParts = (evenChunks / chunksPerPart).toInt()
    //remaining chunks (plus one more if lastChunkSize is > 1) for the final part
    val pr = evenChunks % chunksPerPart

    val parts = ArrayList<UploadPart>()
    var currentOffset = 0L

    (1..evenParts).forEach {
        val p = UploadPart(it, currentOffset, chunksPerPart * plainChunkSize, chunksPerPart * encryptedChunkSize, false)
        currentOffset += p.localSize
        parts.add(p)
    }

    if (pr > 0) {
        val lastChunkEncryptedSize = if (lastChunkSize > 0)
            cipher.getEncryptedSize(lastChunkSize.toInt())
        else
            0

        parts.add(UploadPart(evenParts + 1, currentOffset, (pr * plainChunkSize) + lastChunkSize, (pr * encryptedChunkSize) + lastChunkEncryptedSize, false))
    }

    return parts
}
