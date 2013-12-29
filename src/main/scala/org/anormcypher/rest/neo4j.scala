package org.anormcypher.rest

import scala.concurrent.{Future as SFuture}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import org.anormcypher._

case class Neo4jREST(baseURL:String, transURL:String = "", cypherURL:String = "") extends Neo4jConnection {
  def connect = {
    val base = Http(url(baseURL) OK as.Json)()
    println(base)
    Neo4jREST(baseURL=baseURL,transURL=transURL,cypherURL=cypherURL)
  }

  def query(stmt:CypherStatement):Future[Stream[CypherRow]] = future {
    Stream[CypherRow]()
  }

  //TODO make this timeout configurable
  def querySync(stmt:CypherStatement):Stream[CypherRow] = Await.result[Stream[CypherRow]](query(stmt), 10.second)
}
