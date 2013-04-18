package org.anormcypher.newapi

trait JsonSupport {

  import play.api.libs.json.{Json ⇒ _, _}

  val json = play.api.libs.json.Json

  sealed trait JsonValue extends Value {
    def underlying: JsValue
  }

  implicit def any2JsonCypherValueConverter[A: Format] = new ValueConverter[A, JsonValue] {
    val map = (a: A) => new JsonValue {
      val underlying = implicitly[Format[A]].writes(a)
    }
    val comap = (jcv: JsonValue) => implicitly[Format[A]].reads(jcv.underlying).asOpt
  }
}

trait RestInvoker {
  self: JsonSupport ⇒

  val host = "localhost"
  val port = 7474
  val db = "db/data"
  val username = ""
  val password = ""
  implicit val executionContext = concurrent.ExecutionContext.Implicits.global

  import com.ning.http.client.RequestBuilder
  import dispatch.{host => h, _}

  trait EnhancedUrlVerbs extends RequestVerbs {
    def ^/(segment: String) = {
      val uri = RawUri(subject.build.getUrl)
      val cleanSegment = if (segment startsWith "/") segment substring 1 else segment
      val rawPath = uri.path.orElse(Some("/")).map {
        case u if u endsWith "/" => u concat cleanSegment
        case u => s"$u/$cleanSegment"
      }
      subject.setUrl(uri.copy(path = rawPath).toString())
    }
  }

  implicit class EnhancedRequestVerbs(val subject: RequestBuilder) extends EnhancedUrlVerbs

  private val headers = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json",
    "X-Stream" -> "true",
    "User-Agent" -> "AnormCypher/0.5.0"
  )

  val http = Http.configure {
    builder => builder.setCompressionEnabled(true)
      .setAllowPoolingConnection(true)
      .setRequestTimeoutInMs(5000)
      .setMaximumConnectionsTotal(23)
  }

  val endpoint = (h(host, port) ^/ db) / "cypher" <:< headers as_!(username, password)
}