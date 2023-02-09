package here.lenrik.apps

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoCollection
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import org.bson.UuidRepresentation
import org.litote.kmongo.KMongo
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import redis.clients.jedis.Jedis
import java.io.File
import java.security.MessageDigest
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration

data class User(
	val _id: UUID,
	var name: String?,
	val email: String,
	val salt: String,
	var passHash: String
)

data class AuthData(val uuid: UUID, val token: String): Principal

private const val pepper = "bfdkbfdvvdmwovd"

suspend inline fun withUsers(crossinline body: suspend (MongoCollection<User>) -> Any?): Any? {
	val mongo = KMongo.createClient(MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.STANDARD).build())
	val result = body(mongo.getDatabase("uni").getCollection<User>("users"))
	mongo.close()
	return result
}

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
				withUsers { users ->
					users.findOne(User::email eq cred.name)?.run {
						if (Base64.getDecoder().decode(passHash).contentEquals(md.digest((salt + cred.password + pepper).toByteArray()))) {
							println("login successful, here's your UUID: $_id")
							UserIdPrincipal(_id.toString())
						} else
							null
					}
				} as Principal?
			}
		}
		
		session<AuthData>("session") {
			validate { session ->
				val token = redis.get(session.uuid.toString())
				if(session.token == token)
					session
				else
					null
			}
			challenge { 
				call.respondRedirect("../login")
			}
		}
	}

	routing {
		route("/auth") {
			post("/register") {
				val params = call.receiveParameters()
				println(call.request.queryParameters.toMap())
				val password = params["password"]
				val password_repeat = params["password_repeat"]
				val email = params["email"]
				when {
					password != password_repeat -> call.respondRedirect("/")
					else                        ->  withUsers { users ->
						val user = users.findOne(User::email eq email)
						if (user != null) {
							call.respondRedirect("/")
						} else {
							val salt = Random.Default.nextBytes(32).toString(Charsets.UTF_8)
							val new = User(UUID.randomUUID(), email, email!!, salt, Base64.getEncoder().encodeToString(md.digest((salt + password + pepper).toByteArray())))
							users.insertOne(new)
							val token = Base64.getEncoder().encodeToString(Random.Default.nextBytes(32))
							redis.set(new._id.toString(), token)
							call.sessions.set(AuthData(new._id, token))
						}
					}
				}
				call.respondRedirect(call.request.queryParameters.toMap()["redirect"]?.get(0)?:"/")
			}
		}
		authenticate("form") {
			post("/auth/login") {
				println(call.request.queryParameters.toMap())
				call.principal<UserIdPrincipal>()?.name
				call.respondRedirect(call.request.queryParameters["redirect"]?:"/")
			}
		}
	}
}