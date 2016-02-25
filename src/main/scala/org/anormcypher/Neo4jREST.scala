package org.anormcypher

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import play.api.http.HttpVerbs
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import play.api.libs.json.JsValue
import play.api.libs.json.JsBoolean
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.JsPath
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest

import com.typesafe.scalalogging.StrictLogging

class Neo4jREST(
    wsclient: WSClient,
    val host: String,
    val port: Int,
    val path: String,
    val username: String,
    val password: String,
    val cypherEndpoint: String,
    val https: Boolean = false) extends StrictLogging {

  private val headers = Seq(
    "Accept" -> "application/json", // scalastyle:ignore multiple.string.literals
    "Content-Type" -> "application/json",
    "X-Stream" -> "true",
    "User-Agent" -> "AnormCypher/0.8.1"
  )

  private val baseURL = {
    val protocol = if (https) "https" else "http"
    val pth = path.stripPrefix("/")
    s"$protocol://$host:$port/$pth"
  }

  private val cypherUrl = baseURL + cypherEndpoint

  private def request = {
    val req = wsclient.url(cypherUrl).withHeaders(headers: _*)
    if (username.isEmpty) {
      req
    } else {
      req.withAuth(username, password, WSAuthScheme.BASIC)
    }
  }

  /** Creates a [[WSRequest]] with custom endpoint
    *
    * @param ep an endpoint
    * @return a [[WSRequest]] with the provided endpoint
    */
  private def otherRequest(ep: String): WSRequest = {
    val req = wsclient.url(baseURL + ep).withHeaders(headers: _*)
    if (username.isEmpty) {
      req
    } else {
      req.withAuth(username, password, WSAuthScheme.BASIC)
    }
  }

  /** Asynchronous, non-streaming query */
  def sendQuery(cypherStatement: CypherStatement)
      (implicit ec: ExecutionContext): Future[Seq[CypherResultRow]] =
    query(cypherStatement)(ec) |>>> Iteratee.getChunks[CypherResultRow]

  /** Asynchronous, streaming (i.e. reactive) query */
  def query(stmt: CypherStatement)
      (implicit ec: ExecutionContext): Enumerator[CypherResultRow] = {

    val req = request.withMethod(HttpVerbs.POST)
    val source = req.
                   withBody(
                     Json.toJson(stmt)(Neo4jREST.cypherStatementWrites)).
                   stream()

    Enumerator.flatten(source map { case (resp, body) =>
      if (resp.status == 400) {
        Neo4jStream.errMsg(body)
      } else {
        Neo4jStream.parse(body)
      }
    })
  }

  // scalastyle:off
  /** Sends Cypher data as a transaction
    *
    * A [[CypherTranscation]] is sent to a provided endpoint. The response
    * contains the commit path if the request was made against a
    * '/transaction' endpoint. The response contains an empty String if the
    * request was made against a '/transaction/commit' endpoint.
    *
    * @param cypherTransaction a [[CypherTransaction]] object
    * @param transactionEndpoint the Neo4j transaction endpoint
    * @return a Future containing an optional String response that contains
    * None if the transaction fails, otherwise contains the transaction
    * commit path.
    */
  def sendTransaction(
      cypTrans: CypherTransaction,
      transactionEndpoint: String)
    (implicit ec: ExecutionContext): Future[Option[String]] = {
    val result = otherRequest(transactionEndpoint).post(Json.toJson(cypTrans))
    result.map { response =>
      val strResult = response.body
      if (response.status != 200 && response.status !=201) {
        throw new RuntimeException(strResult)
      }
      val responseJsValue = Json.parse(strResult)
      // Check if the response contains errors
      val badResponse = (responseJsValue \ "errors").get.as[Array[JsValue]]
      if (badResponse.isEmpty) { // no errors, transaction was successful
        val commitPath = (responseJsValue \ "commit").toOption
        commitPath match {
          case Some(cp) =>
            logger.info(s"Transaction '${cypTrans.name}' processed at $cp")
            Some(cp.toString())
          case _ => logger.info(s"Transaction '${cypTrans.name}' completed")
            Some("")
        }  
      } else { // transaction failed
        logger.info(s"Failed transaction: ${cypTrans.name}")
        logger.debug(s"${cypTrans.statements}")
        logger.info(s"${(badResponse.head \ "code").get}")
        logger.info(s"${(badResponse.head \ "message").get}")
        None
      }
    }
  }

  /** Commits an open transaction
    *
    * Closes a transaction by sending an empty body request to '/commit'
    * endpoint.
    *
    * @param transId the transaction id that needs to be closed
    * @return True if a transaction was successfully committed, false otherwise
    */
  def commitTransaction(transId: String)
      (implicit ec: ExecutionContext): Boolean = {
    val commitEndpoint = "/commit"
    val result = otherRequest("transaction" + transId + commitEndpoint).
                   withMethod("POST").
                   execute()
    val res = result.map { response =>
      // If response code is 200, transaction was successfully committed
      val isCommitted = (response.status == 200)
      if (isCommitted) {
        logger.info(s"Transaction $transId closed.")
      } else {
        logger.info(s"Failed to close transaction $transId.")
      }
      isCommitted
    }
    Await.result(res, 10.seconds)
  }
  // scalastyle:on

}

