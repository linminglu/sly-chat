package io.slychat.messenger.core.crypto

object HKDFInfoList {
    //TODO should add userId to these
    fun keyVault(): HKDFInfo = HKDFInfo("keyvault")

    fun localData(): HKDFInfo = HKDFInfo("local-data")

    fun remoteAddressBookEntries(): HKDFInfo = HKDFInfo("address-book-entries")
}
