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