package anormcypher

import dispatch._
import com.codahale.jerkson.Json._
import scala.collection.JavaConverters._


object Neo4jREST {
  // autoconvert to scala types
  implicit def mapWrapper(m: java.util.LinkedHashMap[String,Any]) = m.asScala.toMap
  implicit def arrayWrapper(a: java.util.ArrayList[Any]) = a.asScala.toIndexedSeq

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
    // TODO: make not blow up for exception cases
    val strResult = Http(cypherRequest OK as.String)
    val cypherRESTResult = parse[CypherRESTResult](strResult())
    // build a sequence of Maps for the results
    cypherRESTResult.data.map { 
      d => d.zipWithIndex.map { 
        case (e, idx) => 
          (cypherRESTResult.columns(idx), e) 
      }.toMap 
    }
  }


  // TODO fix type erasure warnings in the matching... 
  def asNode(n:Any):NeoNode = {
    try { 
      n match {
        case node:java.util.LinkedHashMap[String, Any] => {
          val id = node.get("self") match {
            case self:String => self.substring(self.lastIndexOf("/") + 1).toLong
          }
          val data = node.get("data") match {
            case dataMap:java.util.LinkedHashMap[String,Any] => dataMap.asScala.toMap
          }
          NeoNode(id, data)
        }
      }
    } catch {
        case e: Exception => throw new RuntimeException("Unexpected type while building a Node", e)
    }
  }

  def asRelationship(n:Any):NeoRelationship = {
    try { 
      n match {
        case node:java.util.LinkedHashMap[String, Any] => {
          val id = node.get("self") match {
            case self:String => self.substring(self.lastIndexOf("/") + 1).toLong
          }
          val end = node.get("end") match {
            case e:String => e.substring(e.lastIndexOf("/") + 1).toLong
          }
          val start = node.get("start") match {
            case s:String => s.substring(s.lastIndexOf("/") + 1).toLong
          }
          val data = node.get("data") match {
            case dataMap:java.util.LinkedHashMap[String,Any] => dataMap.asScala.toMap
          }
          NeoRelationship(id, data, end, start)
        }
      }
    } catch {
        case e: Exception => throw new RuntimeException("Unexpected type while building a Node", e)
    }
  }
}

case class CypherStatement(query:String, params:Map[String, Any] = Map())
case class CypherRESTResult(columns: Vector[String], data: Seq[Seq[Any]])

case class NeoNode(id:Long, props: Map[String, Any]) 
case class NeoRelationship(id:Long, props: Map[String, Any], start:Long, end:Long) 
