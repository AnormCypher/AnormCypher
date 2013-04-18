package org.anormcypher.newapi

import language.implicitConversions

trait CypherSupport {

  case class CypherRequest[+A](query: String, params: Seq[(String, A)] = Seq.empty) {
    def on[B, C >: A <: Value](pair: (String, B))(implicit converter: ValueConverter[B, C]) =
      CypherRequest(query, params :+ (pair._1 → converter.map(pair._2)))
  }

  case class CypherResponse[+A](columns: Seq[String], rows: Seq[Seq[A]])

  def serialize[A <: Value, B](cypherRequest: CypherRequest[A])(implicit f: Converter[A, CypherRequest, B]) =
    f(cypherRequest)

  def cypher(query: String) = new CypherRequest(query)

  implicit def fun2cValueConverter[A, B <: Value](mapFun: (A ⇒ B), comapFun: (B ⇒ Option[A])) =
    new ValueConverter[A, B] {
      val map = mapFun
      val comap = comapFun
    }

  implicit def fun2cRequestConverter[A <: Value, B](f: (CypherRequest[A] ⇒ B)) =
    new Converter[A, CypherRequest, B] {
      def apply(a: CypherRequest[A]) = f apply a
    }

  implicit def fun2cResponseConverter[A <: Value, B](f: (CypherResponse[A] ⇒ B)) =
    new Converter[A, CypherResponse, B] {
      def apply(a: CypherResponse[A]) = f apply a
    }
}

trait CypherEmbeddedSupport {
  self: CypherSupport with MapSupport ⇒

  implicit val cr2queryAndParams: Converter[IdentityValue, CypherRequest, (String, Map[String, Any])] =
    (obj: CypherRequest[IdentityValue]) ⇒
      obj.query → obj.params.groupBy(_._1).mapValues(seq ⇒ seq.map(_._2.underlying).head)
}

trait CypherRestSupport {
  self: CypherSupport with JsonSupport ⇒

  import play.api.libs.json.{Json ⇒ _, _}

  implicit val cRequestWrites = new Writes[CypherRequest[JsonValue]] {
    def writes(o: CypherRequest[JsonValue]) = json.obj(
      "query" → o.query,
      "params" → JsObject(o.params.map {
        case (k, v) ⇒ k → v.underlying
      }.toList)
    )
  }
  implicit val cRequest2json: Converter[JsonValue, CypherRequest, JsValue] =
    (obj: CypherRequest[JsonValue]) ⇒ json.toJson(obj)
}

trait CypherRest extends CypherSupport with JsonSupport with CypherRestSupport with RestInvoker {

  import dispatch._
  import play.api.libs.json.{Json ⇒ _, _}

  implicit class CypherRestInvoker(cypherRequest: CypherRequest[JsonValue]) {
    def execute[A](implicit converter: Converter[JsonValue, CypherRequest, JsValue]) = http {
      (endpoint << json.prettyPrint(converter(cypherRequest))) OK as.String
    }
  }

}

object CypherEmbedded extends CypherSupport with MapSupport with CypherEmbeddedSupport
object CypherRest extends CypherRest