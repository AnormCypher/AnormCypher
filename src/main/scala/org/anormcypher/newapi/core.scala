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

  implicit def fun2cvConverter[A, B <: CypherValue](f: (A ⇒ B)) = new CypherValueConverter[A, B] {
    def apply(a: A) = f apply a
  }

  implicit def fun2crConverter[A <: CypherValue, B](f: (CypherRequest[A] ⇒ B)) = new CypherRequestConverter[A, B] {
    def apply(a: CypherRequest[A]) = f apply a
  }
}

trait MapSupport {
  self: CypherSupport ⇒

  sealed trait EmbeddedCypherValue extends CypherValue {
    def value: Any
  }

  case class EmbeddedCypherValueImpl[A](underlying: A) extends EmbeddedCypherValue {
    val value = identity(underlying)
  }

  implicit def any2cv[A] = new CypherValueConverter[A, EmbeddedCypherValue] {
    def apply(obj: A) = EmbeddedCypherValueImpl(obj)
  }

  implicit val cr0p2map = new CypherRequestConverter[Nothing, Map[String, Any]] {
    def apply(obj: CypherRequest[Nothing]) = Map(
      "query" → obj.query,
      "params" → Map.empty
    )
  }
  implicit val cr2map: CypherRequestConverter[EmbeddedCypherValue, Map[String, Any]] =
    (obj: CypherRequest[EmbeddedCypherValue]) ⇒ Map(
      "query" → obj.query,
      "params" → obj.params.groupBy(_._1).mapValues(seq ⇒ seq.map(_._2.value).head)
    )
}

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

trait BatchSupport {
  self: JsonSupport ⇒

  import play.api.libs.json.{Json ⇒ _, _}

  trait BatchCypherRequestConverter[A <: CypherValue, B] extends (BatchCypherRequest[A] ⇒ B)

  case class BatchCypherRequest[A <: CypherValue](queries: Seq[CypherRequest[A]]) {
    def serialize[B](implicit f: BatchCypherRequestConverter[A, B]) = f(this)
  }

  def build[A <: CypherValue](cypherRequests: CypherRequest[A]*) = BatchCypherRequest(cypherRequests)

  implicit val bcrWrites = new Writes[BatchCypherRequest[JsonCypherValue]] {
    def writes(bcr: BatchCypherRequest[JsonCypherValue]) = JsArray(bcr.queries.zipWithIndex.map {
      case (req, index) ⇒ json.obj(
        "method" → "POST",
        "to" → "/cypher",
        "body" → json.toJson(req),
        "id" → index
      )
    }.toSeq)
  }
  implicit val bcr2json = new BatchCypherRequestConverter[JsonCypherValue, JsValue] {
    def apply(obj: BatchCypherRequest[JsonCypherValue]) = json.toJson(obj)
  }
}

trait CypherRest extends CypherSupport with JsonSupport

object CypherRest extends CypherRest

object CypherRestBatch extends CypherRest with BatchSupport

object Cypher extends CypherSupport with MapSupport