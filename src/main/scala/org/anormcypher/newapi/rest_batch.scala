package org.anormcypher.newapi

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

object CypherRestBatch extends CypherRest with BatchSupport