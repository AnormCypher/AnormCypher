package org.anormcypher

import dispatch._
import com.codahale.jerkson.Json._
import scala.collection.JavaConverters._
import org.anormcypher.MayErr._

object Neo4jREST {
  // TODO support authentication
  var baseURL = "http://localhost:7474/db/data/"

  def setServer(host:String="localhost", port:Int=7474, path:String="/db/data/") = {
    baseURL = "http://" + host + ":" + port + path 
  }

  def setURL(url:String) = {
    baseURL = url
  }

  def sendQuery(stmt: CypherStatement): Stream[CypherResultRow] = {
    val cypherRequest = url(baseURL + "cypher").POST <:< Map("accept" -> "application/json", "content-type" -> "application/json", "X-Stream" -> "true")
    cypherRequest.setBody(generate(stmt))
    val result = Http(cypherRequest OK as.String).either
    val strResult = result() match {
      case Right(content)         => { /*println("Content: " + content);*/ content; }
      case Left(content) => {throw new RuntimeException("error:" + content) }
    }
    val cypherRESTResult = parse[CypherRESTResult](strResult)
    val metaDataItems = cypherRESTResult.columns.map {
      c => MetaDataItem(c, false, "String") 
    }.toList
    val metaData = MetaData(metaDataItems)
    val data = cypherRESTResult.data.map {
      d => CypherResultRow(metaData, d.toList)
    }.toStream
    data
  }

  // TODO fix type erasure warnings in the matching... 
  def asNode(n:Any):MayErr[CypherRequestError, NeoNode] = {
    try { 
      n match {
        case node:java.util.LinkedHashMap[String, Any] => {
          val id = node.get("self") match {
            case self:String => self.substring(self.lastIndexOf("/") + 1).toLong
          }
          val data = node.get("data") match {
            case dataMap:java.util.LinkedHashMap[String,Any] => dataMap.asScala.toMap
          }
          Right(NeoNode(id, data))
        }
      }
    } catch {
        case e: Exception => Left(TypeDoesNotMatch("Unexpected type while building a Node"))
    }
  }

  def asRelationship(n:Any):MayErr[CypherRequestError, NeoRelationship] = {
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
          Right(NeoRelationship(id, data, start, end))
        }
      }
    } catch {
      case e: Exception => Left(TypeDoesNotMatch("Unexpected type while building a relationship"))
    }
  }
}

case class CypherRESTResult(columns: Vector[String], data: Seq[Seq[Any]])

case class NeoNode(id:Long, props: Map[String, Any]) 
case class NeoRelationship(id:Long, props: Map[String, Any], start:Long, end:Long) 
