package com.example

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testHealth() = testApplication {
        val dbPath = Files.createTempFile("corp-search-test", ".db").absolutePathString()

        environment {
            config = MapApplicationConfig(
                "app.dbPath" to dbPath,
                "app.jwt.issuer" to "corp-search-test",
                "app.jwt.audience" to "corp-search-users",
                "app.jwt.realm" to "corp-search",
                "app.jwt.secret" to "CHANGE_ME_SUPER_SECRET_32+_CHARS",
                "app.jwt.expiresSeconds" to "3600",
                "app.admin.email" to "admin@local",
                "app.admin.password" to "admin12345"
            )
        }

        application {
            module()
        }

        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
