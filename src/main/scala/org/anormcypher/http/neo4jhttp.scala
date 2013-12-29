package org.anormcypher.http

import scala.concurrent.{Future => SFuture}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import org.anormcypher._

object Neo4jHttp {
  def apply(url:String) = new Neo4jHttp(url)
}

class Neo4jHttp(baseURL:String) extends Neo4jConnection {
  var cypherURL = ""
  var transactionURL = ""

  def query(stmt:CypherStatement):SFuture[Stream[CypherRow]] = concurrent.future {
    Stream[CypherRow]()
  }

  //TODO make this timeout configurable
  def querySync(stmt:CypherStatement):Stream[CypherRow] = ??? //Await.result[Stream[CypherRow]](query(stmt), 10.second)
}
