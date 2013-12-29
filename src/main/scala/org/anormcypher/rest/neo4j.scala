package org.anormcypher.rest

import scala.concurrent.{Future => SFuture}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import org.anormcypher._

case class Neo4jREST(baseURL:String) extends Neo4jConnection {
  def connect = {
    //Neo4jREST(baseURL=baseURL,transURL=transURL,cypherURL=cypherURL)
  }

  def query(stmt:CypherStatement):SFuture[Stream[CypherRow]] = concurrent.future {
    Stream[CypherRow]()
  }

  //TODO make this timeout configurable
  def querySync(stmt:CypherStatement):Stream[CypherRow] = ??? //Await.result[Stream[CypherRow]](query(stmt), 10.second)
}
