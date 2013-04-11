package org.anormcypher.newapi

import language.implicitConversions

trait CypherValue

trait CypherValueConverter[A, B <: CypherValue] extends (A ⇒ B)

trait CypherRequestConverter[A <: CypherValue, B] extends (CypherRequest[A] ⇒ B)

case class CypherRequest[A <: CypherValue](query: String, params: Seq[(String, A)] = Seq.empty) {
  def on[B, C >: A <: CypherValue](pair: (String, B))(implicit f: CypherValueConverter[B, C]) =
    CypherRequest(query, params :+ (pair._1 → f(pair._2)))

  def serialize[B](implicit f: CypherRequestConverter[A, B]) = f(this)
}

trait CypherSupport {
  def cypher(query: String) = new CypherRequest(query)
}

trait EmbeddedNeo {

  sealed trait EmbeddedCypherValue extends CypherValue {
    def value: Any
  }

  case class EmbeddedCypherValueImpl[A](underlying: A) extends EmbeddedCypherValue {
    val value = identity(underlying)
  }

  implicit def any2EmbeddedCypherValueConverter[A] = new CypherValueConverter[A, EmbeddedCypherValue] {
    def apply(obj: A) = EmbeddedCypherValueImpl(obj)
  }

  implicit val noParamsCypherRequestConverter = new CypherRequestConverter[Nothing, Map[String, Any]] {
    def apply(obj: CypherRequest[Nothing]) = Map(
      "query" → obj.query,
      "params" → Map.empty
    )
  }
  implicit val cypherRequestConverter = new CypherRequestConverter[EmbeddedCypherValue, Map[String, Any]] {
    def apply(obj: CypherRequest[EmbeddedCypherValue]) = Map(
      "query" → obj.query,
      "params" → obj.params.groupBy(_._1).mapValues(seq ⇒ seq.map(_._2.value).head)
    )
  }
}

trait RestNeo {

  import play.api.libs.json.Json._
  import play.api.libs.json.{JsObject, JsValue, Writes}

  sealed trait JsonCypherValue extends CypherValue {
    def value: JsValue
  }

  case class JsonCypherValueImpl[A: Writes](underlying: A) extends JsonCypherValue {
    val value = implicitly[Writes[A]].writes(underlying)
  }

  implicit def any2JsonCypherValueConverter[A: Writes] = new CypherValueConverter[A, JsonCypherValue] {
    def apply(obj: A) = JsonCypherValueImpl(obj)
  }

  implicit val noParamsCypherRequestWrites = new Writes[CypherRequest[Nothing]] {
    def writes(o: CypherRequest[Nothing]) = obj(
      "query" → o.query,
      "params" → JsObject(Nil)
    )
  }
  implicit val cypherRequestWrites = new Writes[CypherRequest[JsonCypherValue]] {
    def writes(o: CypherRequest[JsonCypherValue]) = obj(
      "query" → o.query,
      "params" → JsObject(o.params.map {
        case (k, v) ⇒ k → v.value
      }.toList)
    )
  }

  implicit val noParamsCypherRequestConverter = new CypherRequestConverter[Nothing, JsValue] {
    def apply(obj: CypherRequest[Nothing]) = toJson(obj)
  }
  implicit val cypherRequestConverter = new CypherRequestConverter[JsonCypherValue, JsValue] {
    def apply(obj: CypherRequest[JsonCypherValue]) = toJson(obj)
  }
}

trait BatchSupport {
  self: RestNeo ⇒

  import play.api.libs.json.Json._
  import play.api.libs.json._

  trait BatchCypherRequestConverter[A <: CypherValue, B] extends (BatchCypherRequest[A] ⇒ B)

  case class BatchCypherRequest[A <: CypherValue](queries: Seq[CypherRequest[A]]) {
    def serialize[B](implicit f: BatchCypherRequestConverter[A, B]) = f(this)
  }

  def build[A <: CypherValue](cypherRequests: CypherRequest[A]*) = BatchCypherRequest(cypherRequests)

  implicit val batchCypherRequestWrites = new Writes[BatchCypherRequest[JsonCypherValue]] {
    def writes(o: BatchCypherRequest[JsonCypherValue]) = JsArray(o.queries.zipWithIndex.map {
      case (req, index) ⇒ obj(
        "method" → "POST",
        "to" → "/cypher",
        "body" → toJson(req),
        "id" → index
      )
    }.toSeq)
  }

  implicit val batchCypherRequestConverter = new BatchCypherRequestConverter[JsonCypherValue, JsValue] {
    def apply(obj: BatchCypherRequest[JsonCypherValue]) = toJson(obj)
  }
}

trait CypherRest extends RestNeo with CypherSupport {
  val json = play.api.libs.json.Json
}

object CypherRest extends CypherRest

object CypherRestBatch extends CypherRest with BatchSupport

object Cypher extends EmbeddedNeo with CypherSupport