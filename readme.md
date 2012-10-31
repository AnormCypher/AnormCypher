## Warning
This library is not usable yet. We'll update this readme when it's ready for some use.

## AnormCypher
This is a Neo4j library purely for REST, using the Jerkson JSON parser and the Dispatch REST client library.

The goals of this library are to provide a great API to use Cypher, and it will be modeled after Anorm from Play,
which I found to be pleasant to use with SQL. More info about Anorm can be found here:
http://www.playframework.org/documentation/2.0.4/ScalaAnorm

Following this library will be the creation of play2-AnormCypher, the plugin for Play2.

[![Build Status](https://travis-ci.org/AnormCypher/AnormCypher.png)](https://travis-ci.org/AnormCypher/AnormCypher)

## Usage
You'll probably notice that this usage is very close to Play's Anorm. That is the idea!

### Configuring a server
The default is localhost, but you can specify a special server when your app is starting via the `setServer` or `setURL` 
options. Authentication and multi-server support will come soon.
``` Scala
import anormcypher._

Neo4jREST.setServer("localhost", 7474, "/db/data/")
```

### Executing Cypher Queries

To start you need to learn how to execute Cypher queries.

First, `import anormcypher._`, and then simply use the Cypher object to create queries. 

``` Scala
import anormcypher._ 

val result: Boolean = Cypher("START n=node(0) RETURN n").execute()    
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
The first way to access the results of a return query is to use the Stream API.

When you call `apply()` on any Cypher statement, you will receive a lazy `Stream` of `Row` instances, where each row can be seen as a dictionary:

``` Scala
// Create Cypher query
val allCountries = Cypher("start n=node(*) where n.type='Country' return n.code as code, n.name as name;")
 
// Transform the resulting Stream[Row] to a List[(String,String)]
val countries = allCountries().map(row => 
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

### Using Pattern Matching
You can also use Pattern Matching to match and extract the Row content. In this case the column name doesn’t matter. Only the order and the type of the parameters is used to match.

The following example transforms each row to the correct Scala type:

``` Scala
case class SmallCountry(name:String) 
case class BigCountry(name:String) 
case class France
 
val countries = Cypher("start n=node(*) where n.type='Country' return n.name as name, n.population as pop")().collect {
  case Row("France", _) => France()
  case Row(name:String, pop:Int) if(pop > 1000000) => BigCountry(name)
  case Row(name:String, _) => SmallCountry(name)      
}
```

Note that since `collect(…)` ignores the cases where the partial function isn’t defined, it allows your code to safely ignore rows that you don’t expect.

### Special data types
#### Nodes

Nodes can be extracted as so:

``` Scala
Cypher("start n=node(*) where n.type='Country' return n.name as name, n")().map {
  case Row(name: String, n: anormcypher.Node) => name -> n
}
```  
Here we specifically chose to use map, as we want an exception if the row isn’t in the format we expect.

A `Node` is just a simple Scala `case class`, not quite as type-safe as configuring your own:
``` Scala
case class Node(id:Int, props:Map[String,Any])
```

#### Relationships

Relationships can be extracted as so:

``` Scala
Cypher("start n=node(*) match n-[r]-m where has(n.name) return n.name as name, r;")().map {
  case Row(name: String, r: anormcypher.Relationship) => name -> r
}
```  
Here we specifically chose to use map, as we want an exception if the row isn’t in the format we expect.

Similarly, a `Relationship` is just a simple Scala `case class`, not quite as type-safe as configuring your own:
``` Scala
case class Relationship(id:Int, props:Map[String,Any])
```

### Dealing with Nullable columns
If a column can contain `Null` values in the database schema, you need to manipulate it as an `Option` type.

For example, the `indepYear` of the `Country` table is nullable, so you need to match it as `Option[Int]`:

``` Scala
Cypher("start n=node(*) where n.type='Country' return n.name as name, n.indepYear as year;")().collect {
  case Row(name:String, Some(year:Int)) => name -> year
}
```

If you try to match this column as `Int` it won’t be able to parse `Null` values. Suppose you try to retrieve the column content as `Int` directly from the dictionary:
``` Scala
Cypher("start n=node(*) where n.type='Country' return n.name as name, n.indepYear as indepYear;")().map { row =>
  row[String]("name") -> row[Int]("indepYear")
}
```

This will produce an `UnexpectedNullableFound(COUNTRY.INDEPYEAR)` exception if it encounters a null value, so you need to map it properly to an `Option[Int]`, as:

``` Scala
Cypher("start n=node(*) where n.type='Country' return n.name as name, n.indepYear as indepYear;")().map { row =>
  row[String]("name") -> row[Option[Int]]("indepYear")
}
```

This is also true for the parser API, as we will see next.

### Using the Parser API
You can use the parser API to create generic and reusable parsers that can parse the result of any Cypher query.

Note: This is really useful, since most queries in a web application will return similar data sets. For example, if you have defined a parser able to parse a Country from a result set, and another Language parser, you can then easily compose them to parse both Country and Language from a single return.

First you need to import `anormcypher.CypherParser._`

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
  Cypher("start n=node(*) where n.type='Country' return n.*").as( str("n.name") ~ int("n.population") * ) 
}
```

As you see, this query’s result type is `List[String~Int]` - a list of country name and population items.

You can also rewrite the same code as:

``` Scala
val result:List[String~Int] = {
  Cypher("start n=node(*) where n.type='Country' return n.*").as(get[String]("n.name")~get[Int]("n.population")*) 
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
  Cypher("start n=node(*) where n.type='Country' return n.*").as(
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
* Wes Freeman: @wfreeman on github
* Jason Jackson: @jasonjackson on github

## Thanks
* The Play Framework team for providing the Anorm library, the basis for this library.
* Coda Hale, for the Jerkson library
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
