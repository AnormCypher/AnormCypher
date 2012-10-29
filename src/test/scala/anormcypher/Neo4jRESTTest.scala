package anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import anormcypher._
import anormcypher.Neo4jREST._

class Neo4jRESTSpec extends FlatSpec with ShouldMatchers {
  //TODO a good way to mock the REST interface would be cool; ideas?
  // for now, this test requires neo4j to be running
  "A Neo4jREST" should "be able to make a query without parameters" in {
    val cypherStatement = CypherStatement(query="START n=node(*) RETURN n;")
    sendQuery(cypherStatement)
  }

  it should "be able to delete and create some data" in {
    sendQuery(CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' DELETE n;"))
    sendQuery(CypherStatement(query="CREATE (n {anormcyphername:'n'})"))
    val cypherStatement = CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' RETURN n;")
    val results = sendQuery(cypherStatement)
    results.size should equal (1)
    asNode(results(0)("n")).props should equal (Map("anormcyphername"->"n"))
  }
}
