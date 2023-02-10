package here.lenrik.apps

import here.lenrik.Theme
import here.lenrik.User
import here.lenrik.findUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*
import kotlinx.html.*
import java.util.*

fun Application.configureBase() {
	routing {
		authenticate("session", optional = true) {
			get("/styles.css") {
				val dark = call.user?.theme == Theme.dark
				call.respondCss {
					body {
						display = Display.flex
						flexDirection = FlexDirection.column
						backgroundColor = if(dark) Color.crimson else Color.aliceBlue
						color = if(dark) Color.white else Color.black
						minHeight = 100.vh
						margin(0.px)
					}
					header {
						display = Display.flex
						width = 100.vw - 10.pct
						height = 4.em
						backgroundColor = if(dark) Color.darkRed else Color.red
						padding(5.px, 5.pct)
					}
					rule("header img") {
						height = 100.pct
					}
					rule("header > *"){
						verticalAlign = VerticalAlign.middle
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
					"form.form" {
						display = Display.flex
						flexDirection = FlexDirection.column
					}
					"form *" {
						backgroundColor  = Color.transparent
					}
					"form button" {
						backgroundColor = Color.white
					}
					":invalid" { 
						backgroundColor = Color.red.lighten(0).withAlpha(0.2)
					}
					"form:invalid"{
						backgroundColor = Color.inherit
					}
				}
			}
		}
	}
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
	respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

fun Route.base(name: String, contentPages: Map<String, DIV.(ApplicationCall) -> Unit> = emptyMap(), requireAuth: Set<String> = emptySet()){
	route("/$name"){
		authenticate("session", optional = true) { 
			get("/{id?}") {
				if(call.parameters["id"] in requireAuth + "user" && call.principal<AuthData>() == null){
					call.respondRedirect("/$name/login")
				} else {
					call.respondHtml {
						head {
							title{ +"Very epic ${name.ifBlank { "site" }}" }
							styleLink("/styles.css")
						}
						body {
							header {
								val block = {
									img("LOGO",
										if(javaClass.classLoader.getResource("static/${name}_logo.png") != null)	
											"/static/${name}_logo.png"
										else
											"/static/home_logo.png"
									)
								}
								if("home" in contentPages) {
									a("/$name/home", classes = "logo") { block() }
								} else {
									block()
								}
								for(page in contentPages.keys){
									a("/$name/$page") { +page }
								}
								val authData = call.principal<AuthData>()
								if(authData == null) {
									a("login?redirect=/$name") {
										+"login or register"
									}
								} else {
									val user = findUser(authData.uuid)
									a("user") {
										+"user (${user?.email})"
									}
								}
							}
							div(classes = "content") {
								when (call.parameters["id"]) {	
									"login"         -> loginPage(name)
									"register"      -> registrationPage(name)
									"user"          -> userPage(name, call)
									in contentPages -> contentPages[call.parameters["id"]]!!(call)
									else            -> pageNotFound(name)
								}
							}
							footer { 
								val a = javaClass.classLoader.getResource("footer.txt")
								if(a != null) {
									+a.readText().lines().first { !it.startsWith("//") }; br
								}
								+ "© 2023${Calendar.getInstance()[Calendar.YEAR].run { if(this > 2023) "-$this" else "" }}"
							}
						}
					}
				}
			}
		}
	}
}

fun DIV.pageNotFound(name: String) {
	+"This page does not exist yet, please go "
	a("/$name") { +"back home" }
	+"."
}

fun DIV.userPage(name: String, call: ApplicationCall){
	val authData = call.principal<AuthData>()
	assert(authData != null)
	val user = findUser(authData!!.uuid)!!
	+(user.name ?:user.email)
}

fun DIV.loginPage(name: String) {
	h1 { +"Login" }
	postForm(classes = "form", action = "/auth/login?redirect=/$name") {
		emailInput(name = "email") {}
		passwordInput(name = "password") { attributes["autocomplete"] = "current-password" }
		button(name = "login", type = ButtonType.submit) { +"login" }
		button(name = "register", type = ButtonType.reset) {
			onClick = "window.location.replace('../register')"
			+"register"
		}
	}
}

fun DIV.registrationPage(name: String) {
	h1 { +"Register" }
	postForm(classes = "form", action = "/auth/register?redirect=/$name") {
		onChange = """let register = this.elements.register
register.disabled = false
let password_repeat = this.elements.password_repeat
if(password_repeat.value != this.elements.password.value){
	password_repeat.setCustomValidity("Passwords do not match")
} else {
	password_repeat.setCustomValidity("")
}
for(element of this.elements){
	register.disabled |= !element[register.disabled?"checkValidity":"reportValidity"]()
}
// let eula = this.elements.eula
// let email = this.elements.email
// this.elements.register.disabled = !(password_repeat.value == password.value && eula.checked)""".trimIndent()
		table {
			tr { td { +"email" }; td { emailInput(name = "email") {
				attributes["autocomplete"] = "email"
				pattern = """^[\w-.]+@([\w-]+\.)+[\w-]{2,}""" + '$'
				minLength = "6"
				required = true
			} } }
			tr { td { +"password" }; td { passwordInput(name = "password") {
				attributes["autocomplete"] = "new-password"
				minLength = "6"
				required = true
			} } }
			tr { td { +"repeat password" }; td { passwordInput(name = "password_repeat") {
				attributes["autocomplete"] = "new-password"
				minLength = "6"
				required = true
			} } }
			tr { td { +"Agree to eula" }; td { checkBoxInput(name = "eula") {
				required = true
			} } }
		}
		button(name = "register", type = ButtonType.submit) {
			disabled = true
			+"register"
		}
	}
}

val ApplicationCall.user: User?
	get() = if(principal<AuthData>() != null) findUser(principal<AuthData>()!!.uuid) else null
