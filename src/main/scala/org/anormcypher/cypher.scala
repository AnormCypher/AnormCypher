package org.anormcypher;

import scala.concurrent.Future
import play.api.libs.iteratee._

case class CypherStatement(query: String, params: Map[String, Any] = Map(), conn:Neo4jConnection = DefaultNeo4jHttp) {
  def apply() = conn.query(this)
  def sync() = conn.querySync(this)
  def on(args: (String, Any)*) = this.copy(params = params ++ args)
}

object Cypher {
  def apply(query:String) = CypherStatement(query)
}

object DefaultNeo4jHttp extends Neo4jConnection {
  var conn = http.Neo4jHttp("http://localhost:7474/db/data/")
  def query(stmt:CypherStatement) = conn.query(stmt)
  def querySync(stmt:CypherStatement) = conn.querySync(stmt)
}

trait Neo4jConnection {
  def query(stmt:CypherStatement):Future[Enumerator[CypherRow]]
  def querySync(stmt:CypherStatement):Iterator[CypherRow]
  def Cypher(query:String) = CypherStatement(query=query, conn=this)
}

trait CypherRow {
  def get(key:String)
  val data:List[Any]
  val columns:List[String]
}
