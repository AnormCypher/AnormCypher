package anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import anormcypher._
import scala.collection.JavaConverters._

class Neo4jRESTSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

  override def beforeEach() = {
    Cypher("CREATE (n {anormcyphername:'n'}) return n;")()
  }

  override def afterEach() = {
   Cypher("START n=node(*) where n.anormcyphername! = 'n' DELETE n;")()
  }

  "A Neo4jREST" should "be able to delete and create nodes" in {
    val cypherStatement = CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' RETURN n;")
    val results = Neo4jREST.sendQuery(cypherStatement).map { row =>
      row[NeoNode]("n")
    }
    results.size should equal (1)
    results(0).props should equal (Map("anormcyphername"->"n"))
  }

  it should "be able to retrieve properties of nodes" in {
    Neo4jREST.sendQuery(CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' DELETE n;"))
    Neo4jREST.sendQuery(CypherStatement(query="CREATE (n {anormcyphername:'n', i:1, arr:[1,2,3], arrc:['a','b','c']})"))
    val cypherStatement = CypherStatement(query="START n=node(*) where n.anormcyphername! = 'n' RETURN n;")
    val results = Neo4jREST.sendQuery(cypherStatement)
    results.size should equal (1)
    val nodes = results.map { row =>
      row[NeoNode]("n")
    }
    nodes(0).props("anormcyphername") should equal ("n")
    nodes(0).props("i") should equal (1)
    nodes(0).props("arr").asInstanceOf[java.util.ArrayList[Int]].asScala should equal (Vector(1,2,3))
    nodes(0).props("arrc").asInstanceOf[java.util.ArrayList[String]].asScala should equal (Vector("a","b","c"))
  }

  it should "be able to retrieve collections of nodes" in {
    Cypher("START n=node(*) where has(n.anormcyphername) DELETE n;")()
    val n = Cypher("CREATE (n {anormcyphername:'n'}) return n;")().map {
      row => row[NeoNode]("n")
    }.head
    val n2 = Cypher("CREATE (n {anormcyphername:'n2'}) return n;")().map {
      row => row[NeoNode]("n")
    }.head
    val cypherStatement = CypherStatement(query="START n=node(*) where has(n.anormcyphername) RETURN collect(n);")
    val results = Neo4jREST.sendQuery(cypherStatement)
    val nodes = results.map { row =>
      row[Seq[NeoNode]]("collect(n)")
    }.head
    nodes.size should equal (2)
    nodes should contain (n)
    nodes should contain (n2)
  }

}
