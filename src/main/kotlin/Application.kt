package com.example

import com.example.config.AppConfig
import com.example.corpsearch.routes.importRoutes
import com.example.db.DatabaseFactory
import com.example.routes.analyticsRoutes
import com.example.routes.auditRoutes
import com.example.routes.authRoutes
import com.example.routes.docRoutes
import com.example.routes.inviteAdminRoutes
import com.example.routes.invitePublicRoutes
import com.example.routes.savedSearchRoutes
import com.example.routes.searchRoutes
import com.example.routes.sourceRbacRoutes
import com.example.routes.sourceRoutes
import com.example.routes.userRoutes
import com.example.security.JwtConfig
import com.example.storage.FileStorage
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.http.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val cfg = AppConfig.from(environment.config)
    DatabaseFactory.init(cfg)
    JwtConfig.init(cfg)

    install(CallLogging) { level = Level.INFO }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }
        )
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        anyHost() // dev only
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal error"))
            )
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = cfg.jwt.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                val email = credential.payload.getClaim("email").asString()
                val role = credential.payload.getClaim("role").asString()
                if (!email.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }
    }

    routing {
        get("/health") { call.respond(mapOf("ok" to true)) }
        invitePublicRoutes()

        authRoutes(cfg)

        authenticate("auth-jwt") {
            sourceRoutes()
            docRoutes()
            searchRoutes()
            userRoutes()
            inviteAdminRoutes(frontendBaseUrl = "http://localhost:5173")
            auditRoutes()
            sourceRbacRoutes()
            savedSearchRoutes()
            analyticsRoutes()
            importRoutes(storageDirPath = FileStorage.DEFAULT_UPLOADS_DIR)
        }
    }
}
