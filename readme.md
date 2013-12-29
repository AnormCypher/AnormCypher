## AnormCypher
The goals of this library are to provide a great API to use Cypher, and it will be modeled after Anorm from Play,
which I found to be pleasant to use with SQL. More info about Anorm can be found here:
http://www.playframework.org/documentation/2.0.4/ScalaAnorm

Integration tests currently run against neo4j-community-2.0.0.

[![Build Status](https://travis-ci.org/AnormCypher/AnormCypher.png)](https://travis-ci.org/AnormCypher/AnormCypher)

## SBT Console Demo

Switch to an empty folder and create a build.sbt file with the following:
``` Scala
libraryDependencies ++= Seq(
  "org.anormcypher" %% "anormcypher" % "1.0.0"
)
```

Run `sbt console`

Assuming you have a local Neo4j Server running on the default port, try (note: this will create nodes on your database):
``` Scala
import org.anormcypher._

val conn = AnormCypherREST("http://localhost:7474/db/data/")

// create some test nodes
conn.withTx {
  Cypher("""create (anorm {name:"AnormCypher"}), (test {name:"Test"})""").execute()
}

// a simple query
val req = conn.Cypher("start n=node(*) return n.name")

// get a stream of results back
val stream = req()

// get the results and put them into a list
stream.map(row => {row[String]("n.name")}).toList
```

## Usage
You'll probably notice that this usage is very close to Play's Anorm. That is the idea!

### Configuring a server
The default is localhost, but you can specify a special server when your app is starting via the `setServer`  
option.

``` Scala
import org.anormcypher._

// without auth
DefaultNeo4jREST.setServer("http://localhost:7474/db/data/")

// or with basic auth
DefaultNeo4jREST.setServer("http://username:password@localhost:7474/db/data/")
```

### Handling multiple connections
One of the downsides of using the DefaultNeo4jREST singleton object, is that you can't easily have two "connections" to different servers at the same time, without resetting the server each time.

``` Scala
val conn = Neo4jREST("http://localhost:7474/db/data/")
val conn2 = Neo4jREST("http://localhost:7474/db/data/")

// prefix your Cypher() with the connection you'd like to use
conn.Cypher("return 1")
```

### Executing Cypher Queries
First, `import org.anormcypher._`, and then simply use the Cypher object to create queries. 

``` Scala
import org.anormcypher._ 

val result:Boolean = Cypher("START n=node(0) RETURN n").execute()
```

The `execute()` method returns a Boolean value indicating whether the execution was successful.

Since Scala supports multi-line strings, feel free to use them for complex Cypher statements:

``` Scala
// create some sample data
val result = Cypher("""
  create (germany:Country {name:"Germany", population:81726000, code:"DEU"}),
         (france:Country {name:"France", population:65436552, code:"FRA", indepYear:1790}),
         (monaco:Country {name:"Monaco", population:32000, code:"MCO"});
  """).execute()
// result: Boolean = true
 
val cypherQuery = Cypher(
  """
    match (n:Country)-->(m)
    where n.code = 'FRA';
    return n,m;
  """
)
```

If your Cypher query needs dynamic parameters, you can declare placeholders like `{name}` in the query string, and later assign a value to them with `on`:

``` Scala
Cypher(
  """
    match (n:Country)
    where n.code = {countryCode}
    return n.name
  """
).on("countryCode" -> "FRA")
```

### Retrieving data using the Stream API
When you call `apply()` on any Cypher statement, you will receive a `Future[Stream[CypherRow]]`, where each row can be seen as a dictionary:

``` Scala
// Create Cypher query

val allCountries = Cypher("match (n:Country) return n.code as code, n.name as name")
 
// Transform the resulting Stream[CypherRow] to a List[(String,String)]
val countries = allCountries().map(row => 
  row[String]("code") -> row[String]("name")
).toList
```

### Special data types
#### Nodes

Nodes can be extracted as so:

``` Scala
Cypher("match (n:AnormTest) return n")().map { row =>
   row[NeoNode]("n")
}
```  
Here we specifically chose to use map, as we want an exception if the row isn’t in the format we expect.

A `Node` is just a simple Scala `case class`, not quite as type-safe as configuring your own:
``` Scala
case class NeoNode(id:Long, props:Map[String,Any], labels:Seq[String])
```

#### Relationships

Relationships can be extracted as so:

``` Scala
Cypher("match (n)-[r]-(m) where has(n.name) return n.name as name, r;")().map { row =>
  row[String]("name") -> row[NeoRelationship]("r")
}
```  
Here we specifically chose to use map, as we want an exception if the row isn’t in the format we expect.

Similarly, a `Relationship` is just a simple Scala `case class`, not quite as type-safe as configuring your own:
``` Scala
case class NeoRelationship(id:Long, props:Map[String,Any], type:String)
```

### Dealing with Nullable columns
If a column can contain `Null` values in the database schema, you need to manipulate it as an `Option` type.

For example, the `indepYear` of the `Country` table is nullable, so you need to match it as `Option[Int]`:

``` Scala
Cypher("match (n:Country) return n.name as name, n.indepYear as year;")().map { row =>
  row[String]("name") -> row[Option[Int]]("year")
}
```

If you try to match this column as `Int` it won’t be able to parse `Null` values. Suppose you try to retrieve the column content as `Int` directly from the dictionary:
``` Scala
Cypher("match (n:Country) return n.name as name, n.indepYear as indepYear;")().map { row =>
  row[String]("name") -> row[Int]("year")
}
```

This will produce an `UnexpectedNullableFound(COUNTRY.INDEPYEAR)` exception if it encounters a null value, so you need to map it properly to an `Option[Int]`, as:

``` Scala
Cypher("start n=node(*) where n.type! = 'Country' return n.name as name, n.indepYear as indepYear;")().map { row =>
  row[String]("name") -> row[Option[Int]]("indepYear")
}
```

### Transactional API
To get increased performance, you can use the transactional API. If you are only creating, don't bother get the results back, just using `.execute()` which gives a `Future[Boolean]`. Otherwise you will get a `Future[Stream[CypherRow]]`.

```
withTx {
  for(x <- 1 to 10000) {
    Cypher("create (n:AnormCypherTest)").execute()
  }
}

// using a connection
conn.withTx {
  ...
}
```

### Embedded
To use embedded, you can add another libraryDependency in your build.sbt for:

```
libraryDependencies ++= Seq(
   "org.neo4j" % "neo4j" % "2.0.0"
)
```

Then, you can instantiate an embedded database and pass it to AnormCypher:

```
import org.anormcypher.embedded._

val db = new org.neo4j.graphdb.factory.GraphDatabaseFactory().newEmbeddedDatabase("/path/to/db/")
val conn = Neo4jEmbedded(db)

// same API with conn...
```

## Contributors
* Wes Freeman: @wfreeman on github
* Jason Jackson: @jasonjackson on github
* Julien Sirocchi: @sirocchj on github
* Pieter-Jan Van Aeken: @PieterJanVanAeken on github
* @okumin on github
* @mvallerie on github
* Geoff Moes: @elegantcoding on github

## Thanks
* The Play Framework team for providing the Anorm library, the basis for this library.
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
