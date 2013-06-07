package org.anormcypher

trait BatchSupport {
  self: CypherSupport with JsonSupport with CypherRestSupport ⇒

  import play.api.libs.json.{Json ⇒ _, _}

  case class BatchCypherRequest[A](queries: Seq[CypherRequest[A]])

  def build[A <: JsonValue](cypherRequests: CypherRequest[A]*) = BatchCypherRequest(cypherRequests)

  implicit val bcrWrites = new Writes[BatchCypherRequest[JsonValue]] {
    def writes(bcr: BatchCypherRequest[JsonValue]) = JsArray(bcr.queries.zipWithIndex.map {
      case (req, index) ⇒ json.obj(
        "method" → "POST",
        "to" → "/cypher",
        "body" → json.toJson(req),
        "id" → index
      )
    }.toSeq)
  }
  implicit val bcr2json = new Converter[JsonValue, BatchCypherRequest, JsValue] {
    def apply(obj: BatchCypherRequest[JsonValue]) = json.toJson(obj)
  }
}

object CypherRestBatch extends CypherRest with BatchSupport
