package here.lenrik

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoCollection
import org.bson.UuidRepresentation
import org.bson.conversions.Bson
import org.litote.kmongo.KMongo
import org.litote.kmongo.find
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import java.util.*
import kotlin.collections.MutableMap
import kotlin.collections.contains
import kotlin.collections.find
import kotlin.collections.mutableMapOf
import kotlin.collections.set

data class User(
	val _id: UUID,
	var name: String?,
	var email: String,
	val salt: String,
	var passHash: String,
	var dark: Boolean,
)

inline fun withUsers(crossinline body: (MongoCollection<User>) -> Any?): Any? {
	val mongo = KMongo.createClient(MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.STANDARD).build())
	val result = body(mongo.getDatabase("uni").getCollection<User>("users"))
	mongo.close()
	return result
}

private val userCache: MutableMap<UUID, User> = mutableMapOf()

fun findUser(uuid: UUID): User? = if(uuid in userCache) userCache[uuid] else (withUsers {
	it.find<User>().find { user -> user._id == uuid }
} as User?).also { if(it != null) userCache[it._id] = it }

fun findUser(filter: Bson): User? = (withUsers {
	return@withUsers it.findOne(filter)
} as User?).also { if(it != null) userCache[it._id] = it }

fun insertUser(user: User) = withUsers { 
	userCache[user._id] = user
	it.insertOne(user)
}

inline fun updateUser(uuid: UUID, crossinline block: User.() -> Unit){
	val user = findUser(uuid)!!
	user.block()
	insertUser(user)
}