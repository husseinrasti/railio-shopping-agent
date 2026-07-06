package ai.railio.shop

import ai.railio.shop.application.agent.ChatAgent
import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.application.payment.PaymentService
import ai.railio.shop.infrastructure.config.AppConfig
import ai.railio.shop.infrastructure.di.AppModule
import ai.railio.shop.web.configureCallLogging
import ai.railio.shop.web.configureCors
import ai.railio.shop.web.configureSerialization
import ai.railio.shop.web.configureStatusPages
import ai.railio.shop.web.routes.catalogRoutes
import ai.railio.shop.web.routes.chatRoutes
import ai.railio.shop.web.routes.paymentRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ksp.generated.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/** Ktor entrypoint; port and env come from `application.yaml` (+ env overrides). */
fun main(args: Array<String>) = EngineMain.main(args)

/**
 * Application module: installs DI and plugins, then mounts the REST + chat routes.
 * Referenced from `application.yaml`.
 */
fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule().module)
    }

    val config: AppConfig by inject()
    val catalog: CatalogService by inject()
    val payments: PaymentService by inject()
    val agent: ChatAgent by inject()

    configureSerialization()
    configureCors(config)
    configureCallLogging()
    configureStatusPages()

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        catalogRoutes(catalog)
        paymentRoutes(payments)
        chatRoutes(agent)
    }
}
