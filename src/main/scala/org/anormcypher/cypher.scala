package org.anormcypher;

import scala.concurrent.Future

case class CypherStatement(query: String, params: Map[String, Any] = Map(), conn:Neo4jConnection = DefaultNeo4jREST) {
  def apply() = conn.query(this)
  def sync() = conn.querySync(this)
  def on(args: (String, Any)*) = this.copy(params = params ++ args)
}

object Cypher {
  def apply(query:String) = CypherStatement(query)
}

object DefaultNeo4jREST extends Neo4jConnection {
  var conn = rest.Neo4jREST("http://localhost:7474/db/data/")
  def query(stmt:CypherStatement) = conn.query(stmt)
  def querySync(stmt:CypherStatement) = conn.querySync(stmt)
}

trait Neo4jConnection {
  def query(stmt:CypherStatement):Future[Stream[CypherRow]]
  def querySync(stmt:CypherStatement):Stream[CypherRow]
  def Cypher(query:String) = CypherStatement(query=query, conn=this)
}

trait CypherRow {
  val data:List[Any]
  val columns:List[String]
}
