package here.lenrik.apps

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.html.DIV

fun Application.configureToDo() {
	routing {
		authenticate("session", optional = true) {
			base("todo", setOf<Pair<String, DIV.(ApplicationCall) -> Unit>>(
				"home" to { call ->
				}
			).toMap())
		}
	}
}
