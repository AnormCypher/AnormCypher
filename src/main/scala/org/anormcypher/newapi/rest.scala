package org.anormcypher.newapi

trait JsonSupport {
  self: CypherSupport ⇒

  import play.api.libs.json.{Json ⇒ _, _}

  val json = play.api.libs.json.Json

  sealed trait JsonCypherValue extends CypherValue {
    def value: JsValue
  }

  case class JsonCypherValueImpl[A: Writes](underlying: A) extends JsonCypherValue {
    val value = implicitly[Writes[A]].writes(underlying)
  }

  implicit def any2JsonCypherValueConverter[A: Writes] = new CypherValueConverter[A, JsonCypherValue] {
    def apply(obj: A) = JsonCypherValueImpl(obj)
  }

  implicit val cr0pWrites = new Writes[CypherRequest[Nothing]] {
    def writes(o: CypherRequest[Nothing]) = json.obj(
      "query" → o.query,
      "params" → JsObject(Nil)
    )
  }
  implicit val crWrites = new Writes[CypherRequest[JsonCypherValue]] {
    def writes(o: CypherRequest[JsonCypherValue]) = json.obj(
      "query" → o.query,
      "params" → JsObject(o.params.map {
        case (k, v) ⇒ k → v.value
      }.toList)
    )
  }
  implicit val cr0p2json = new CypherRequestConverter[Nothing, JsValue] {
    def apply(obj: CypherRequest[Nothing]) = json.toJson(obj)
  }
  implicit val cr2json: CypherRequestConverter[JsonCypherValue, JsValue] =
    (obj: CypherRequest[JsonCypherValue]) ⇒ json.toJson(obj)
}

trait CypherRest extends CypherSupport with JsonSupport

object CypherRest extends CypherRest