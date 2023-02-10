package here.lenrik.apps

import here.lenrik.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import org.litote.kmongo.eq
import redis.clients.jedis.Jedis
import java.io.File
import java.security.MessageDigest
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration

typealias CallPipeline = PipelineContext<Unit, ApplicationCall>

data class AuthData(val uuid: UUID, val token: String): Principal

@Suppress("SpellCheckingInspection")
private const val pepper = "bfdkbfdvvdmwovd"

fun Application.configureAuth() {
	val md = MessageDigest.getInstance("SHA-256")
	val redis = Jedis()
	install(Sessions) {
		cookie<AuthData>("token", directorySessionStorage(File("build/.sessions"))) {
			cookie.path = "/"
			cookie.maxAge = Duration.INFINITE
		}
	}

	authentication {
		form("form") {
			userParamName = "email"
			passwordParamName = "password"
			validate { cred ->
				findUser(User::email eq cred.name)?.run { findAuth(_id) }?.run {
					if (Base64.getDecoder().decode(passHash).contentEquals(md.digest((salt + cred.password + pepper).toByteArray()))) {
						println("login successful, here's your UUID: $_id")
						UserIdPrincipal(_id.toString())
					} else null
				}
			}
			challenge{
				call.respondRedirect((call.request.queryParameters.toMap()["redirect"]?.get(0)?:"/") + "register")
			}
		}
		
		session<AuthData>("session") {
			validate { session ->
				val token = redis.get(session.uuid.toString())
				if(session.token == token) session
				else null
			}
			challenge {
				call.sessions.clear<AuthData>()
				call.respondRedirect("../login")
			}
		}
	}

	routing {
		route("/auth") {
			post("/register") {
				val params = call.receiveParameters()
				val password = params["password"]
				val passwordRepeat = params["password_repeat"]
				val email = params["email"]
				when {
					password == passwordRepeat && findUser(User::email eq email) == null -> {
						call.application.log.info("registering new user with email$email")
						val salt = Random.Default.nextBytes(32).toString(Charsets.UTF_8)
						val newUser = User(UUID.randomUUID(), email!!, email, Theme.light)
						val newAuth = Auth(newUser._id, salt, Base64.getEncoder().encodeToString(md.digest((salt + password + pepper).toByteArray())))
						insertUser(newUser)
						insertAuth(newAuth)
						val token = Base64.getEncoder().encodeToString(Random.Default.nextBytes(32))
						redis.set(newAuth._id.toString(), token)
						call.sessions.set(AuthData(newAuth._id, token))
						call.respondRedirect(call.request.queryParameters.toMap()["redirect"]?.get(0)?:"/")
					}
					else                                                                 -> call.respondRedirect("/")
				}
			}
			route("/logout"){
				suspend fun CallPipeline.action(ignored: Unit) {
					call.principal<UserIdPrincipal>().apply { 
						if(this != null) {
							call.sessions.clear<AuthData>()
							redis.del(call.principal<UserIdPrincipal>()?.name)
						}
						call.respondRedirect(call.request.queryParameters.toMap()["redirect"]?.get(0) ?: "/")
					}
				}
				get(CallPipeline::action)
				post(CallPipeline::action)
			}
		}
		authenticate("form") {
			post("/auth/login") {
				val name = call.principal<UserIdPrincipal>()?.name
				val token = Base64.getEncoder().encodeToString(Random.Default.nextBytes(32))
				call.sessions.set(AuthData(UUID.fromString(name), token))
				redis.set(name, token)
				call.application.log.info("Created token {} for {}", token, name)
				call.respondRedirect(call.request.queryParameters["redirect"] ?: "/")
			}
		}
	}
}