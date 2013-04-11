package org.anormcypher.newapi

import language.implicitConversions
import scala.reflect.ClassTag

trait CypherValue

trait CypherValueConverter[A, B <: CypherValue] {
  def map: (A ⇒ B)

  def comap: (B ⇒ Option[A])
}

trait CypherRequestConverter[A <: CypherValue, B] extends (CypherRequest[A] ⇒ B)

trait CypherResponseConverter[A <: CypherValue, B] extends (CypherResponse[A] ⇒ B)

case class CypherRequest[A <: CypherValue](query: String, params: Seq[(String, A)] = Seq.empty) {
  def on[B, C >: A <: CypherValue](pair: (String, B))(implicit converter: CypherValueConverter[B, C]) =
    CypherRequest(query, params :+ (pair._1 → converter.map(pair._2)))

  def serialize[B](implicit f: CypherRequestConverter[A, B]) = f(this)
}

case class CypherResponse[A <: CypherValue](columns: Seq[String], rows: Seq[Seq[A]]) {
  def deserialize[B](implicit f: CypherResponseConverter[A, B]) = f(this)
}

trait CypherSupport {
  def cypher(query: String) = new CypherRequest(query)

  implicit def fun2cValueConverter[A, B <: CypherValue](mapFun: (A ⇒ B), comapFun: (B ⇒ Option[A])) =
    new CypherValueConverter[A, B] {
      val map = mapFun
      val comap = comapFun
    }

  implicit def fun2cRequestConverter[A <: CypherValue, B](f: (CypherRequest[A] ⇒ B)) =
    new CypherRequestConverter[A, B] {
      def apply(a: CypherRequest[A]) = f apply a
    }

  implicit def fun2cResponseConverter[A <: CypherValue, B](f: (CypherResponse[A] ⇒ B)) =
    new CypherResponseConverter[A, B] {
      def apply(a: CypherResponse[A]) = f apply a
    }
}

trait MapSupport {
  self: CypherSupport ⇒

  sealed trait EmbeddedCypherValue extends CypherValue {
    def value: Any
  }

  implicit def any2cv[A: ClassTag] = new CypherValueConverter[A, EmbeddedCypherValue] {
    val map = (a: A) ⇒ new EmbeddedCypherValue {
      val value = identity(a)
    }
    val comap = (ecv: EmbeddedCypherValue) => ecv.value match {
      case x: A ⇒ Some(x)
      case _ ⇒ None
    }
  }

  implicit val cr0p2query = new CypherRequestConverter[Nothing, String] {
    def apply(obj: CypherRequest[Nothing]) = obj.query
  }
  implicit val cr2queryAndParams: CypherRequestConverter[EmbeddedCypherValue, (String, Map[String, Any])] =
    (obj: CypherRequest[EmbeddedCypherValue]) ⇒
      obj.query → obj.params.groupBy(_._1).mapValues(seq ⇒ seq.map(_._2.value).head
      )
}

object CypherEmbedded extends CypherSupport with MapSupport