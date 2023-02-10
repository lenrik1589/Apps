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

data class Auth(
	val _id: UUID,
	val salt: String,
	var passHash: String,
)

private val mongo = KMongo.createClient(MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.STANDARD).build())

private inline fun withAuth(crossinline body: MongoCollection<Auth>.() -> Auth?): Auth? = mongo.getDatabase("uni").getCollection<Auth>("auth").body()

fun findAuth(uuid: UUID): Auth? = withAuth {
	find<Auth>().find { it._id == uuid }
}

fun findAuth(filter: Bson): Auth? = withAuth {
	findOne(filter)
}

fun insertAuth(auth: Auth) = withAuth {
	insertOne(auth)
	null
}

inline fun updateAuth(uuid: UUID, crossinline block: Auth.() -> Unit){
	insertAuth(findAuth(uuid)!!.apply(block))
}

private val userCache: MutableMap<UUID, User> = mutableMapOf()

data class User(
	val _id: UUID,
	var email: String,
	var name: String?,
	var theme: Theme
)

enum class Theme { dark, light }

private inline fun withUser(crossinline body: MongoCollection<User>.() -> User?): User? = mongo.getDatabase("uni").getCollection<User>("user").body()

fun findUser(uuid: UUID): User? = if(uuid in userCache) userCache[uuid] else withUser {
	find<User>().find { user -> user._id == uuid }
}.also { if(it != null) userCache[it._id] = it }

fun findUser(filter: Bson): User? = withUser {
	findOne(filter)
}.also { if(it != null) userCache[it._id] = it }

fun insertUser(user: User) = withUser {
	userCache[user._id] = user
	insertOne(user)
	null
}

inline fun updateUser(uuid: UUID, crossinline block: User.() -> Unit){
	insertUser(findUser(uuid)!!.apply(block))
}

private val todoCache: MutableMap<UUID, MutableSet<Todo>> = mutableMapOf()

data class Todo(
	val _id: UUID,
	val users: Set<Owner>,
	val lists: Set<TodoList>
) {
	data class Owner(val _id: UUID, var role: String)
	data class TodoList(val name: String, val items: Set<TodoItem>)
	data class TodoItem(val name: String, val complete: Boolean, val due_at: Long?)
}

private inline fun withTodo(crossinline body: MongoCollection<Todo>.() -> Any?): Any? = mongo.getDatabase("uni").getCollection<Todo>("todo").body()

fun findTodoByUser(uuid: UUID): Set<Todo>? = if(uuid in todoCache) todoCache[uuid] else (withTodo {
	setOf(find<Todo>().find { todo -> todo.users.any { user -> user._id == uuid } }!!) 
} as Set<Todo>?).also { if(it != null) todoCache[uuid] = it.toMutableSet() } 

fun findTodo(uuid: UUID): Todo? = withTodo {
	find<Todo>().find { user -> user._id == uuid }
} as Todo?

fun findTodo(filter: Bson): Set<Todo>? = withTodo {
	findOne(filter)
} as Set<Todo>?

fun insertTodo(todo: Todo) = withTodo {
	for((uuid, set) in todoCache){
		if(todo.users.any { it._id == uuid }) set.add(todo)
	}
	insertOne(todo)
	null
}

inline fun updateTodo(uuid: UUID, crossinline block: Todo.() -> Unit){
	insertTodo(findTodo(uuid)!!.apply(block))
}
