package org.anormcypher.http

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.ws._
import org.anormcypher._
import play.extras.iteratees._

object Neo4jHttp {
  def apply(url:String) = new Neo4jHttp(url)
}

class Neo4jHttp(baseURL:String) extends Neo4jConnection {
  val (cypherURL, transactionURL) = connect

  def connect:(String,String) = {
    Await.result(
      WS.url(baseURL).get().map(response =>
        ((response.json \ "cypher").as[String], (response.json \ "transaction").as[String])
      ), 3.second)
  }

  def querySync(stmt:CypherStatement):List[CypherRow] = {
    var arr = collection.mutable.ArrayBuffer[CypherRow]()
    val it = Iteratee.fold(arr)((arr, e:CypherRow) => arr += e)
    //TODO make this timeout configurable
    Await.result(query(stmt).map(enum => enum.run(it)), 600.second)
    arr.toList
  }

  def query(stmt:CypherStatement):Future[Enumerator[CypherRow]] = {
    import AnormCypherJsonSerialization._
    import JsonBodyParser._
    import JsonIteratees._
    import JsonEnumeratees._

    val futureResponse: Future[Response] = WS.url(cypherURL).post(Json.toJson(stmt))

    var cols = Seq[String]()
    val bodyParser = parser(
      jsObject(
        "columns" -> jsSimpleArray.map(col => cols = col.as[Seq[String]]),
        "data" -> (jsArray(jsValues(jsSimpleArray)) ><> parseItem &>> Iteratee.getChunks[Option[HttpCypherRow]])
      )
    )

    def parseItem: Enumeratee[JsArray, Option[HttpCypherRow]] = Enumeratee.map { arr =>
      for {
        data <- arr.asOpt[Seq[Any]]
      } yield HttpCypherRow(cols.toList, data.toList)
    }

    futureResponse.map{ resp => 
      val stream = resp.ahcResponse.getResponseBodyAsStream
      Enumerator.fromStream(stream)(bodyParser)
    }
  }
}

case class HttpCypherRow(cols:List[String],data:List[Any]) extends CypherRow {
  val map:Map[String,Any] = (cols, data).zipped.toMap

  def get(key:String):Any = {
    map(key)
  }
}
