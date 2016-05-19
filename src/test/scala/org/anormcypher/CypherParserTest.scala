package org.anormcypher

import CypherParser._

class CypherParserSpec extends BaseAnormCypherSpec {
  override def beforeEach = {
    // initialize some test data
    Cypher("""create 
      (us {type:"Country", name:"United States", code:"USA", tag:"anormcyphertest"}),
      (germany {type:"Country", name:"Germany", code:"DEU", population:81726000, tag:"anormcyphertest"}),
      (france {type:"Country", name:"France", code:"FRA", tag:"anormcyphertest", indepYear:1789}),
      (monaco {name:"Monaco", population:32000, type:"Country", code:"MCO", tag:"anormcyphertest"}),
      (english {type:"Language", name:"English", code:"EN", tag:"anormcyphertest"}),
      (french {type:"Language", name:"French", code:"FR", tag:"anormcyphertest"}),
      (german {type:"Language", name:"German", code:"DE", tag:"anormcyphertest"}),
      (arabic {type:"Language", name:"Arabic", code:"AR", tag:"anormcyphertest"}),
      (italian {type:"Language", name:"Italian", code:"IT", tag:"anormcyphertest"}),
      (russian {type:"Language", name:"Russian", code:"RU", tag:"anormcyphertest"}),
      (france)-[:speaks {official:true}]->(french),
      (france)-[:speaks]->(arabic),
      (france)-[:speaks]->(italian),
      (germany)-[:speaks {official:true}]->(german),
      (germany)-[:speaks]->(english),
      (germany)-[:speaks]->(russian),
      (proptest {name:"proptest", tag:"anormcyphertest", f:1.234, i:1234, l:12345678910, s:"s", arri:[1,2,3,4], arrs:["a","b","c"], arrf:[1.234,2.345,3.456]});
      """)()
  }

  override def afterEach = {
    // delete the test data
    Cypher("""match (n)     
      where n.tag = "anormcyphertest"
      optional match (n)-[r]-()
      delete n, r;
      """)()
  }

  "CypherParser" should "be able to parse a node" in {
    case class Country(name:String, node:NeoNode)
    val results = Cypher("start n=node(*) where n.type = \"Country\" return n.name as name, n order by name desc")().map {
      row => Country(row[String]("name"), row[NeoNode]("n"))
    }.toList
    results.head.name should equal ("United States")
    results.head.node.props should equal (Map("name" -> "United States", "type" -> "Country", "tag" -> "anormcyphertest", "code" -> "USA"))
  }

  it should "be able to parse into a single Long" in {
    val count: Long = Cypher("""
      start n=node(*) 
      where n.tag = 'anormcyphertest' 
      return count(n)""").as(scalar[Long].single)
    count should equal (11)
  }

  it should "be able to parse into a single Float" in {
    val count: Float = Cypher("""
      start n=node(*)
      where n.tag = 'anormcyphertest'
      return count(n)""").as(scalar[Float].single)
    count should equal (11)
  }

  it should "be able to parse a case class with a node" in {
    val results = Cypher("start n=node(*) where n.type = 'Country' return n.name as name, n")().map {
      case CypherRow(name: String, n: NeoNode) => name -> n
      case e:Any => {//println(e);
      }
    }.toList
    // TODO this isn't working!
    //results.head("United States").props should equal (Map("name" -> "United States", "type" -> "Country", "tag" -> "anormcyphertest", "code" -> "USA"))
  }

  it should "be able to parse and flatten into a tuple" in {
    val result:List[(String,Int)] = 
      Cypher("""
        start n=node(*) 
        where n.type = 'Country' and exists(n.name) and exists(n.population)
        return n.name, n.population 
        order by n.name
        """
      ).as(
        (str("n.name") ~ int("n.population")).map(flatten).*
      ) 
    result should equal (List(("Germany",81726000), ("Monaco", 32000)))
  }
}
