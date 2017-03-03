package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import nl.komponents.kovenant.Promise
import rx.Observable

interface StorageService {
    val quota: Observable<Quota>

    val updates: Observable<List<RemoteFile>>

    val syncRunning: Observable<Boolean>

    fun init()

    fun shutdown()

    fun sync()

    fun getFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception>

    fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception>
}