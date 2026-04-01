package com.example.routes

import com.example.db.Repos
import com.example.security.Passwords
import com.example.util.requireTrimmed
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class UserDto(val id: Long, val email: String, val role: String)

@Serializable data class CreateUserReq(
    val email: String,
    val password: String,
    val role: String // ADMIN / USER
)

@Serializable data class UpdateUserRoleReq(val role: String)
@Serializable data class ResetPasswordReq(val newPassword: String)

private fun ApplicationCall.actorEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

private fun ApplicationCall.actorRole(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

private fun requireAdmin(call: ApplicationCall): Boolean = (call.actorRole() == "ADMIN")

fun Route.userRoutes() {
    route("/api/users") {

        get {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@get
            }
            val users = Repos.listUsers().map { UserDto(it.id, it.email, it.role) }
            call.respond(users)
        }

        post {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@post
            }

            val req = call.receive<CreateUserReq>()
            val email = requireTrimmed(req.email, "email", 3, 255)
            val password = requireTrimmed(req.password, "password", 6, 200)
            val role = requireTrimmed(req.role, "role", 3, 32).uppercase()

            if (role != "ADMIN" && role != "USER") {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "role must be ADMIN or USER"))
                return@post
            }

            val exists = Repos.findUserByEmail(email)
            if (exists != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "User already exists"))
                return@post
            }

            val id = Repos.createUser(email, Passwords.hash(password), role)
            Repos.insertAudit(call.actorEmail(), "CREATE_USER", "id=$id email=$email role=$role")

            call.respond(HttpStatusCode.Created, UserDto(id, email, role))
        }

        put("/{id}/role") {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@put
            }

            val id = call.parameters["id"]!!.toLong()
            val req = call.receive<UpdateUserRoleReq>()
            val role = requireTrimmed(req.role, "role", 3, 32).uppercase()
            if (role != "ADMIN" && role != "USER") {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "role must be ADMIN or USER"))
                return@put
            }

            if (role != "ADMIN" && Repos.isLastAdmin(id)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "You cannot demote the last ADMIN"))
                return@put
            }

            val ok = Repos.updateUserRole(id, role)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@put
            }

            Repos.insertAudit(call.actorEmail(), "UPDATE_USER_ROLE", "id=$id role=$role")
            call.respond(mapOf("ok" to true))
        }

        put("/{id}/password") {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@put
            }

            val id = call.parameters["id"]!!.toLong()
            val req = call.receive<ResetPasswordReq>()
            val newPassword = requireTrimmed(req.newPassword, "newPassword", 6, 200)

            val ok = Repos.resetUserPassword(id, Passwords.hash(newPassword))
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@put
            }

            Repos.insertAudit(call.actorEmail(), "RESET_USER_PASSWORD", "id=$id")
            call.respond(mapOf("ok" to true))
        }

        delete("/{id}") {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@delete
            }

            val id = call.parameters["id"]!!.toLong()

            val actor = call.actorEmail()
            val me = actor?.let { Repos.findUserByEmail(it) }
            if (me != null && me.id == id) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "You cannot delete yourself"))
                return@delete
            }

            if (Repos.isLastAdmin(id)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "You cannot delete the last ADMIN"))
                return@delete
            }

            val ok = Repos.deleteUser(id)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@delete
            }

            Repos.insertAudit(call.actorEmail(), "DELETE_USER", "id=$id")
            call.respond(mapOf("ok" to true))
        }
    }
}
