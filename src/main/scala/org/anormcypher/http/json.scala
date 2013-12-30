package org.anormcypher.http

import play.api.libs.json.Json._
import play.api.libs.json._
import org.anormcypher._

object AnormCypherJsonSerialization {
  implicit val mapFormat = new Format[Map[String, Any]] {
    def read(xs: Seq[(String, JsValue)]): Map[String, Any] = (xs map {
      case (k, JsBoolean(b)) => k -> b
      case (k, JsNumber(n)) => k -> n
      case (k, JsString(s)) => k -> s
      case (k, JsArray(bs)) if (bs.forall(_.isInstanceOf[JsBoolean])) =>
        k -> bs.asInstanceOf[Seq[JsBoolean]].map(_.value)
      case (k, JsArray(ns)) if (ns.forall(_.isInstanceOf[JsNumber])) =>
        k -> ns.asInstanceOf[Seq[JsNumber]].map(_.value)
      case (k, JsArray(ss)) if (ss.forall(_.isInstanceOf[JsString])) =>
        k -> ss.asInstanceOf[Seq[JsString]].map(_.value)
      case (k, JsObject(o)) => k -> read(o)
      case _ => throw new RuntimeException(s"unsupported type")
    }).toMap

    def reads(json: JsValue) = json match {
      case JsObject(xs) => JsSuccess(read(xs))
      case x => JsError(s"json not of type Map[String, Any]: $x")
    }

    def writes(map: Map[String, Any]) =
      Json.obj(map.map {
        case (key, value) => {
          val ret: (String, JsValueWrapper) = value match {
            case b: Boolean => key -> JsBoolean(b)
            case b: Byte => key -> JsNumber(b)
            case s: Short => key -> JsNumber(s)
            case i: Int => key -> JsNumber(i)
            case l: Long => key -> JsNumber(l)
            case f: Float => key -> JsNumber(f)
            case d: Double => key -> JsNumber(d)
            case c: Char => key -> JsNumber(c)
            case s: String => key -> JsString(s)
            case bs: Seq[_] if (bs.forall(_.isInstanceOf[Boolean])) =>
              key -> JsArray(bs.map(b => JsBoolean(b.asInstanceOf[Boolean])))
            case bs: Seq[_] if (bs.forall(_.isInstanceOf[Byte])) =>
              key -> JsArray(bs.map(b => JsNumber(b.asInstanceOf[Byte])))
            case ss: Seq[_] if (ss.forall(_.isInstanceOf[Short])) =>
              key -> JsArray(ss.map(s => JsNumber(s.asInstanceOf[Short])))
            case is: Seq[_] if (is.forall(_.isInstanceOf[Int])) =>
              key -> JsArray(is.map(i => JsNumber(i.asInstanceOf[Int])))
            case ls: Seq[_] if (ls.forall(_.isInstanceOf[Long])) =>
              key -> JsArray(ls.map(l => JsNumber(l.asInstanceOf[Long])))
            case fs: Seq[_] if (fs.forall(_.isInstanceOf[Float])) =>
              key -> JsArray(fs.map(f => JsNumber(f.asInstanceOf[Float])))
            case ds: Seq[_] if (ds.forall(_.isInstanceOf[Double])) =>
              key -> JsArray(ds.map(d => JsNumber(d.asInstanceOf[Double])))
            case cs: Seq[_] if (cs.forall(_.isInstanceOf[Char])) =>
              key -> JsArray(cs.map(c => JsNumber(c.asInstanceOf[Char])))
            case ss: Seq[_] if (ss.forall(_.isInstanceOf[String])) =>
              key -> JsArray(ss.map(s => JsString(s.asInstanceOf[String])))
            case sam: Map[_, _] if (sam.keys.forall(_.isInstanceOf[String])) =>
              key -> writes(sam.asInstanceOf[Map[String, Any]])
            case xs: Seq[_] => throw new RuntimeException(s"unsupported Neo4j array type: $xs (mixed types?)")
            case x => throw new RuntimeException(s"unsupported Neo4j type: $x")
          }
          ret
        }
      }.toSeq: _*)
  }

  implicit object CypherStatementFormat extends Format[CypherStatement] {
    def writes(cs: CypherStatement): JsValue = JsObject(List(
      "query" -> JsString(cs.query),
      "params" -> mapFormat.writes(cs.params)))
  }

  implicit val seqReads = new Reads[Seq[Any]] {
    def read(xs: Seq[JsValue]): Seq[Any] = xs map {
      case JsBoolean(b) => b
      case JsNumber(n) => n
      case JsString(s) => s
      case JsArray(arr) => read(arr)
      case JsNull => null
      case o: JsObject => o.as[Map[String, Any]]
      case _ => throw new RuntimeException(s"unsupported type")
    }

    def reads(json: JsValue) = json match {
      case JsArray(xs) => JsSuccess(read(xs))
      case _ => JsError("json not of type Seq[Any]")
    }
  }

}
