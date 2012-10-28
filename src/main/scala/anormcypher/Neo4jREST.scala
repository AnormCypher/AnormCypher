package anormcypher

import dispatch._
import com.codahale.jerkson.Json._
import java.util.LinkedHashMap
import scala.collection.JavaConverters._

object NeoRESTConnection {
  // TODO: read from properties
  val baseURL = "http://localhost:7474/db/data/"

  def sendQuery(stmt: CypherStatement): Seq[Map[String, Any]] = {
    val cypherRequest = url(baseURL + "cypher").POST <:< Map("accept" -> "application/json", "content-type" -> "application/json")
    cypherRequest.setBody(generate(stmt))
    val strResult = Http(cypherRequest OK as.String)
    // TODO: make not blow for exception cases
    val cypherRESTResult = parse[CypherRESTResult](strResult())
    // build a sequence of Maps for the results
    cypherRESTResult.data.map { 
      d => d.zipWithIndex.map { 
        case (e, idx) => (cypherRESTResult.columns(idx), e)
      }.toMap 
    }
  }

  // TODO: convert this stuff to use json4s, or something that
  // doesn't force the use of Java->Scala conversions.
  // didn't realize Jerkson did that.
  def asNeoNode(n:Any):NeoNode = {
    val node = n.asInstanceOf[LinkedHashMap[String, Any]].asScala
    val self = node("self").asInstanceOf[String]
    val id = self.substring(self.lastIndexOf("/") + 1).toLong
    NeoNode(id, node("data").asInstanceOf[LinkedHashMap[String, Any]].asScala.toMap)
  }

  def asNeoRelationship(r:Any):NeoRelationship = {
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
