package here.lenrik.apps

import com.mongodb.MongoClientSettings
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.bson.UuidRepresentation
import org.litote.kmongo.KMongo
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
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

private const val pepper = "bfdkbfdvvdmwovd"

fun Application.configureAuth() {
	val md = MessageDigest.getInstance("SHA-256")
	install(Sessions) {
		this.cookie<String>("token") {
			cookie.maxAge = Duration.INFINITE
		}
	}
	
	val createMongo = { KMongo.createClient(MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.STANDARD).build()) }

	authentication {
		form("auth-form") {
			userParamName = "email"
			passwordParamName = "password"
			validate { cred ->
				val mongo = createMongo()
				val users = mongo.getDatabase("uni").getCollection<User>("users")
				users.findOne(User::email eq cred.name)?.run {
					if (passHash == md.digest((salt + cred.password + pepper).toByteArray()).fold("") { str, it -> str + "%02x".format(it) }) {
						println("login successful, here's your UUID: $_id")
						UserIdPrincipal(_id.toString())
					} else
						null
				}.also { mongo.close() }
			}
		}
	}

	routing {
		route("/auth") {
			post("/register") {
				val params = call.receiveParameters()
				when {
					params["password"] != params["password_repeat"] -> call.respondRedirect("/")
					else                                            -> {
						val mongo = createMongo()
						val users = mongo.getDatabase("uni").getCollection<User>("users")
						val user = users.findOne(User::email eq params["email"])
						if (user != null) {
							user.run {
								call.respondRedirect("/")
							}
						} else {
							val salt = Random.Default.nextBytes(32).toString(Charsets.UTF_8)
							users.insertOne(User(UUID.randomUUID(), params["email"], params["email"]!!, salt, md.digest((salt + params["password"] + pepper).toByteArray()).fold("") { str, it -> str + "%02x".format(it) }))
						}
						mongo.close()
					}
				}
				call.sessions.set("token", "wawacat")
				call.respondRedirect("/todo/home")
			}
		}
		authenticate("auth-form") {
			post("/auth/login") {
				call.principal<UserIdPrincipal>()?.name
				call.respondRedirect(call.request.queryParameters["redirect"]?:"/")
			}
		}
	}
}