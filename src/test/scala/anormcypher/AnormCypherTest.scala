package anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import anormcypher._
import anormcypher.Neo4jREST._
import scala.collection.JavaConverters._

class AnormCypherSpec extends FlatSpec with ShouldMatchers {
  "Cypher" should "be able to build a CypherStatement with apply" in {
    val query = "START n=node(*) RETURN n;"
    Cypher(query) should equal (CypherStatement(query))
  } 

  it should "be able to build a CypherStatement and send it with apply" in {
    val results = sendQuery(CypherStatement(query="START n=node(*) RETURN n;"))
    Cypher("START n=node(*) RETURN n;")() should equal (results)
  }

  it should "be able to add parameters with .on()" in {
    val query = "START n=node({id}) where n.name! = {test} RETURN n;"
    Cypher(query).on("id"->0, "test"->"hello") should equal (
      CypherStatement(query, Map("id"->0, "test"->"hello")))
  }

  it should "be able to retrieve properties of nodes" in {
  }

  it should "be able to retrieve collections of nodes" in {
  }

}
