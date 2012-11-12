package org.anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import org.anormcypher._
import org.anormcypher.Neo4jREST._
import scala.collection.JavaConverters._

class CypherParserSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {
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
/*
  "CypherParser" should "be able to parse into a single Long" in {
    val count: Long = Cypher("""
      start n=node(*) 
      where n.tag! = 'anormcyphertest' 
      return count(n)""").as(scalar[Long].single)
    count should equal (7)
  }
*/
}
