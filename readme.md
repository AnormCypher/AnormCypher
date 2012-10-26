## AnormCypher
This is a Neo4j library purely for REST, using the Jerkson JSON parser and the Dispatch REST client library.

The goals of this library are to provide a great API to use Cypher, and it will be modeled after Anorm from Play,
which I found to be pleasant to use with SQL. More info about Anorm can be found here:
http://www.playframework.org/documentation/2.0.4/ScalaAnorm

Following this library will be the creation of play2-AnormCypher, the plugin for Play2.

## Usage
You'll probably notice that this usage is very close to Play's Anorm. That is the idea!

### Executing Cypher Queries

To start you need to learn how to execute Cypher queries.

First, `import anormcypher._`, and then simply use the Cypher object to create queries. You need a `Connection` to run a query, and you can retrieve one from the `anormcypher.NeoDB` helper


``` Scala
import anormcypher._ 

NeoDB.withConnection { implicit c =>
  val result: Boolean = Cypher("START n=node(0) RETURN n").execute()    
} 
```

The `execute()` method returns a Boolean value indicating whether the execution was successful.

To execute an update, you can use executeUpdate(), which returns the number of `(Nodes, Relationships, Properties)` updated.

`val result: (Int, Int, Int) = Cypher("START n=node(0) DELETE n").executeUpdate()`

Since Scala supports multi-line strings, feel free to use them for complex Cypher statements:

``` Scala
val cypherQuery = Cypher(
  """
    start n=node(1) 
    match n--m
    where c.code = 'FRA';
    return n,m;
  """
)
```

If your Cypher query needs dynamic parameters, you can declare placeholders like `{name}` in the query string, and later assign a value to them:

``` Scala
Cypher(
  """
    start n=node(1) 
    match n--m
    where n.code = {countryCode};
    return n,m;
  """
).on("countryCode" -> "FRA")
```

### Retrieving data using the Stream API
The first way to access the results of a select query is to use the Stream API.

When you call `apply()` on any Cypher statement, you will receive a lazy `Stream` of `Row` instances, where each row can be seen as a dictionary:

``` Scala
// Create an Cypher query
val selectCountries = Cypher("start n=node(*) where n.type='Country' return n.code as code, n.name as name;")
 
// Transform the resulting Stream[Row] to a List[(String,String)]
val countries = selectCountries().map(row => 
  row[String]("code") -> row[String]("name")
).toList
```

In the following example we will count the number of Country entries in the database, so the result set will be a single row with a single column:

``` Scala
// First retrieve the first row
val firstRow = Cypher("start n=node(*) where n.type='Country' return count(n) as c").apply().head
 
// Next get the content of the 'c' column as Long
val countryCount = firstRow[Long]("c")
```

...

## License LGPL
Copyright 2012 Wes Freeman

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
