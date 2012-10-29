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

  it should "be able to delete and create nodes" in {
    sendQuery(CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' DELETE n;"))
    sendQuery(CypherStatement(query="CREATE (n {anormcyphername:'n'})"))
    val cypherStatement = CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' RETURN n;")
    val results = sendQuery(cypherStatement)
    results.size should equal (1)
    asNode(results(0)("n")).props should equal (Map("anormcyphername"->"n"))
  }

  it should "be able to retrieve properties of nodes" in {
    sendQuery(CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' DELETE n;"))
    sendQuery(CypherStatement(query="CREATE (n {anormcyphername:'n', i:1, arr:[1,2,3], arrc:['a','b','c']})"))
    val cypherStatement = CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' RETURN n;")
    val results = sendQuery(cypherStatement)
    results.size should equal (1)
    asNode(results(0)("n")).props("anormcyphername") should equal ("n")
    asNode(results(0)("n")).props("i") should equal (1)
    //TODO make this stuff use scala classes :(
    val onetwothree = new java.util.ArrayList[Int]
    onetwothree.add(1)
    onetwothree.add(2)
    onetwothree.add(3)
    val abc = new java.util.ArrayList[String]
    abc.add("a")
    abc.add("b")
    abc.add("c")
    asNode(results(0)("n")).props("arr") should equal (onetwothree)
    asNode(results(0)("n")).props("arrc") should equal (abc)
  }

  it should "be able to retrieve collections of nodes" in {
    sendQuery(CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' DELETE n;"))
    sendQuery(CypherStatement(query="CREATE (n {anormcyphername:'n'})"))
    sendQuery(CypherStatement(query="CREATE (n {anormcyphername:'n'})"))
    val cypherStatement = CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' RETURN collect(n);")
    val results = sendQuery(cypherStatement)
    results(0)("collect(n)").asInstanceOf[java.util.ArrayList[Any]].foreach { n =>
      asNode(n)
    }
  }

}
