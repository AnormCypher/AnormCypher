package org.anormcypher

import MayErr.eitherToError
import MayErr.errorToEither
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag
import scala.util.control.Exception.allCatch

abstract class CypherRequestError

case class ColumnNotFound(
    columnName: String,
    possibilities: List[String])
  extends CypherRequestError {

  override def toString: String = {
    columnName + " not found, available columns : " +
    possibilities.map { p => p.dropWhile(_ == '.') }.
      mkString(", ") // scalastyle:ignore multiple.string.literals
  }
}

case class TypeDoesNotMatch(message: String) extends CypherRequestError

case class InnerTypeDoesNotMatch(message: String) extends CypherRequestError

case class UnexpectedNullableFound(on: String) extends CypherRequestError

case object NoColumnsInReturnedResult extends CypherRequestError

case class CypherMappingError(msg: String) extends CypherRequestError

trait Column[A] extends ((Any, MetaDataItem) => MayErr[CypherRequestError, A])

object Column {

  val BIGINT: String = "BigInt"

  def apply[A](
      transformer: ((Any, MetaDataItem) => MayErr[CypherRequestError, A])
    ): Column[A] = new Column[A] {

    def apply(
        value: Any,
        meta: MetaDataItem): MayErr[CypherRequestError, A] = {
      transformer(value, meta)
    }
  }

  def nonNull[A](
      transformer: ((Any, MetaDataItem) => MayErr[CypherRequestError, A])):
    Column[A] = Column[A] {
    case (value, meta@MetaDataItem(qualified, _, _)) =>
      if (value != null) {
        transformer(value, meta)
      } else {
        Left(UnexpectedNullableFound(qualified.toString))
      }
  }

  private def typeMismatchErr(
      obj: Any,
      meta: MetaDataItem,
      typ: String): TypeDoesNotMatch = {
    TypeDoesNotMatch(s"Cannot convert $obj: ${obj.getClass} to" +
                     s" $typ for column ${meta.column}")
  }

  implicit def rowToString: Column[String] = Column.nonNull[String] {
    (value, meta) => value match {
      case s: String => Right(s)
      case x: Any => Left(typeMismatchErr(x, meta, "String"))
    }
  }

