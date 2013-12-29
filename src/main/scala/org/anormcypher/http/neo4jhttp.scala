package org.anormcypher.http

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import play.api.libs.iteratee._
import org.anormcypher._

object Neo4jHttp {
  def apply(url:String) = new Neo4jHttp(url)
}

class Neo4jHttp(baseURL:String) extends Neo4jConnection {
  var cypherURL = ""
  var transactionURL = ""

  def query(stmt:CypherStatement):Future[Enumerator[CypherRow]] = future {
    Enumerator[CypherRow]()
  }

  def querySync(stmt:CypherStatement):Iterator[CypherRow] = ??? //Await.result[Stream[CypherRow]](query(stmt), 10.second)
}
