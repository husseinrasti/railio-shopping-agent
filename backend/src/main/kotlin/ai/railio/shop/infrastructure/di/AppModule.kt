package ai.railio.shop.infrastructure.di

import ai.railio.shop.infrastructure.config.AppConfig
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Root Koin module.
 *
 * [ComponentScan] picks up every `@Single`-annotated service, repository,
 * provider and factory under the app package, so wiring is automatic. The one
 * manual definition is [appConfig], which is constructed from the environment.
 *
 * Referenced from `Application.kt` via the generated `AppModule().module`.
 */
@Module
@ComponentScan("ai.railio.shop")
class AppModule {

    /** Application configuration loaded from environment variables. */
    @Single
    fun appConfig(): AppConfig = AppConfig.fromEnv()
}
