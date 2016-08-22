package io.slychat.messenger.services.di

import dagger.Component
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.persistence.InstallationDataPersistenceManager
import io.slychat.messenger.services.AuthenticationService
import io.slychat.messenger.services.LocalAccountDirectory
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.ui.*
import rx.Scheduler
import javax.inject.Singleton

/** Composed of objects which must live for the lifetime of the application. */
@Singleton
@Component(modules = arrayOf(ApplicationModule::class, RelayModule::class, UIServicesModule::class, PlatformModule::class))
interface ApplicationComponent {
    //FIXME used in Sentry.init
    val platformInfo: PlatformInfo

    val uiPlatformInfoService: UIPlatformInfoService

    val uiRegistrationService: UIRegistrationService

    val uiLoginService: UILoginService

    val uiContactsService: UIContactsService

    val uiMessengerService: UIMessengerService

    val uiHistoryService: UIHistoryService

    val uiDevelService: UIDevelService

    val uiNetworkStatusService: UINetworkStatusService

    val uiStateService: UIStateService

    val uiEventService: UIEventService

    val rxScheduler: Scheduler

    val authenticationService: AuthenticationService

    val uiTelephonyService: UITelephonyService

    val uiWindowService: UIWindowService

    val uiLoadService: UILoadService

    val uiInfoService: UIInfoService

    val uiAccountModificationService: UIAccountModificationService

    val uiPlatformService: UIPlatformService

    val platformContacts: PlatformContacts

    //FIXME only used for gcm client in AndroidApp
    val serverUrls: BuildConfig.ServerUrls

    val appConfigService: AppConfigService

    //FIXME only used for gcm client in AndroidApp
    @get:SlyHttp
    val slyHttpClientFactory: HttpClientFactory

    val uiConfigService: UIConfigService

    val uiGroupService: UIGroupService

    val localAccountDirectory: LocalAccountDirectory

    val installationDataPersistenceManager: InstallationDataPersistenceManager

    fun plus(userModule: UserModule): UserComponent
}