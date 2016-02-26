package org.anormcypher

import play.api.libs.iteratee._
import play.api.libs.json._
import scala.concurrent._

/** Iteratee parsers for Neo4j json response */
object Neo4jStream {
  import play.extras.iteratees._, JsonParser._, JsonEnumeratees._, JsonIteratees._, Combinators._
  // alias to shorten signatures
  type R[T] = Iteratee[CharString, T]

  def err(msg: String): R[Unit] = play.api.libs.iteratee.Error(msg, Input.EOF)

  def matchString(value: String): R[Unit] = {
    def step(matched: String, remainder: String): R[Unit] =
      if (remainder.isEmpty()) Done(Unit, Input.Empty) else Cont {
        case Input.EOF => err(
          s"Premature end of input, asked to match '$value', matched '$matched', expecting '$remainder'")
        case Input.Empty =>
          step(matched, remainder)
        case Input.El(data) =>
          val in = data.mkString
          // upstream can send "" instead of Input.Empty
          if (in.isEmpty)
            step(matched, remainder)
          else if (remainder.startsWith(in)) // matched a prefix
            if (remainder.length == in.length) // exact match
              Done(Unit, Input.Empty)
            else // still have some leftover to match
              step(matched + in, remainder.substring(in.length))
          else if (in.startsWith(remainder)) // complete match but with leftover in the input
            Done(Unit, Input.El(CharString.fromString(in.substring(remainder.length()))))
          else
            err(s"Asked to match '$value', matched '$matched', expecting '$remainder', but found '$in'")
      }

    step("", value)
  }

  /**
   * Parses the column name meta data.
   *
   * Consumes open brace, "columns", colon and
   * returns the following array content as meta data.
   */
  // TODO: match string "columns"
  def columns(implicit ec: ExecutionContext): R[MetaData] = for {
    _ <- skipWhitespace
    _ <- expect('{');      _ <- skipWhitespace
    colKey <- jsonString;  _ <- skipWhitespace
    _ <- expect(':');      _ <- skipWhitespace
    jsarr <- jsSimpleArray
    jsres = Json.fromJson[Seq[String]](jsarr)
  } yield MetaData(jsres.get.map(c => MetaDataItem(c, false, "String")).toList)

  /** Consumes comma, "data", colon, bracket (data value is array of array) */
  // TODO: match string "data"
  def openDataSeq(implicit ec: ExecutionContext): R[Unit] = for {
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
  def row(implicit ec: ExecutionContext): R[Seq[Any]] = for {
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
  def parse(source: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Enumerator[CypherResultRow] = {
    val decoded: Enumerator[CharString] = source &> Encoding.decode()

    val futEnumer: Future[Enumerator[CypherResultRow]] = for {
      (meta, afterColumns) <- Concurrent.runPartial(decoded, columns)
      (_, inDataSeq) <- Concurrent.runPartial(afterColumns, openDataSeq)
    } yield {
      val seqAny = inDataSeq &> Enumeratee.grouped(row) ><> Enumeratee.filter(!_.isEmpty)
      seqAny.map(s => CypherResultRow(meta, s.toList))
    }

    Enumerator.flatten(futEnumer)
  }

  def parse2(source: Enumerator[Array[Byte]])(implicit ec: ExecutionContext) = {
    val (in, out) = Concurrent.joined[JsObject]
    val objs: Enumeratee[CharString, JsObject] = jsArray(jsValues(jsSimpleObject))
    val results = objs &>> in
    val errors = objs &>> Iteratee.head.map ( _ match {
        case None =>
        case Some(x) => throw new RuntimeException(x.toString)
    })
    val parser = jsObject("errors" -> errors, "results" -> results)
    source &> Encoding.decode() ><> parser
  }

  /**
   * Turns a neo4j error response into an Error iteratee, containing
   * the 'message' portion of the original neo4j error response
   */
  def errMsg[A](source: Enumerator[Array[Byte]])(implicit ec: ExecutionContext):
      Enumerator[A] = new Enumerator[A] {
    override def apply[B](i: Iteratee[A, B]): Future[Iteratee[A, B]] = {
      (source &> Encoding.decode() |>>> JsonIteratees.jsSimpleObject) map { obj =>
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
