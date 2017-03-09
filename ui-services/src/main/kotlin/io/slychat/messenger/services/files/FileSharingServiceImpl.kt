package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.kb
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadInfo
import io.slychat.messenger.core.persistence.UploadState
import io.slychat.messenger.services.bindUi
import nl.komponents.kovenant.Promise

class FileSharingServiceImpl(
    private val transferManager: TransferManager,
    private val fileAccess: PlatformFileAccess
) : FileSharingService {
    override fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String): Promise<Unit, Exception> {
        return fileAccess.getFileInfo(localFilePath) bindUi { fileInfo ->
            val cipher = CipherList.defaultDataEncryptionCipher
            val key = generateKey(cipher.keySizeBits)

            val userMetadata = UserMetadata(
                key,
                cipher.id,
                remoteFileDirectory,
                remoteFileName
            )

            //FIXME calc based on file size
            val chunkSize = 128.kb
            val remoteFileSize = getRemoteFileSize(cipher, fileInfo.size, chunkSize)

            val parts = calcUploadParts(cipher, remoteFileSize, chunkSize, MIN_PART_SIZE)

            val fileMetadata = FileMetadata(
                fileInfo.size,
                chunkSize
            )

            val file = RemoteFile(
                generateFileId(),
                generateShareKey(),
                0,
                false,
                userMetadata,
                fileMetadata,
                currentTimestamp(),
                currentTimestamp(),
                remoteFileSize
            )

            val upload = Upload(
                generateUploadId(),
                generateFileId(),
                UploadState.PENDING,
                localFilePath,
                false,
                null,
                parts
            )

            val info = UploadInfo(
                upload,
                file
            )

            transferManager.upload(info)
        }
    }
}
