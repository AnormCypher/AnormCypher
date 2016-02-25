package org.anormcypher

import scala.reflect.ClassTag
import scala.util.control.Exception.allCatch

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
      if (Option(value).isDefined) {
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
      implicit transformer: Column[A]):
    Column[Option[A]] = Column { (value, meta) =>
        if (Option(value).isDefined) {
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
