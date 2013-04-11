package org.anormcypher.newapi

trait JsonSupport {
  self: CypherSupport ⇒

  import play.api.libs.json.{Json ⇒ _, _}

  val json = play.api.libs.json.Json

  sealed trait JsonCypherValue extends CypherValue {
    def value: JsValue
  }

  implicit def any2JsonCypherValueConverter[A: Format] = new CypherValueConverter[A, JsonCypherValue] {
    val map = (a: A) => new JsonCypherValue {
      val value = implicitly[Format[A]].writes(a)
    }
    val comap = (jcv: JsonCypherValue) => implicitly[Format[A]].reads(jcv.value).asOpt
  }

  implicit val cRequestNoParamsWrites = new Writes[CypherRequest[Nothing]] {
    def writes(o: CypherRequest[Nothing]) = json.obj(
      "query" → o.query,
      "params" → JsObject(Nil)
    )
  }
  implicit val cRequestWrites = new Writes[CypherRequest[JsonCypherValue]] {
    def writes(o: CypherRequest[JsonCypherValue]) = json.obj(
      "query" → o.query,
      "params" → JsObject(o.params.map {
        case (k, v) ⇒ k → v.value
      }.toList)
    )
  }
  implicit val cRequestNoParams2json = new CypherRequestConverter[Nothing, JsValue] {
    def apply(obj: CypherRequest[Nothing]) = json.toJson(obj)
  }
  implicit val cRequest2json: CypherRequestConverter[JsonCypherValue, JsValue] =
    (obj: CypherRequest[JsonCypherValue]) ⇒ json.toJson(obj)
}

trait CypherRest extends CypherSupport with JsonSupport

object CypherRest extends CypherRest