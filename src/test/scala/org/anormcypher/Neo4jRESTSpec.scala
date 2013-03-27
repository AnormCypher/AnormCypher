package org.anormcypher

import concurrent.ExecutionContext.Implicits.global
import concurrent.Future

class Neo4jRESTSpec extends CommonTest with AnormCypher with Neo4jREST with Neo4jRESTConfig {

  override def beforeEach() {
    val f1 = Cypher(
      """create (n {anormcyphername:'n'}),
        |(n2 {anormcyphername:'n2'}),
        |(n3 {anormcyphername:'n3'}),
        |n-[:test {name:'r'}]->n2,
        |n2-[:test {name:'r2'}]->n3;
      """.stripMargin
    )()
    val f2 = Cypher(
      """create (n5 {anormcyphername:'n5'}),
        |(n6 {anormcyphername:'n6'}),
        |n5-[:test {name:'r', i:1, arr:[1,2,3], arrc:["a","b","c"]}]->n6;
      """.stripMargin
    )()
    val f3 = Cypher("create (n7 {anormcyphername:'nprops', i:1, arr:[1,2,3], arrc:['a','b','c']});")()
    waitIO {
      Future.sequence(f1 :: f2 :: f3 :: Nil)
    }
  }

  override def afterEach() {
    waitIO {
      Cypher("start n=node(*) match n-[r?]-() where has(n.anormcyphername) delete n,r;")()
    }
  }

  "Neo4jREST" should "be able to retrieve properties of nodes" in {
    val results = waitIO {
      Cypher("start n=node(*) where n.anormcyphername! = 'nprops' return n;")() map {
        _ map {
          row => row[NeoNode]("n")
        }
      }
    }
    results.size should equal(1)
    val node = results.head
    node.props("anormcyphername") should equal("nprops")
    node.props("i") should equal(1)
    node.props("arr").asInstanceOf[Seq[Int]] should equal(Vector(1, 2, 3))
    node.props("arrc").asInstanceOf[Seq[String]] should equal(Vector("a", "b", "c"))
  }

  it should "be able to retrieve collections of nodes" in {
    val nodes = waitIO {
      Cypher(
        """start n=node(*)
          |where n.anormcyphername! = 'n' or n.anormcyphername! = 'n2'
          |return collect(n);
        """.stripMargin
      )() map {
        _ map {
          row => row[Seq[NeoNode]]("collect(n)")
        }
      }
    }.head
    nodes.size should equal(2)
  }

  it should "be able to retrieve properties of relationships" in {
    val results = waitIO {
      Cypher(
        """start n=node(*)
          |match n-[r]->m
          |where n.anormcyphername! = 'n5'
          |return r;
        """.stripMargin
      )() map {
        _ map {
          row => row[NeoRelationship]("r")
        }
      }
    }
    results.size should equal(1)
    val rel = results.head
    rel.props("name") should equal("r")
    rel.props("i") should equal(1)
    rel.props("arr").asInstanceOf[Seq[Int]] should equal(Vector(1, 2, 3))
    rel.props("arrc").asInstanceOf[Seq[String]] should equal(Vector("a", "b", "c"))
  }

  it should "be able to retrieve collections of relationships" in {
    val (r, r2) = waitIO {
      Cypher(
        """create (n {anormcyphername:'n8'}),
          |(n2 {anormcyphername:'n9'}),
          |(n3 {anormcyphername:'n10'}),
          |n-[r:test {name:'r'}]->n2,
          |n2-[r2:test {name:'r2'}]->n3
          |return r, r2;
        """.stripMargin
      )() map {
        _ map {
          row => row[NeoRelationship]("r") -> row[NeoRelationship]("r2")
        }
      }
    }.head
    val rels = waitIO {
      Cypher(
        """start n=node(*)
          |match p=n-[r*2]->m
          |where has(n.anormcyphername)
          |and n.anormcyphername = "n8"
          |return r;
        """.stripMargin
      )() map {
        _ map {
          row => row[Seq[NeoRelationship]]("r")
        }
      }
    }.head
    rels.size should equal(2)
    rels should contain(r)
    rels should contain(r2)
  }

}