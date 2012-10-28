package anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import anormcypher._
import java.util.LinkedHashMap
import scala.collection.JavaConverters._

class Neo4jRESTSpec extends FlatSpec with ShouldMatchers {
  val cypherStatement = CypherStatement(query="START n=node(*) MATCH n-[r?]->() RETURN n,1,r")
  //TODO a good way to mock the REST interface would be cool; ideas?
  // for now, this test requires neo4j to be running
  "A NeoRESTConnection" should "be able to connect and make a query without parameters" in {
    println(NeoRESTConnection.sendQuery(cypherStatement))
  }

  it should "placeholder" in {
    //val results = NeoRESTConnection.sendQuery(cypherStatement)
    //println(results(1)("n").asInstanceOf[LinkedHashMap[String,Any]].asScala("data"))
  }
}
