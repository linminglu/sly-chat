package io.slychat.messenger.ios

import io.slychat.messenger.services.ui.UILoadService

class IOSUILoadService : UILoadService {
    override fun loadComplete() {
        Main.instance.uiLoadComplete()
    }
}