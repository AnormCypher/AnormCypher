package anormcypher

import dispatch._
import com.codahale.jerkson.Json._
import scala.collection.JavaConverters._
import java.util.LinkedHashMap

// maybe this does need to be a class
object Neo4jREST {
  // TODO support authentication
  var baseURL = "http://localhost:7474/db/data/"

  def setServer(host:String="localhost", port:Int=7474, path:String="/db/data/") = {
    baseURL = "http://" + host + ":" + port + path 
  }

  def setURL(url:String) = {
    baseURL = url
  }

  def sendQuery(stmt: CypherStatement): Seq[Map[String, Any]] = {
    val cypherRequest = url(baseURL + "cypher").POST <:< Map("accept" -> "application/json", "content-type" -> "application/json")
    cypherRequest.setBody(generate(stmt))
    val strResult = Http(cypherRequest OK as.String)
    val cypherRESTResult = parse[CypherRESTResult](strResult())
    // TODO: make not blow up for exception cases
    // build a sequence of Maps for the results
    cypherRESTResult.data.map { 
      d => d.zipWithIndex.map { 
        case (e, idx) => (cypherRESTResult.columns(idx), e)
      }.toMap 
    }
  }

  // TODO clean these up...
  def asNode(n:Any):NeoNode = {
    val node = n.asInstanceOf[LinkedHashMap[String, Any]].asScala
    val self = node("self").asInstanceOf[String]
    val id = self.substring(self.lastIndexOf("/") + 1).toLong
    NeoNode(id, node("data").asInstanceOf[LinkedHashMap[String, Any]].asScala.toMap)
  }

  def asRelationship(r:Any):NeoRelationship = {
    val rel = r.asInstanceOf[LinkedHashMap[String, Any]].asScala
    val self = rel("self").asInstanceOf[String]
    val id = self.substring(self.lastIndexOf("/") + 1).toLong
    val endStr = rel("end").asInstanceOf[String]
    val end = endStr.substring(endStr.lastIndexOf("/") + 1).toLong
    val startStr = rel("start").asInstanceOf[String]
    val start = startStr.substring(startStr.lastIndexOf("/") + 1).toLong
    NeoRelationship(id, rel("data").asInstanceOf[LinkedHashMap[String,Any]].asScala.toMap, start, end)
  }
}

case class CypherStatement(query:String, params:Map[String, Any] = Map())
case class CypherRESTResult(columns: Vector[String], data: Seq[Seq[Any]])

case class NeoNode(id:Long, props: Map[String, Any]) 
case class NeoRelationship(id:Long, props: Map[String, Any], start:Long, end:Long) 