object Neo4jREST {

  private val Self = "self"
  private val Data = "data"
  private val UnsupportedType = "unsupported type"

  def apply(
      host: String = "localhost",
      port: Int = 7474, // scalastyle:ignore magic.number
      path: String = "/db/data/",
      username: String = "",
      password: String = "",
      cypherEndpoint: String = "cypher",
      https: Boolean = false)
      (implicit wsclient: WSClient): Neo4jREST = {
    new Neo4jREST(wsclient, host, port, path, username,
                  password, cypherEndpoint, https)
  }

  implicit val mapFormat: Format[Map[String, Any]] = {
    new Format[Map[String, Any]] {

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
        case (k, JsObject(o)) => k -> read(o.toSeq)
        case _ => throw new RuntimeException(UnsupportedType)
      }).toMap

      def reads(json: JsValue) = json match {
        case JsObject(xs) => JsSuccess(read(xs.toSeq))
        case x: Any => JsError(s"json not of type Map[String, Any]: $x")
      }

      def writes( // scalastyle:ignore cyclomatic.complexity
          map: Map[String, Any]) =
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
                key -> JsArray(bs.map(b =>
                  JsBoolean(b.asInstanceOf[Boolean])))
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
              case sam: Map[_, _] if
                  (sam.keys.forall(_.isInstanceOf[String])) =>
                key -> writes(sam.asInstanceOf[Map[String, Any]])
              case sm: Seq[_] if
                  (sm.forall(_.isInstanceOf[Map[String,Any]])) =>
                key -> JsArray(sm.map { m =>
                         writes(m.asInstanceOf[Map[String,Any]])
                       })
              case xs: Seq[_] => throw new RuntimeException(
                  s"unsupported Neo4j array type: $xs (mixed types?)")
              case x: Any => throw new RuntimeException(
                  s"unsupported Neo4j type: $x")
            }
            ret
          }
        }.toSeq: _*)
    }
  }

  implicit val cypherStatementWrites = Json.writes[CypherStatement]

  implicit val seqReads = new Reads[Seq[Any]] {
    def read(xs: Seq[JsValue]): Seq[Any] = xs map {
      case JsBoolean(b) => b
      case JsNumber(n) => n
      case JsString(s) => s
      case JsArray(arr) => read(arr)
      case JsNull => null
      case o: JsObject => o.as[Map[String, Any]]
      case _ => throw new RuntimeException(UnsupportedType)
    }

    def reads(json: JsValue) = json match {
      case JsArray(xs) => JsSuccess(read(xs))
      case _ => JsError("json not of type Seq[Any]")
    }
  }

  implicit val cypherRESTResultReads = Json.reads[CypherRESTResult]

  object IdURLExtractor {

    def unapply(s: String): Option[Long] = s.lastIndexOf('/') match {
      case pos: Int if pos >= 0 => Some(s.substring(pos + 1).toLong)
      case _ => None
    }

  }

  def asNode(msa: Map[String, Any]): MayErr[CypherRequestError, NeoNode] = {
    (msa.get(Self), msa.get(Data)) match {
      case (Some(IdURLExtractor(id)), Some(props: Map[_, _])) if
          props.keys.forall(_.isInstanceOf[String]) =>
        Right(NeoNode(id, props.asInstanceOf[Map[String, Any]]))
      case x: Any => Left(
          TypeDoesNotMatch("Unexpected type while building a Node"))
    }
  }

  def asRelationship(
      m: Map[String, Any]): MayErr[CypherRequestError, NeoRelationship] = {
    (m.get(Self), m.get("start"), m.get("end"), m.get(Data)) match {
      case (Some(IdURLExtractor(id)),
            Some(IdURLExtractor(sId)),
            Some(IdURLExtractor(eId)),
            Some(props: Map[_, _])) if
          props.keys.forall(_.isInstanceOf[String]) =>
        Right(NeoRelationship(id,
                              props.asInstanceOf[Map[String, Any]],
                              sId,
                              eId))
      case _ => Left(
          TypeDoesNotMatch("Unexpected type while building a relationship"))
    }
  }

}

case class CypherRESTResult(columns: Vector[String], data: Seq[Seq[Any]])

case class NeoNode(id: Long, props: Map[String, Any])

case class NeoRelationship(
    id: Long,
    props: Map[String, Any],
    start: Long,
    end: Long)
