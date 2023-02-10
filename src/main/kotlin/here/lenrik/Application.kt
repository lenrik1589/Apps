package here.lenrik

import here.lenrik.apps.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
	embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
	configureAuth()
	configureRoutes()
	configureBase()
	configureToDo()
//	configureSecurity()
//	configureTemplating()
//	configureRouting()
}
