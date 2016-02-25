package org.anormcypher

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._ // scalastyle:ignore underscore.import

import play.extras.iteratees.CharString
import play.extras.iteratees.Encoding
import play.extras.iteratees.JsonIteratees
import play.extras.iteratees.Combinators.drop
import play.extras.iteratees.Combinators.expect
import play.extras.iteratees.Combinators.peekOne
import play.extras.iteratees.Combinators.skipWhitespace
import play.extras.iteratees.JsonParser.jsonString
import play.extras.iteratees.JsonIteratees.jsSimpleArray


/** Iteratee parsers for Neo4j json response */
object Neo4jStream {

  /**
   * Parses the column name meta data.
   *
   * Consumes open brace, "columns", colon and
   * returns the following array content as meta data.
   */
  // TODO: match string "columns"
  def columns(
      implicit ec: ExecutionContext): Iteratee[CharString, MetaData] = for {
    _ <- skipWhitespace
    _ <- expect('{');      _ <- skipWhitespace
    colKey <- jsonString;  _ <- skipWhitespace
    _ <- expect(':');      _ <- skipWhitespace
    jsarr <- jsSimpleArray
    jsres = Json.fromJson[Seq[String]](jsarr)
  } yield {
    MetaData(jsres.get.map(c => MetaDataItem(c, false, "String")).toList)
  }

  /** Consumes comma, "data", colon, bracket (data value is array of array) */
  // TODO: match string "data"
  def openDataSeq(
      implicit ec: ExecutionContext): Iteratee[CharString, Unit] = for {
    _ <- skipWhitespace
    _ <- expect(',');      _ <- skipWhitespace
    dataKey <- jsonString; _ <- skipWhitespace
    _ <- expect(':');      _ <- skipWhitespace
    _ <- expect('[');      _ <- skipWhitespace
  } yield ()

  /**
   * Parses one row of the result set, returning an empty list at the end
   *
   * Returns the content of one array in a Seq; can be used with
   * Enumeratee.grouped to repeatedly parse the result set till it
   * reaches the end.
   *
   * An empty list is also returned on any non-array starting char where
   * an open bracket is expected.
   */
  def row(
      implicit ec: ExecutionContext): Iteratee[CharString, Seq[Any]] = for {
    ch <- peekOne
    result <- ch match {
      case Some('[') => for {
        jsarr <- jsSimpleArray
        jsres = Json.fromJson[Seq[Any]](jsarr)(Neo4jREST.seqReads)
      } yield jsres.get
      case None => Done[CharString, Seq[Any]](Seq.empty, Input.EOF)
      case in@_ => drop(1).flatMap(_ => row)
    }
  } yield result

  /** Adapts a stream of byte array to a stream of CypherResultRow */
  def parse(
      source: Enumerator[Array[Byte]])
      (implicit ec: ExecutionContext): Enumerator[CypherResultRow] = {
    val decoded: Enumerator[CharString] = source &> Encoding.decode()

    val futEnumer: Future[Enumerator[CypherResultRow]] = for {
      (meta, afterColumns) <- Concurrent.runPartial(decoded, columns)
      (_, inDataSeq) <- Concurrent.runPartial(afterColumns, openDataSeq)
    } yield {
      val seqAny = inDataSeq &> Enumeratee.grouped(row) ><>
                   Enumeratee.filter(!_.isEmpty)
      seqAny.map(s => CypherResultRow(meta, s.toList))
    }

    Enumerator.flatten(futEnumer)
  }

  /**
   * Turns a neo4j error response into an Error iteratee, containing
   * the 'message' portion of the original neo4j error response
   */
  def errMsg[A](
      source: Enumerator[Array[Byte]])(implicit ec: ExecutionContext):
    Enumerator[A] = new Enumerator[A] {
    override def apply[B](i: Iteratee[A, B]): Future[Iteratee[A, B]] = {
      (source &> Encoding.decode() |>>> JsonIteratees.jsSimpleObject).map {obj =>
        val msg = obj.value.get("message") match {
          case None => ""
          case Some(JsString(v)) => v
          case Some(jsv) => jsv.toString
        }
        play.api.libs.iteratee.Error(msg, Input.EOF)
      }
    }
  }
}
