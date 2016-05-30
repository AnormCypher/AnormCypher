## AnormCypher
<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-generate-toc again -->
**Table of Contents**

- [SBT Console Demo](#sbt-console-demo)
- [Usage](#usage)
    - [Configuring a server](#configuring-a-server)
    - [Executing Cypher Queries](#executing-cypher-queries)
    - [Retrieving data with CypherStatement.apply()](#retrieving-data-with-cypherstatementapply)
    - [Reactive Streaming with Neo4jConnection.streamAutocommit](#reactive-streaming-with-neo4jconnectionstreamautocommit)
    - [Transaction API](#transaction-api)
    - [Using Pattern Matching](#using-pattern-matching)
    - [Special data types](#special-data-types)
        - [Nodes](#nodes)
        - [Relationships](#relationships)
        - [Dealing with Nullable columns](#dealing-with-nullable-columns)
        - [Using the Parser API (the Parser API is a work in progress)](#using-the-parser-api-the-parser-api-is-a-work-in-progress)
    - [Contributors](#contributors)
    - [Thanks](#thanks)
    - [License LGPL](#license-lgpl)

<!-- markdown-toc end -->
[![Join the chat at https://gitter.im/AnormCypher/AnormCypher](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/AnormCypher/AnormCypher?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
This is a Neo4j client library for the HTTP Cypher transactional endpoints.

The goals of this library are to provide a great API to use Cypher, and it will be modeled after Anorm from Play,
which I found to be pleasant to use with SQL. More info about Anorm can be found here:
http://www.playframework.org/documentation/2.0.4/ScalaAnorm

Integration tests currently run against neo4j-community-2.1.3.

[![Build Status](https://travis-ci.org/AnormCypher/AnormCypher.png?branch=master)](https://travis-ci.org/AnormCypher/AnormCypher?branch=master)

The latest release is 0.9.1.  Version 0.9.x depends on the play-json and play-ws libraries from Play 2.4.3.  If you need to use AnormCypher in Play 2.3.x, please use version 0.7.0.

AnormCypher did not support transaction before 0.9.  The last release without transaction support is 0.8.1.

As of version 0.5, AnormCypher uses play-json and Scala 2.11. 

If you want to use scala 2.10, you need to use version 0.4.x (latest is 0.4.4)

If you want to use scala 2.9, you need to use version 0.3.x (latest is 0.3.1).

## SBT Console Demo

Install neo4j on localhost.  For neo4j servers after 2.3, disable authentication by editing the following line in `conf/neo4j-server.properties` before running `neo4j start`

```
dbms.security.auth_enabled=false
```

If you would like to connect to a neo4j instance on another server, or use authentication, simply set the appropriate parameters (host, port, username, and password, etc) in the Neo4jREST factory method in the following example.

Clone the AnormCypher repository from github, and run `sbt test:console`

Alternatively, you can create a build.sbt file with the following:
``` Scala
scalaVersion := "2.11.6"

resolvers ++= Seq(
  "anormcypher" at "http://repo.anormcypher.org/",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)


libraryDependencies ++= Seq(
  "org.anormcypher" %% "anormcypher" % "0.9.1"
)
```

[Note] If you take the second step route from above, you will notice that the async http client ships with the default logging level set to DEBUG.  To reduce the noise from logging, you can use the logback-test.xml configuration in our src/test/resources.  If you want to see the details of the http client, you can simply run `sbt console` which won't include test-classes on the classpath.

Assuming you have a local Neo4j Server running on the default port, try (note: this will create nodes on your database):
``` Scala
import org.anormcypher._
import play.api.libs.ws._

// Provide an instance of WSClient
val wsclient = ning.NingWSClient()

// Setup the Rest Client
// Need to add the Neo4jConnection type annotation so that the default
// Neo4jConnection -> Neo4jTransaction conversion is in the implicit scope
implicit val connection: Neo4jConnection = Neo4jREST()(wsclient)

// Provide an ExecutionContext
implicit val ec = scala.concurrent.ExecutionContext.global

// create some test nodes
Cypher("""create (anorm:anormcyphertest {name:"AnormCypher"}), (test:anormcyphertest {name:"Test"})""").execute()

// a simple query
val req = Cypher("match (n:anormcyphertest) return n.name")

// get a stream of results back
val stream = req()

// get the results and put them into a list
stream.map(row => {row[String]("n.name")}).toList

// remove the test nodes
Cypher("match (n:anormcyphertest) delete n")()

// shut down WSClient
wsclient.close()
```

## Usage
You'll probably notice that this usage is very close to Play's Anorm. That is the idea!

### Configuring a server
The default is localhost, but you can specify a special server when your app is starting via the `setServer` or `setURL` 
options.
``` Scala
import org.anormcypher._
import play.api.libs.ws._

// Provide an instance of WSClient
implicit val wsclient = ning.NingWSClient()

// without auth
implicit val connection: Neo4jConnection = Neo4jREST("localhost", 7474)

// or with basic auth
implicit val connection2: Neo4jConnection = Neo4jREST("localhost", 7474, "username", "password")
```

### Executing Cypher Queries

To start you need to learn how to execute Cypher queries.

First, `import org.anormcypher._`, setup an implicit Neo4jREST instance, and then use the Cypher object to create queries.

``` Scala
import org.anormcypher._ 
import play.api.libs.ws._

// Provide an instance of WSClient
implicit val wsclient = ning.NingWSClient()
implicit val connection: Neo4jConnection = Neo4jREST()

val result: Boolean = Cypher("START n=node(0) RETURN n").execute()
```

The `execute()` method returns a Boolean value indicating whether the execution was successful.

Since Scala supports multi-line strings, feel free to use them for complex Cypher statements:

``` Scala
// create some sample data
val result = Cypher("""
  create (germany {name:"Germany", population:81726000, type:"Country", code:"DEU"}),
         (france {name:"France", population:65436552, type:"Country", code:"FRA", indepYear:1790}),
         (monaco {name:"Monaco", population:32000, type:"Country", code:"MCO"});
  """).execute()
// result: Boolean = true
 
val cypherQuery = Cypher(
  """
    start n=node(*) 
    match n-->m
    where n.code = 'FRA'
    return n,m;
  """
)
```

If your Cypher query needs dynamic parameters, you can declare placeholders like `{name}` in the query string, and later assign a value to them with `on`:

``` Scala
Cypher(
  """
    start n=node(*) 
    where n.type = "Country"
      and n.code = {countryCode}
    return n.name
  """
).on("countryCode" -> "FRA")
```

### Retrieving data with CypherStatement.apply()
The first way to access the results of a return query is to use `statement.apply()` or simply `statement()`

When you call `apply()` on any Cypher statement, you will receive a `Seq` of `CypherRow` instances, where each row can be seen as a dictionary:

``` Scala
// Create Cypher query
val allCountries = Cypher("start n=node(*) where n.type = 'Country' return n.code as code, n.name as name")
 
// Transform the resulting Seq[CypherRow] to a List[(String,String)]
val countries = allCountries.apply().map(row => 
  row[String]("code") -> row[String]("name")
).toList
```

In the following example we will count the number of Country entries in the database, so the result set will be a single row with a single column:

``` Scala
// First retrieve the first row
val firstRow = Cypher("start n=node(*) where n.type = 'Country' return count(n) as c").apply().head
 
// Next get the content of the 'c' column as Long
val countryCount = firstRow[Long]("c")
// countryCount: Long = 3
```

### Reactive Streaming with Neo4jConnection.streamAutocommit
Occasionally we need to handle a very large data set returned from the Neo4j server -- a dataset so large that it would exhaust the JVM's heap space if the entire server response were read in before being processed.  There might be situations where we cannot avoid loading the entire data set; in such cases the only solution would be increasing the maximum heap size and running the program on a machine with more memory.  However, most of the time, processing could start without having the complete data set.  For example, if all we have to do is to perform some transformation on each CypherResultRow and then stream the data to the client, there is no reason to wait till we have received all the data from the server; we can use reactive streaming to start working as soon as we receive one CypherResultRow.

AnormCypher uses Play's Enumerator|Iteratee [API](https://www.playframework.com/documentation/2.4.x/Enumerators) to achieve reactive streaming.  Specifically, the Neo4jConnection trait defines the `streamAutocommit` method with the following signature:


``` Scala
import play.api.libs.iteratee._

trait Neo4jConnection {
  ...
  def streamAutocommit(stmt: CypherStatement)(implicit ec: ExecutionContext): Enumerator[CypherResultRow]
  ...
}

```

while another method, `execute`, consumes the reactive stream (`Enumerator[CypherResultRow]`) produced by this method and returns the entire response as a Sequence, asynchronously

``` Scala
trait Neo4jConnection {
  /** Asynchronous, non-streaming query */
  def execute(stmt: CypherStatement)(implicit ec: ExecutionContext): Future[Seq[CypherResultRow]] =
      streamAutocommit(stmt) |>>> Iteratee.getChunks[CypherResultRow]
}
```

As the method name implies, the query is executed in its own separate transaction, because the large data set it's designed to work with prohibits the transaction to be held open across requests. (We will talk about transaction a little bit later)

One obvious gain is that, if you are using Play for the web front end, and Neo4j as the data store, you can now stream cypher result sets directly to the browsing client using AnomrCypher as a bridge.

``` Scala
object MyController extends Controller {
  def allNodes: Enumerator[CypherResultRow] = neo4jconn.streamAutocommit(CypherStatement("match n return n"))

  def stream = Action {
	  Ok.chunked(allNodes map (_.toString))
  }

  import akka.stream.scaladsl.Source
  import play.api.libs.streams.Streams
  def akkaStream = Action {
    Ok.chunked(Source(Streams.enumeratorToPublisher(allNodes map (_.toString))))
  }
}
```

Consult [James Roper's article](https://jazzy.id.au/2012/11/06/iteratees_for_imperative_programmers.html) for a good introduction to Enumerator and Iteratee and to the problems they are designed to solve.

### Transaction API
AnormCypher supports transactional cypher with the `Neo4jTransaction.withTx` method.  The method is a standard Loan Pattern implementation, starting a new transaction before passing the transction to the code block, then either commiting the transaction if the code executed successfully or rolling back the transaction if an exception is throw during code execution.  A typical use of the API is as follows:
``` Scala
val Tag = "anormcyphertest"
val res = Neo4jTransaction.withTx { implicit tx =>
      val res1 = Cypher(s"""create (n:${Tag}{name: "n1", level: 1}) return n.name as name, n.level as level """)()
//      res1(0)[String]("name") shouldBe "n1"

      val res2 = Cypher(s"""create (n:${Tag}{name: "n2", level: 2}) return n.name as name""")()
//      res2(0)[String]("name") shouldBe "n2"

      val res3 = Cypher(s"""match (n1:${Tag}{name: "n1"}), (n2:${Tag}{name: "n2"})
create (n1)-[r:hasChildren]->(n2)""")()
    }
Await.result(res, 3.seconds)

```
One important to remember is that, within the code block passed to `withTx`, the `CypherStatement`s should all execute sequentially.  This is best ensured by calling `apply()` on each statement.  It is possible to use results from previous statements within the same transaction.

### Using Pattern Matching
You can also use Pattern Matching to match and extract the CypherRow content. In this case the column name doesn’t matter. Only the order and the type of the parameters is used to match.

The following example transforms each row to the correct Scala type:

``` Scala
case class SmallCountry(name:String) 
case class BigCountry(name:String) 
case class France()

// NOTE: case CypherRow syntax is NOT YET SUPPORTED
val countries = Cypher("start n=node(*) where n.type = 'Country' return n.name as name, n.population as pop")().collect {
  case CypherRow("France", _) => France()
  case CypherRow(name:String, pop:Int) if(pop > 1000000) => BigCountry(name)
  case CypherRow(name:String, _) => SmallCountry(name)      
}
// countries: Seq[Product with Serializable] = List(BigCountry(Germany), France(), SmallCountry(Monaco))
```

Note that since `collect(…)` ignores the cases where the partial function isn’t defined, it allows your code to safely ignore rows that you don’t expect.

### Special data types
#### Nodes

Nodes can be extracted as so:

``` Scala
// NOTE: case CypherRow syntax is NOT YET SUPPORTED
Cypher("start n=node(*) where n.type = 'Country' return n.name as name, n")().map {
  case CypherRow(name: String, n: org.anormcypher.NeoNode) => name -> n
}
```  
Here we specifically chose to use map, as we want an exception if the row isn’t in the format we expect.

A `Node` is just a simple Scala `case class`, not quite as type-safe as configuring your own:
``` Scala
case class NeoNode(props:Map[String,Any])
```

#### Relationships

Relationships can be extracted as so:

``` Scala
// NOTE: case CypherRow syntax is NOT YET SUPPORTED
Cypher("start n=node(*) match n-[r]-m where has(n.name) return n.name as name, r;")().map {
  case CypherRow(name: String, r: org.anormcypher.NeoRelationship) => name -> r
}
```  
Here we specifically chose to use map, as we want an exception if the row isn’t in the format we expect.

Similarly, a `Relationship` is just a simple Scala `case class`, not quite as type-safe as configuring your own:
``` Scala
case class NeoRelationship(props:Map[String,Any])
```

### Dealing with Nullable columns
If a column can contain `Null` values in the database schema, you need to manipulate it as an `Option` type.

For example, the `indepYear` of the `Country` table is nullable, so you need to match it as `Option[Int]`:

``` Scala
// NOTE: case CypherRow syntax is NOT YET SUPPORTED
Cypher("start n=node(*) where n.type = 'Country' return n.name as name, n.indepYear as year;")().collect {
  case CypherRow(name:String, Some(year:Int)) => name -> year
}
```

If you try to match this column as `Int` it won’t be able to parse `Null` values. Suppose you try to retrieve the column content as `Int` directly from the dictionary:
``` Scala
Cypher("start n=node(*) where n.type = 'Country' return n.name as name, n.indepYear as indepYear;")().map { row =>
  row[String]("name") -> row[Int]("indepYear")
}
```

This will produce an `UnexpectedNullableFound(COUNTRY.INDEPYEAR)` exception if it encounters a null value, so you need to map it properly to an `Option[Int]`, as:

``` Scala
Cypher("start n=node(*) where n.type = 'Country' return n.name as name, n.indepYear as indepYear;")().map { row =>
  row[String]("name") -> row[Option[Int]]("indepYear")
}
```

This is also true for the parser API, as we will see next.

### Using the Parser API (the Parser API is a work in progress)
You can use the parser API to create generic and reusable parsers that can parse the result of any Cypher query.

Note: This is really useful, since most queries in a web application will return similar data sets. For example, if you have defined a parser able to parse a Country from a result set, and another Language parser, you can then easily compose them to parse both Country and Language from a single return.

First you need to import `org.anormcypher.CypherParser._`

First you need a `CypherRowParser`, i.e. a parser able to parse one row to a Scala value. For example we can define a parser to transform a single column result set row, to a Scala `Long`:

``` Scala
val rowParser = scalar[Long]
```

Then we have to transform it into a `CypherResultSetParser`. Here we will create a parser that parse a single row:

``` Scala
val rsParser = scalar[Long].single
```

So this parser will parse a result set to return a `Long`. It is useful to parse to results produced by a simple Cypher count query:

``` Scala
val count: Long = Cypher("start n=node(*) return count(n)").as(scalar[Long].single)
```

Let’s write a more complicated parser:

`str("name") ~ int("population")`, will create a `CypherRowParser` able to parse a row containing a `String` name column and an `Integer` population column. Then we can create a `ResultSetParser` that will parse as many rows of this kind as it can, using *:

``` Scala
val populations:List[String~Int] = {
  Cypher("start n=node(*) where n.type = 'Country' return n.*").as( str("n.name") ~ int("n.population") * ) 
}
```

As you see, this query’s result type is `List[String~Int]` - a list of country name and population items.

You can also rewrite the same code as:

``` Scala
val result:List[String~Int] = {
  Cypher("start n=node(*) where n.type = 'Country' return n.*").as(get[String]("n.name")~get[Int]("n.population")*) 
}
```

Now what about the `String~Int` type? This is an AnormCypher type that is not really convenient to use outside of your database access code. You would rather have a simple tuple `(String, Int)` instead. You can use the map function on a `CypherRowParser` to transform its result to a more convenient type:

``` Scala
str("n.name") ~ int("n.population") map { case n~p => (n,p) }
```

Note: We created a tuple `(String,Int)` here, but there is nothing stopping you from transforming the `CypherRowParser` result to any other type, such as a custom case class.

Now, because transforming `A~B~C` types to `(A,B,C)` is a common task, we provide a `flatten` function that does exactly that. So you finally write:

``` Scala
val result:List[(String,Int)] = {
  Cypher("start n=node(*) where n.type = 'Country' return n.*").as(
    str("n.name") ~ int("n.population") map(flatten) *
  ) 
}
```

Now let’s try with a more complicated example. How to parse the result of the following query to retrieve the country name and all spoken languages for a country code?

``` Cypher
start country=node_auto_index(code="FRA")
match country-[:speaks]->language
return country.name, language.name;
```

Let's start by parsing all rows as a `List[(String,String)]` (a list of name,language tuples):

``` Scala
var p: CypherResultSetParser[List[(String,String)] = {
  str("country.name") ~ str("language.name") map(flatten) *
}
```

Now we get this kind of result:

``` Scala
List(
  ("France", "Arabic"), 
  ("France", "French"), 
  ("France", "Italian"), 
  ("France", "Portuguese"), 
  ("France", "Spanish"), 
  ("France", "Turkish")
)
```

We can then use the Scala collection API, to transform it to the expected result:

``` Scala
case class SpokenLanguages(country:String, languages:Seq[String])

languages.headOption.map { f =>
  SpokenLanguages(f._1, languages.map(_._2))
}
```

Finally, we get this convenient function:

``` Scala
case class SpokenLanguages(country:String, languages:Seq[String])

def spokenLanguages(countryCode: String): Option[SpokenLanguages] = {
  val languages: List[(String, String)] = Cypher(
    """
      start country=node_auto_index(code="{code}")
      match country-[:speaks]->language
      return country.name, language.name;
    """
  )
  .on("code" -> countryCode)
  .as(str("country.name") ~ str("language.name") map(flatten) *)

  languages.headOption.map { f =>
    SpokenLanguages(f._1, languages.map(_._2))
  }
}
```

To continue, let’s complicate our example to separate the official language from the others:

``` Scala
case class SpokenLanguages(
  country:String, 
  officialLanguage: Option[String], 
  otherLanguages:Seq[String]
)

def spokenLanguages(countryCode: String): Option[SpokenLanguages] = {
  val languages: List[(String, String, Boolean)] = Cypher(
    """
      start country=node_auto_index(code="{code}")
      match country-[:speaks]->language
      return country.name, language.name, language.isOfficial;
    """
  )
  .on("code" -> countryCode)
  .as {
    str("country.name") ~ str("language.name") ~ str("language.isOfficial") map {
      case n~l~"T" => (n,l,true)
      case n~l~"F" => (n,l,false)
    } *
  }

  languages.headOption.map { f =>
    SpokenLanguages(
      f._1, 
      languages.find(_._3).map(_._2),
      languages.filterNot(_._3).map(_._2)
    )
  }
}
```

If you try this on the world sample database, you will get:

``` Scala
$ spokenLanguages("FRA")
> Some(
    SpokenLanguages(France,Some(French),List(
      Arabic, Italian, Portuguese, Spanish, Turkish
    ))
)
```

## Contributors
* Eve Freeman: [@freeeve](https://github.com/freeeve) on github
* Jason Jackson: [@jasonjackson](https://github.com/jasonjackson) on github
* Julien Sirocchi: [@sirocchj](https://github.com/sirocchj) on github
* Pieter-Jan Van Aeken: [@PieterJanVanAeken](https://github.com/PieterJanVanAeken) on github
* [@okumin](https://github.com/okumin) on github
* [@mvallerie](https://github.com/mvallerie) on github
* Denis Rosca: [@denisrosca](https://github.com/denisrosca) on github
* Darren Gibson: [@zarthross](https://github.com/zarthross) on github
* Kai Chen: [@Kai-Chen](https://github.com/kai-chen) on github

## Thanks
* The Play Framework team for providing the Anorm library, the basis for this library. (and now the play-json module)
* Databinder.net, for the Dispatch library
* Neo Technologies for Neo4j!

## License LGPL

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.
 
You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see http://www.gnu.org/licenses/