  implicit def rowToInt: Column[Int] = Column.nonNull[Int] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidInt => Right(bd.toIntExact)
      case x: Any => Left(typeMismatchErr(x, meta, "Int"))
    }
  }

  implicit def rowToDouble: Column[Double] = Column.nonNull[Double] {
    (value, meta) => value match {
      case bd: BigDecimal => Right(bd.toDouble)
      case x: Any => Left(typeMismatchErr(x, meta, "Double"))
    }
  }

  implicit def rowToFloat: Column[Float] = Column.nonNull[Float] {
    (value, meta) => value match {
      case bd: BigDecimal => Right(bd.toFloat)
      case x: Any => Left(typeMismatchErr(x, meta, "Float"))
    }
  }

  implicit def rowToShort: Column[Short] = Column.nonNull[Short] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidShort => Right(bd.toShortExact)
      case x: Any => Left(typeMismatchErr(x, meta, "Short"))
    }
  }

  implicit def rowToByte: Column[Byte]= Column.nonNull[Byte] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidByte => Right(bd.toByteExact)
      case x: Any => Left(typeMismatchErr(x, meta, "Byte"))
    }
  }

  implicit def rowToBoolean: Column[Boolean] = Column.nonNull[Boolean] {
    (value, meta) => value match {
      case b: Boolean => Right(b)
      case x: Any => Left(typeMismatchErr(x, meta, "Boolean"))
    }
  }

  implicit def rowToLong: Column[Long] = Column.nonNull[Long] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidLong => Right(bd.toLongExact)
      case x: Any => Left(typeMismatchErr(x, meta, "Long"))
    }
  }

  implicit def rowToNeoNode: Column[NeoNode] = Column.nonNull[NeoNode] {
    (value, meta) => value match {
      case msa: Map[_, _] if msa.keys.forall(_.isInstanceOf[String]) =>
        Neo4jREST.asNode(msa.asInstanceOf[Map[String, Any]])
      case x: Any => Left(typeMismatchErr(x, meta, "NeoNode"))
    }
  }

  implicit def rowToNeoRelationship: Column[NeoRelationship] = {
    Column.nonNull {
      (value, meta) => value match {
        case msa: Map[_, _] if msa.keys.forall(_.isInstanceOf[String]) =>
          Neo4jREST.asRelationship(msa.asInstanceOf[Map[String, Any]])
        case x: Any => Left(typeMismatchErr(x, meta, "NeoRelationship"))
      }
    }
  }

  implicit def rowToBigInt: Column[BigInt] = Column.nonNull[BigInt] {
    (value, meta) => value match {
      case bd: BigDecimal => bd.toBigIntExact() match {
        case Some(bi) => Right(bi)
        case None => Left(typeMismatchErr(bd, meta, BIGINT))
      }
      case x: Any => Left(typeMismatchErr(x, meta, BIGINT))
    }
  }

  implicit def rowToBigDecimal: Column[BigDecimal] = {
    Column.nonNull[BigDecimal] {
      (value, meta) => value match {
        case b: BigDecimal => Right(b)
        case x: Any => Left(typeMismatchErr(x, meta, "BigDecimal"))
      }
    }
  }

  implicit def rowToOption[A, B](
      implicit transformer: Column[A]): Column[Option[A]] = Column {
    (value, meta) => if (value != null) {
      transformer(value, meta).map(Some(_))
    } else {
      (Right(None): MayErr[CypherRequestError, Option[A]])
    }
  }

  def checkSeq[A: ClassTag](
      seq: Seq[Any],
      meta: MetaDataItem)(mapFun: (Any) => A):
    MayErr[CypherRequestError, Seq[A]] = {
    allCatch.either {
      seq.map(mapFun)
    } fold(
      throwable => Left(
        InnerTypeDoesNotMatch(s"Cannot convert $seq:${seq.getClass} to " +
        s"Seq[${implicitly[ClassTag[A]].runtimeClass}] for column " +
        s"${meta.column}: ${throwable.getLocalizedMessage}")
      ),
      result => Right(result)
    )
  }

  implicit def rowToSeqString: Column[Seq[String]] = {
    Column.nonNull[Seq[String]] {
      (value, meta) => value match {
        case xs: Seq[_] => checkSeq[String](xs, meta) {
          case s: String => s
          case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to String")
        }
        case x: Any => Left(typeMismatchErr(x, meta, "Seq[String]"))
      }
    }
  }

  implicit def rowToSeqInt: Column[Seq[Int]] = Column.nonNull[Seq[Int]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Int](xs, meta) {
        case bd: BigDecimal if bd.isValidInt => bd.toIntExact
        case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to Int")
      }
      case x: Any => Left(typeMismatchErr(x, meta, "Seq[Int]"))
    }
  }

  implicit def rowToSeqLong: Column[Seq[Long]] = Column.nonNull[Seq[Long]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Long](xs, meta) {
        case bd: BigDecimal if bd.isValidLong => bd.toLongExact
        case x: Any => throw new RuntimeException(
            s"Cannot convert $x: ${x.getClass} to Long")
      }
      case x: Any => Left(typeMismatchErr(x, meta, "Seq[Long]"))
    }
  }

  implicit def rowToSeqBoolean: Column[Seq[Boolean]] = {
    Column.nonNull[Seq[Boolean]] {
      (value, meta) => value match {
        case xs: Seq[_] => Column.checkSeq[Boolean](xs, meta) {
          case b: Boolean => b
          case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to Boolean")
        }
        case x: Any => Left(typeMismatchErr(x, meta, "Seq[Boolean]"))
      }
    }
  }

  implicit def rowToSeqDouble: Column[Seq[Double]] = {
    Column.nonNull[Seq[Double]] {
      (value, meta) => value match {
        case xs: Seq[_] => checkSeq[Double](xs, meta) {
          case bd: BigDecimal => bd.toDouble
          case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to Double")
        }
        case x: Any => Left(typeMismatchErr(x, meta, "Seq[Double]"))
      }
    }
  }

  implicit def rowToSeqNeoRelationship: Column[Seq[NeoRelationship]] = {
    Column.nonNull[Seq[NeoRelationship]] {
      (value, meta) => value match {
        case xs: Seq[_] => checkSeq[NeoRelationship](xs, meta) {
          case msa: Map[_,_] if msa.keys.forall(_.isInstanceOf[String]) => {
            Neo4jREST.asRelationship(msa.asInstanceOf[Map[String, Any]]) fold(
              error => throw new RuntimeException(error match {
                case TypeDoesNotMatch(msg) => msg
                case _ => ""
              }),
              value => value
              )
          }
          case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to NeoRelationship")
        }
        case x: Any => Left(typeMismatchErr(x, meta, "Seq[NeoRelationship]"))
      }
    }
  }

  implicit def rowToSeqNeoNode: Column[Seq[NeoNode]] = Column.nonNull {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[NeoNode](xs, meta) {
        case msa: Map[_,_] if msa.keys.forall(_.isInstanceOf[String]) => {
          Neo4jREST.asNode(msa.asInstanceOf[Map[String, Any]]) fold(
            error => throw new RuntimeException(error match {
              case TypeDoesNotMatch(msg) => msg
              case _ => ""
            }),
            value => value
            )
        }
        case x: Any => throw new RuntimeException(
            s"Cannot convert $x: ${x.getClass} to NeoNode")
      }
      case x: Any => Left(typeMismatchErr(x, meta, "Seq[NeoNode]"))
    }
  }

  implicit def rowToSeqMapStringString: Column[Seq[Map[String, String]]] = {
    Column.nonNull[Seq[Map[String, String]]] {
      (value, meta) => value match {
        case xs: Seq[_] => checkSeq[Map[String,String]](xs, meta) {
          case m: Map[String,String] => m
          case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to Map[String,String]")
        }
        case x: Any => Left(
            typeMismatchErr(x, meta, "Seq[Map[String,String]]"))
      }
    }
  }

  implicit def rowToSeqMapStringAny: Column[Seq[Map[String, Any]]] = {
    Column.nonNull[Seq[Map[String, Any]]] {
      (value, meta) => value match {
        case xs: Seq[_] => checkSeq[Map[String,Any]](xs, meta) {
          case m: Map[String,Any] => m
          case x: Any => throw new RuntimeException(
              s"Cannot convert $x: ${x.getClass} to Map[String,Any]")
        }
        case x: Any => Left(typeMismatchErr(x, meta, "Seq[Map[String,Any]]"))
      }
    }
  }

}

