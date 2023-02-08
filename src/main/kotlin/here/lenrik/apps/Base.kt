package here.lenrik.apps

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.css.*
import kotlinx.html.*
import java.util.*

fun Application.configureBase() {
	routing {
		get("/styles.css") {
			call.respondCss {
				body {
					display = Display.flex
					flexDirection = FlexDirection.column
					backgroundColor = Color.aliceBlue
					minHeight = 100.vh
					margin(0.px)
				}
				header {
					width = 100.vw - 10.pct
					height = 100.px
					backgroundColor = Color.red
					padding(5.px, 5.pct)
				}
				rule("header img") {
					height = 100.pct
				}
				footer {
					marginTop = LinearDimension.auto
					backgroundColor = Color.cadetBlue
					width = 100.vw
					display = Display.flex
					flexWrap = FlexWrap.wrap
					justifyContent = JustifyContent.center
					color = Color.white
				}
				rule("div.content") {
					padding(10.px, 5.vw)
					display = Display.flex
				}
				rule("form.form") {
					display = Display.flex
					flexDirection = FlexDirection.column
				}
				rule("form p") {}
			}
		}
	}
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
	respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

inline fun Route.base(name: String, contentPages: Set<String> = emptySet(), crossinline body: (DIV.(ApplicationCall) -> Unit) = DIV::pageNotFound){
	route("/$name"){
		get("/{id?}") {
			println(call.request.queryParameters)
			call.respondHtml {
				head {
					title{ +"Very epic site" }
					styleLink("/styles.css")
				}
				body {
					header {
						a("/$name/home") { img("LOGO", "/static/${name}_logo.png") }
						for(page in contentPages){
							a("/$name/$page") { +page }
						}
						println(call.sessions.get("token"))
						when(call.sessions.get("token")){
							"local"        -> {

							}
							else           -> {
								a("/login"){
									+ "login or register"
								}
							}
						}
					}
					div(classes = "content"){
						when (call.parameters["id"]) {
							"login"         -> loginPage()
							"register"      -> registrationPage()
							in contentPages -> body(call)
							else            -> pageNotFound(call)
						}
					}
					footer { p {
						val a = javaClass.classLoader.getResource("footer.txt")
						if(a != null) {
							+a.readText().lines().first { !it.startsWith("//") }; br
						}
						+ "© 2023${Calendar.getInstance()[Calendar.YEAR].run { if(this > 2023) "-$this" else "" }}"
					} }
				}
			}
		}
	}
}

fun DIV.pageNotFound(call: ApplicationCall) {
	p {
		+"this page does not exist yet, please go "
		a("/") { +"back home" }
	}
}

fun DIV.loginPage() {
	h1 { +"Login" }
	postForm(classes = "form", action = "/auth/login?regirect=/my%20homies%20are%20home") {
		emailInput(name = "email") {}
		passwordInput(name = "password") { attributes["autocomplete"] = "current-password" }
		button(name = "login", type = ButtonType.submit) { +"login" }
		button(name = "register", type = ButtonType.reset) {
			onClick = "window.location.replace('/register')"
			+"register"
		}
	}
}

fun DIV.registrationPage() {
	h1 { +"Register" }
	postForm(classes = "form", action = "/auth/register") {
		onChange = """
			console.log(this)
			this.elements.register.disabled = !(
				this.elements.password_repeat.value == this.elements.password.value &&
				this.elements.eula.checked
			)
		""".trimIndent()
		table {
			tr { td { +"email" }; td { emailInput(name = "email") { attributes["autocomplete"] = "email" } } }
			tr { td { +"password" }; td { passwordInput(name = "password") { attributes["autocomplete"] = "new-password" } } }
			tr { td { +"repeat password" }; td { passwordInput(name = "password_repeat") { attributes["autocomplete"] = "new-password" } } }
			tr { td { +"Agree to eula" }; td { checkBoxInput(name = "eula") } }
		}
		button(name = "register", type = ButtonType.submit) {
			disabled = true
			+"register"
		}
	}
}
