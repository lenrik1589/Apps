package here.lenrik

import here.lenrik.apps.base
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRoutes(){
	routing {
		base("",)
		static("/static") {
			resources("static")
		}
	}
}