object CypherRow {
  def unapplySeq(row: CypherRow): Option[List[Any]] = Some(row.asList)
}

trait CypherRow {

  protected[anormcypher] val data: List[Any]
  protected[anormcypher] val metaData: MetaData

  lazy val asList = data.zip(metaData.ms.map(_.nullable)).
                        map { i => if (i._2) Option(i._1) else i._1 }

  lazy val asMap: Map[String, Any] =
    metaData.ms.map(_.column).zip(asList).toMap

  def get[A](
      a: String)
      (implicit c: Column[A]): MayErr[CypherRequestError, A] = {
    CypherParser.get(a)(c)(this) match {
      case Success(a) => Right(a)
      case Error(e) => Left(e)
    }
  }

  private def getType(t: String) = t match {
    case "long" => Class.forName("java.lang.Long")
    case "int" => Class.forName("java.lang.Integer")
    case "boolean" => Class.forName("java.lang.Boolean")
    case _ => Class.forName(t)
  }

  private lazy val ColumnsDictionary: Map[String, Any] =
    metaData.ms.map(_.column.toUpperCase).zip(data).toMap

  private[anormcypher] def get1(
      a: String): MayErr[CypherRequestError, Any] = {
    for {
      meta <- metaData.
                get(a).
                toRight(ColumnNotFound(a, metaData.availableColumns));
      (column, nullable, clazz) = meta;
      result <- ColumnsDictionary.
                  get(column.toUpperCase).
                  toRight(ColumnNotFound(column, metaData.availableColumns))
    } yield result
  }

