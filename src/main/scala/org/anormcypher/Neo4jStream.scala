package org.anormcypher

import play.api.libs.iteratee._
import play.api.libs.json._
import scala.concurrent._

/** Iteratee parsers for Neo4j json response */
object Neo4jStream {
  import play.extras.iteratees._, JsonParser._, JsonEnumeratees._, JsonIteratees._, Combinators._
  // alias to shorten signatures
  type R[T] = Iteratee[CharString, T]

  def err[T](msg: String): R[T] = play.api.libs.iteratee.Error(msg, Input.EOF)

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

  /** expect a single character, skpping following whitespaces */
  def wsExpect(c: Char)(implicit ec: ExecutionContext): R[Unit] =
    for (_ <- expect(c); _ <- skipWhitespace) yield ()

  @inline def openBrace(implicit ec: ExecutionContext): R[Unit] = wsExpect('{')
  @inline def openBracket(implicit ec: ExecutionContext): R[Unit] = wsExpect('[')

  /** expect a string */
  // TODO: verify that the captured string is the same as expected, raise error otherwise
  def wsExpect(word: String)(implicit ec: ExecutionContext): R[Unit] =
    for (_ <- jsonString; _ <- skipWhitespace) yield ()

  def arrayProperty(name: String)(implicit ec: ExecutionContext): R[Unit] =
    for (_ <- wsExpect(name); _<- wsExpect(':');  _ <- openBracket) yield ()

  def results(implicit ec: ExecutionContext): R[MetaData] = for {
    _ <- skipWhitespace
    _ <- openBrace
    _ <- arrayProperty("results")
    ch <- peekOne
    next <- ch match {
      case Some(']') => drop(1).flatMap(_ => checkErrors)
      case Some('{') => columns
      case Some(x) => err(s"Unexpected character '$x'")
      case None => err("Unexpected EOF")
    }
  } yield next

  /** Extracts error message and raise error, if any */
  def checkErrors(implicit ec: ExecutionContext): R[MetaData] = for {
    _ <- skipWhitespace
    _ <- wsExpect(',')
    _ <- arrayProperty("errors")
    ch <- peekOne
    errObj <- ch match {
      case Some(']') => drop(1).map(_ => MetaData(Nil))
      case Some('{') => jsSimpleObject.flatMap(obj => err(
        obj.value("message").asInstanceOf[JsString].value))
      case Some(x) => err(s"Unexpected character '$x'")
      case None => err("Unexpected EOF")
    }
  } yield errObj

  /**
   * Parses the column name meta data.
   *
   * Consumes <code>{"results":[{"columns":[...], "data":[</code> and
   * returns the following array content as meta data.
   *
   * Returns a MetaData with an empty list if the results are empty.
   */
  def columns(implicit ec: ExecutionContext): R[MetaData] = for {
    _ <- skipWhitespace
    _ <- openBrace
    _ <- wsExpect("columns")
    _ <- wsExpect(':')
    jsarr <- jsSimpleArray
    res = Json.fromJson[Seq[String]](jsarr).get
    _ <- skipWhitespace
    _ <- wsExpect(',')
    _ <- arrayProperty("data")
  } yield MetaData(res.map(c => MetaDataItem(c, false, "String")).toList)

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
    _ <- skipWhitespace
    ch <- peekOne
    result <- ch match {
      case None => Done[CharString, Seq[Any]](Seq.empty, Input.EOF)
      case Some('[') => for {
        jsarr <- jsSimpleArray
        jsres = Json.fromJson[Seq[Any]](jsarr)(Neo4jREST.seqReads)
      } yield jsres.get
      case Some(',') => drop(1).flatMap(_ => row)
      case in@_ => drop(1).flatMap(_ => row)// TODO: parsing error
    }
  } yield result

  /** Adapts a stream of byte array to a stream of CypherResultRow */
  def parse(source: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Enumerator[CypherResultRow] = {
    val decoded: Enumerator[CharString] = source &> Encoding.decode()

    import Concurrent.runPartial
    val futEnumer: Future[Enumerator[CypherResultRow]] = for {
      (meta, inDataSeq) <- runPartial(decoded, results)
    } yield {
      val seqAny = inDataSeq &> Enumeratee.grouped(row) ><> Enumeratee.filter(!_.isEmpty)
      seqAny.map(s => CypherResultRow(meta, s.toList))
    }

    Enumerator.flatten(futEnumer)
  }
}
