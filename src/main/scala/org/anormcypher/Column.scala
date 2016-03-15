package org.anormcypher

import scala.reflect.ClassTag
import scala.util.control.Exception.allCatch

trait Column[A] extends ((Any, MetaDataItem) => MayErr[CypherRequestError, A])

object Column {

  def apply[A](transformer: ((Any, MetaDataItem) => MayErr[CypherRequestError, A])): Column[A] = new Column[A] {

    def apply(value: Any, meta: MetaDataItem): MayErr[CypherRequestError, A] = transformer(value, meta)

  }

  def nonNull[A](
      transformer: ((Any, MetaDataItem) => MayErr[CypherRequestError, A]))
    : Column[A] = Column[A] {
    case (value, meta@MetaDataItem(qualified, _, _)) =>
      if (Option(value).isDefined) {
        transformer(value, meta)
      } else {
        Left(UnexpectedNullableFound(qualified.toString))
      }
  }

  implicit def rowToString = Column.nonNull[String] {
    (value, meta) => value match {
      case s: String => Right(s)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to String for column ${meta.column}"))
    }
  }

  implicit def rowToInt = Column.nonNull[Int] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidInt => Right(bd.toIntExact)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Int for column ${meta.column}"))
    }
  }

  implicit def rowToDouble = Column.nonNull[Double] {
    (value, meta) => value match {
      case bd: BigDecimal => Right(bd.toDouble)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to Double for column ${meta.column}"))
    }
  }

  implicit def rowToFloat = Column.nonNull[Float] {
    (value, meta) => value match {
      case bd: BigDecimal => Right(bd.toFloat)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to Float for column ${meta.column}"))
    }
  }

  implicit def rowToShort = Column.nonNull[Short] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidShort => Right(bd.toShortExact)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to Short for column ${meta.column}"))
    }
  }

  implicit def rowToByte = Column.nonNull[Byte] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidByte => Right(bd.toByteExact)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to Byte for column ${meta.column}"))
    }
  }

  implicit def rowToBoolean = Column.nonNull[Boolean] {
    (value, meta) => value match {
      case b: Boolean => Right(b)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Boolean for column ${meta.column}"))
    }
  }

  implicit def rowToLong = Column.nonNull[Long] {
    (value, meta) => value match {
      case bd: BigDecimal if bd.isValidLong => Right(bd.toLongExact)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to Long for column ${meta.column}"))
    }
  }

  implicit def rowToNeoNode = Column.nonNull[NeoNode] {
    (value, meta) => value match {
      case msa: Map[_, _] if msa.keys.forall(_.isInstanceOf[String]) =>
        Neo4jREST.asNode(msa.asInstanceOf[Map[String, Any]])
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to NeoNode for column ${meta.column}"))
    }
  }

  implicit def rowToNeoRelationship: Column[NeoRelationship] = Column.nonNull {
    (value, meta) => value match {
      case msa: Map[_, _] if msa.keys.forall(_.isInstanceOf[String]) =>
        Neo4jREST.asRelationship(msa.asInstanceOf[Map[String, Any]])
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to NeoRelationship for column ${meta.column}"))
    }
  }

  implicit def rowToBigInt = Column.nonNull[BigInt] {
    (value, meta) => value match {
      case bd: BigDecimal => bd.toBigIntExact() match {
        case Some(bi) => Right(bi)
        case None => Left(TypeDoesNotMatch(s"Cannot convert $bd:${bd.getClass} to BigInt for column ${meta.column}"))
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x:${x.getClass} to BigInt for column ${meta.column}"))
    }
  }

  implicit def rowToBigDecimal = Column.nonNull[BigDecimal] {
    (value, meta) => value match {
      case b: BigDecimal => Right(b)
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to BigDecimal for column ${meta.column}"))
    }
  }

  implicit def rowToOption[A, B](
      implicit transformer: Column[A]): Column[Option[A]] = Column {
    (value, meta) => if (Option(value).isDefined) {
      transformer(value, meta).map(Some(_))
    } else {
      (Right(None): MayErr[CypherRequestError, Option[A]])
    }
  }

  def checkSeq[A : ClassTag](seq: Seq[Any], meta: MetaDataItem)(mapFun: (Any) => A): MayErr[CypherRequestError, Seq[A]] = {
    allCatch.either {
      seq.map(mapFun)
    } fold(
      throwable => Left(InnerTypeDoesNotMatch(s"Cannot convert $seq:${seq.getClass} to " +
        s"Seq[${implicitly[ClassTag[A]].runtimeClass}] for column ${meta.column}: ${throwable.getLocalizedMessage}")),
      result => Right(result)
      )
  }

  implicit def rowToSeqString = Column.nonNull[Seq[String]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[String](xs, meta) {
        case s: String => s
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to String")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[String] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqInt = Column.nonNull[Seq[Int]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Int](xs, meta) {
        case bd: BigDecimal if bd.isValidInt => bd.toIntExact
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to Int")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[Int] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqLong = Column.nonNull[Seq[Long]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Long](xs, meta) {
        case bd: BigDecimal if bd.isValidLong => bd.toLongExact
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to Long")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[Long] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqBoolean = Column.nonNull[Seq[Boolean]] {
    (value, meta) => value match {
      case xs: Seq[_] => Column.checkSeq[Boolean](xs, meta) {
        case b: Boolean => b
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to Boolean")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[Boolean] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqDouble = Column.nonNull[Seq[Double]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Double](xs, meta) {
        case bd: BigDecimal => bd.toDouble
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to Double")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[Double] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqNeoRelationship = Column.nonNull[Seq[NeoRelationship]] {
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
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to NeoRelationship")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[NeoRelationship] for column ${meta.column}"))
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
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to NeoNode")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[NeoNode] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqMapStringString = Column.nonNull[Seq[Map[String,String]]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Map[String,String]](xs, meta) {
        case m: Map[String,String] => m
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to Map[String,String]")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[Map[String,String]] for column ${meta.column}"))
    }
  }

  implicit def rowToSeqMapStringAny = Column.nonNull[Seq[Map[String,Any]]] {
    (value, meta) => value match {
      case xs: Seq[_] => checkSeq[Map[String,Any]](xs, meta) {
        case m: Map[String,Any] => m
        case x => throw new RuntimeException(s"Cannot convert $x: ${x.getClass} to Map[String,Any]")
      }
      case x => Left(TypeDoesNotMatch(s"Cannot convert $x: ${x.getClass} to Seq[Map[String,Any]] for column ${meta.column}"))
    }
  }

}
