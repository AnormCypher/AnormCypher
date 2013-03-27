package org.anormcypher

import concurrent.ExecutionContext.Implicits.global
import concurrent.Future

class AnormCypherSpec extends CommonTest with AnormCypher with Neo4jREST with Neo4jRESTConfig {

  override def beforeEach() {
    // initialize some test data
    waitIO {
      Cypher(
        """create
          |(us {type:"Country", name:"United States", code:"USA", tag:"anormcyphertest"}),
          |(germany {type:"Country", name:"Germany", code:"DEU", population:81726000, tag:"anormcyphertest"}),
          |(france {type:"Country", name:"France", code:"FRA", tag:"anormcyphertest", indepYear:1789}),
          |(monaco {name:"Monaco", population:32000, type:"Country", code:"MCO", tag:"anormcyphertest"}),
          |(english {type:"Language", name:"English", code:"EN"}),
          |(french {type:"Language", name:"French", code:"FR"}),
          |(german {type:"Language", name:"German", code:"DE"}),
          |(arabic {type:"Language", name:"Arabic", code:"AR"}),
          |(italian {type:"Language", name:"Italian", code:"IT"}),
          |(russian {type:"Language", name:"Russian", code:"RU"}),
          |france-[:speaks {official:true}]->french,
          |france-[:speaks]->arabic,
          |france-[:speaks]->italian,
          |germany-[:speaks {official:true}]->german,
          |germany-[:speaks]->english,
          |germany-[:speaks]->russian,
          |(proptest {name:"proptest", tag:"anormcyphertest", f:1.234, i:1234, l:12345678910, s:"s", arri:[1,2,3,4], arrs:["a","b","c"], arrf:[1.234,2.345,3.456]});
        """.stripMargin
      )()
    }
  }

  override def afterEach() {
    // delete the test data
    waitIO {
      Cypher(
        """start n=node(*)
          |match n-[r?]-m
          |where n.tag! = "anormcyphertest"
          |delete n, r, m;
        """.stripMargin
      )()
    }
  }

  "Cypher" should "be able to build a CypherStatement and send it with apply" in {
    val query = """start n=node(*)
                  |where n.name! = 'proptest'
                  |return n;
                """.stripMargin

    waitIO(Cypher(query)()).size should equal(1)
  }

  it should "be able to send a query and map the results to a list" in {
    val query = """start n=node(*)
                  |where n.type! = "Country"
                  |and n.tag! = "anormcyphertest"
                  |return n.code as code, n.name as name
                  |order by name desc;
                """.stripMargin

    val future = Cypher(query)() map {
      _ map {
        row => row[String]("code") -> row[String]("name")
      }
    }
    waitIO(future).toList should equal(
      List(
        "USA" -> "United States",
        "MCO" -> "Monaco",
        "DEU" -> "Germany",
        "FRA" -> "France"
      )
    )
  }

  it should "be able to submit a few requests in a row" in {
    val query = """start n=node(*)
                  |where n.tag! = "anormcyphertest"
                  |return n;
                """.stripMargin

    val f1 = Cypher(query)()
    val f2 = Cypher(query)()
    val f3 = Cypher(query)()
    val f4 = Cypher(query)()
    val f5 = Cypher(query)()
    val results = waitIO {
      Future.sequence(f1 :: f2 :: f3 :: f4 :: f5 :: Nil)
    }
    results forall {
      res => res == results.head
    } should equal(true)
  }

  it should "be able to extract properties of different types" in {
    val query = """start n=node(*)
                  |where n.name! = "proptest"
                  |return n.i, n.l, n.s, n.f, n.arri, n.arrs, n.arrf;
                """.stripMargin

    val future = Cypher(query)() map {
      _ map {
        row => row[Int]("n.i") :: row[Long]("n.l") :: row[String]("n.s") ::
          row[Double]("n.f") :: row[Seq[Int]]("n.arri") :: row[Seq[Long]]("n.arri") ::
          row[Seq[String]]("n.arrs") :: row[Seq[Double]]("n.arrf") :: Nil
      }
    }
    waitIO(future).toList.head should equal(
      List(
        1234,
        12345678910l,
        "s",
        1.234,
        Vector(1, 2, 3, 4),
        Vector(1, 2, 3, 4),
        Vector("a", "b", "c"),
        Vector(1.234, 2.345, 3.456)
      )
    )
  }

  it should "be able to .execute() a good query" in {
    val query = "start n=node(*) return n;"

    waitIO(Cypher(query).execute()) should equal(true)
  }

  it should "be able to .execute() a bad query" in {
    val query = """start n=node(0) asdf
                  |return n;
                """.stripMargin

    waitIO(Cypher(query).execute()) should equal(false)
  }

  it should "be able to parse nullable fields of various types" in {
    val query = """start n=node(*)
                  |where n.type! = 'Country'
                  |return n.indepYear? as indepYear
                  |order by n.indepYear?
                """.stripMargin

    val results = Cypher(query)() map {
      s => s map {
        row => row[Option[Int]]("indepYear")
      }
    }
    waitIO(results).toList should equal(List(Some(1789), None, None, None))
  }

  it should "fail on null fields if they're not Option" in {
    val query = """start n=node(*)
                  |where n.type! = 'Country'
                  |return n.indepYear? as indepYear;
                """.stripMargin

    evaluating {
      val future = Cypher(query)() map {
        _ map {
          row => row[Int]("indepYear")
        }
      }
      waitIO(future)
    } should produce[RuntimeException]
  }

}