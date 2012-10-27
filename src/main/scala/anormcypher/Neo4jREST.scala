package anormcypher

import dispatch._
import com.codahale.jerkson.Json._

class NeoRESTConnection {
  // TODO: read from properties
  val baseURL = "http://localhost:7474/db/data/"

  def sendQuery(stmt: CypherStatement): CypherRESTResult = {
    val cypherRequest = url(baseURL + "cypher").POST <:< Map("accept" -> "application/json", "content-type" -> "application/json")
    cypherRequest.addParameter("query", stmt.query)
    cypherRequest.addParameter("params", generate(stmt.params))
    val strResult = Http(cypherRequest OK as.String)
    println(cypherRequest.toString)
    parse[CypherRESTResult](strResult())
  }
}

case class CypherStatement(query:String, params:Map[String, Any] = null)

case class CypherRESTResult(columns: Vector[String], data: Seq[Map[String, Any]])
