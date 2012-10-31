package anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import anormcypher._
import anormcypher.Neo4jREST._
import scala.collection.JavaConverters._

class AnormCypherSpec extends FlatSpec with ShouldMatchers with BeforeAndAfter {
  before {
    // initialize some test data
    Cypher("""create 
      (us {type:"Country", name:"United States", code:"USA", tag:"anormcyphertest"}),
      (us {type:"Country", name:"Germany", code:"DEU", tag:"anormcyphertest"}),
      (us {type:"Country", name:"France", code:"FRA", tag:"anormcyphertest"});
      """)()
  }

  after {
    // delete the test data
    Cypher("""start n=node(*)
      where n.tag! = "anormcyphertest"
      delete n;
      """)()
  }

  "Cypher" should "be able to build a CypherStatement with apply" in {
    val query = """
      START n=node(*) 
      RETURN n;
      """
    Cypher(query) should equal (CypherStatement(query))
  } 

  it should "be able to build a CypherStatement and send it with apply" in {
    val query = """
      START n=node(0) 
      RETURN n;
      """
    Cypher(query)().size should equal (1)
  }

  it should "be able to add parameters with .on()" in {
    val query = """
      start n=node({id}) 
      where n.name! = {test} 
      return n;
      """
    Cypher(query).on("id"->0, "test"->"hello") should equal (
      CypherStatement(query, Map("id"->0, "test"->"hello")))
  }

  it should "be able to send a query and map the results to a list" in {
    val allCountries = Cypher("""
      start n=node(*) 
      where n.type! = "Country"
      and n.tag! = "anormcyphertest"
      return n.code as code, n.name as name 
      order by name desc;
      """)
    val countries = allCountries().map(row => 
      row[String]("code") -> row[String]("name")
    ).toList
    countries should equal (
      List("USA" -> "United States",
           "DEU" -> "Germany",
           "FRA" -> "France")
    )
  }

  it should "be able to submit a few requests in a row" in {
    val query = """
      START n=node(*) 
      where n.tag! = "anormcyphertest"
      RETURN n;
      """
    val test = Cypher(query)()
    Cypher(query)() should equal (test)
    Cypher(query)() should equal (test)
    Cypher(query)() should equal (test)
    Cypher(query)() should equal (test)
  }

}
