package anormcypher

import dispatch._
import com.codahale.jerkson.Json._

object NeoRESTConnection {
  // TODO: read from properties
  val baseURL = "http://localhost:7474/db/data/"

  def sendQuery(stmt: CypherStatement): CypherRESTResult = {
    val cypherRequest = url(baseURL + "cypher").POST <:< Map("accept" -> "application/json", "content-type" -> "application/json")
    cypherRequest.setBody(generate(stmt))
    val strResult = Http(cypherRequest OK as.String)
    val restResult = strResult()
    println("RESTResult: " + restResult)
    parse[CypherRESTResult](restResult)
  }
}

case class CypherStatement(query:String, params:Map[String, Any] = Map())

case class CypherRESTResult(columns: Vector[String], data: Seq[Seq[Any]])