  def apply[B](a: String)(implicit c: Column[B]): B = get[B](a)(c).get

}

case class MockRow(metaData: MetaData, data: List[Any]) extends CypherRow

case class CypherResultRow(
    metaData: MetaData,
    data: List[Any]) extends CypherRow {

  override def toString(): String = {
    s"CypherResultRow(${metaData.
      ms.
      zip(data).
      map(t => "'" + t._1.column + "':" + t._2 + " as " + t._1.clazz).
      mkString(", ")})"
  }

}

case class CypherStatement(query: String, params: Map[String, Any] = Map()) {

  def apply()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Seq[CypherResultRow] = Await.result(async(), 30.seconds)

  def async()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Seq[CypherResultRow]] = connection.sendQuery(this)

  def on(args: (String, Any)*): CypherStatement = {
    this.copy(params = params ++ args)
  }

  def execute()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Boolean = {
    var retVal = true // scalastyle:ignore var.local
    try {
      // throws an exception on a query that doesn't succeed.
      apply()
    } catch {
      case e: Exception => retVal = false
    }
    retVal
  }

  def as[T](
      parser: CypherResultSetParser[T])
      (implicit connection: Neo4jREST, ec: ExecutionContext): T = {
    Cypher.as[T](parser, apply())
  }

  def list[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Seq[A] = {
    as(rowParser.*)
  }

  def single[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): A = {
    as(CypherResultSetParser.single(rowParser))
  }

  def singleOpt[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Option[A] = {
    as(CypherResultSetParser.singleOpt(rowParser))
  }

  def parse[T](
      parser: CypherResultSetParser[T])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): T =
    Cypher.parse[T](parser, apply())

  def executeAsync()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Boolean] = {
    val p = Promise[Boolean]()
    async().onComplete { x => p.success(x.isSuccess) }
    p.future
  }

  def asAsync[T](
      parser: CypherResultSetParser[T])
      (implicit connection: Neo4jREST, ec: ExecutionContext): Future[T] = {
    Cypher.as[T](parser, async())
  }

  def listAsync[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Seq[A]] = asAsync(rowParser.*)

  def singleAsync[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[A] = asAsync(CypherResultSetParser.single(rowParser))

  def singleOptAsync[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Option[A]] = asAsync(CypherResultSetParser.singleOpt(rowParser))

  def parseAsync[T](
      parser: CypherResultSetParser[T])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Future[T] = {
    Cypher.parse[T](parser, async())
  }

  override def toString(): String = query
}


object Cypher {

  def apply(cypher: String): CypherStatement = CypherStatement(cypher)

  def as[T](
      parser: CypherResultSetParser[T],
      rs: Future[Seq[CypherResultRow]])
      (implicit ec: ExecutionContext): Future[T] = rs.map { as(parser,_) }

  def as[T](
      parser: CypherResultSetParser[T],
      rs: Seq[CypherResultRow]): T = parser(rs) match {
    case Success(a) => a
    case Error(e) => sys.error(e.toString)
  }

  def parse[T](
      parser: CypherResultSetParser[T],
      rs: Future[Seq[CypherResultRow]])
      (implicit ec: ExecutionContext): Future[T] = rs.map { s =>
    parse[T](parser,s)
  }

  def parse[T](
      parser: CypherResultSetParser[T],
      rs: Seq[CypherResultRow]): T = parser(rs) match {
    case Success(a) => a
    case Error(e) => sys.error(e.toString)
  }

}
