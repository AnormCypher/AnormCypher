package anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import anormcypher._
import anormcypher.Neo4jREST._
import scala.collection.JavaConverters._

class AnormCypherSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = {
    // initialize some test data
    Cypher("""create 
      (us {type:"Country", name:"United States", code:"USA", tag:"anormcyphertest"}),
      (germany {type:"Country", name:"Germany", code:"DEU", tag:"anormcyphertest"}),
      (france {type:"Country", name:"France", code:"FRA", tag:"anormcyphertest"}),
      (english {type:"Language", name:"English", code:"EN"}),
      (french {type:"Language", name:"French", code:"FR"}),
      (german {type:"Language", name:"German", code:"DE"}),
      (arabic {type:"Language", name:"Arabic", code:"AR"}),
      (italian {type:"Language", name:"Italian", code:"IT"}),
      (russian {type:"Language", name:"Russian", code:"RU"}),
      france-[:speaks {official:true}]->french,
      france-[:speaks]->arabic,
      france-[:speaks]->italian,
      germany-[:speaks {official:true}]->german,
      germany-[:speaks]->english,
      germany-[:speaks]->russian,
      (proptest {name:"proptest", tag:"anormcyphertest", f:1.234, i:1234, l:12345678910, s:"s", arri:[1,2,3,4], arrs:["a","b","c"], arrf:[1.234,2.345,3.456]});
      """)()
  }

  override def afterEach() = {
    // delete the test data
    Cypher("""start n=node(*)
      match n-[r?]-m
      where n.tag! = "anormcyphertest"
      delete n, r, m;
      """)()
  }

  "Cypher" should "be able to build a CypherStatement with apply" in {
    val query = """
      START n=node(*) 
      RETURN n;
      """
    Cypher(query) should equal (CypherStatement(query))
  } 

  it should "be able to make a query without parameters" in {
    val query = """
      START n=node(*) 
      RETURN n;
      """
    val cypherStatement = CypherStatement(query)
    cypherStatement()
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

  it should "be able to extract properties of different types" in {
    val allProps = Cypher("""
      start n=node(*) 
      where n.name! = "proptest"
      return n.i, n.l, n.s, n.f, n.arri, n.arrs, n.arrf;
      """)
    val props = allProps().map(row => 
      List(
        row[Int]("n.i"), 
        row[Long]("n.l"),
        row[String]("n.s"),
        row[Double]("n.f"),
        row[Seq[Int]]("n.arri"),
        row[Seq[Long]]("n.arri"),
        row[Seq[String]]("n.arrs"),
        row[Seq[Double]]("n.arrf")
      )
    ).toList.head
    props should equal (
      List(
        1234, 
        12345678910l, 
        "s", 
        1.234,
        Vector(1,2,3,4), 
        Vector(1,2,3,4), 
        Vector("a","b","c"), 
        Vector(1.234, 2.345, 3.456))
    )
  }

  it should "be able to .execute() a query" in {
    val query = """
      START n=node(0) 
      RETURN n;
      """
    val exit_status: Boolean = Cypher(query).execute()
    exit_status should equal (true)
  }
}
