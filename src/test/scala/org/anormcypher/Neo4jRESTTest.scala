package org.anormcyphertest

import org.scalatest._
import org.scalatest.matchers._
import org.anormcypher._
import scala.collection.JavaConverters._

class Neo4jRESTSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

  override def beforeEach() = {
    Cypher("""
      CREATE (n {anormcyphername:'n'}), 
      (n2 {anormcyphername:'n2'}), 
      (n3 {anormcyphername:'n3'}), 
      n-[:test {name:'r'}]->n2, 
      n2-[:test {name:'r2'}]->n3;
      """)()
    Cypher("""
      CREATE (n5 {anormcyphername:'n5'}), 
        (n6 {anormcyphername:'n6'}), 
        n5-[:test {name:'r', i:1, arr:[1,2,3], arrc:["a","b","c"]}]->n6;
      """)()
    Cypher("""
      CREATE (n7 {anormcyphername:'nprops', i:1, arr:[1,2,3], arrc:['a','b','c']});
      """)()
  }

  override def afterEach() = {
    Cypher("START n=node(*) match n-[r?]-() where has(n.anormcyphername) DELETE n,r;")()
  }

  "Neo4jREST" should "be able to retrieve properties of nodes" in {
    val results = Cypher("START n=node(*) where n.anormcyphername! = 'nprops' RETURN n;")()
    results.size should equal (1)
    val node = results.map { row =>
      row[NeoNode]("n")
    }.head
    node.props("anormcyphername") should equal ("nprops")
    node.props("i") should equal (1)
    node.props("arr").asInstanceOf[java.util.ArrayList[Int]].asScala should equal (Vector(1,2,3))
    node.props("arrc").asInstanceOf[java.util.ArrayList[String]].asScala should equal (Vector("a","b","c"))
  }

  it should "be able to retrieve collections of nodes" in {
    val results = Cypher("""
      START n=node(*) 
      where n.anormcyphername! = 'n' or n.anormcyphername! = 'n2'
      RETURN collect(n);
      """)()
    val nodes = results.map { row =>
      row[Seq[NeoNode]]("collect(n)")
    }.head
    nodes.size should equal (2)
  }

  it should "be able to retrieve properties of relationships" in {
    val results = Cypher("""
      START n=node(*) 
      match n-[r]->m 
      where n.anormcyphername! = 'n5'
      RETURN r;
      """)()
    results.size should equal (1)
    val rel = results.map { row =>
      row[NeoRelationship]("r")
    }.head
    rel.props("name") should equal ("r")
    rel.props("i") should equal (1)
    rel.props("arr").asInstanceOf[java.util.ArrayList[Int]].asScala should equal (Vector(1,2,3))
    rel.props("arrc").asInstanceOf[java.util.ArrayList[String]].asScala should equal (Vector("a","b","c"))
  }

  it should "be able to retrieve collections of relationships" in {
    val (r,r2) = Cypher("""
      CREATE (n {anormcyphername:'n8'}), 
        (n2 {anormcyphername:'n9'}), 
        (n3 {anormcyphername:'n10'}), 
        n-[r:test {name:'r'}]->n2, 
        n2-[r2:test {name:'r2'}]->n3
        return r, r2;
      """)().map {
      row => (row[NeoRelationship]("r"), row[NeoRelationship]("r2"))
    }.head
    val results = Cypher("""
      START n=node(*) 
      match p=n-[r*2]->m
      where has(n.anormcyphername) 
      and n.anormcyphername = "n8"
      RETURN r;
      """)()
    val rels = results.map { row =>
      row[Seq[NeoRelationship]]("r")
    }.head
    rels.size should equal (2)
    rels should contain (r)
    rels should contain (r2)
  }

}
