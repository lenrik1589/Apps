package here.lenrik.apps

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlin.collections.set

fun Application.configureToDo() {
	routing {
		base("todo") { call ->
			when (call.parameters["id"]) {
			}
		}
	}
}
