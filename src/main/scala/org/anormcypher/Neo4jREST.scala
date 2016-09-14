package org.anormcypher

import akka.stream._, scaladsl._
import play.api.http.HttpVerbs.{DELETE, POST}
import play.api.libs.json._, Json._
import play.api.libs.ws._
import scala.concurrent._, duration._

class Neo4jREST(val wsclient: WSClient, val host: String, val port: Int,
  val username: String, val password: String, val https: Boolean) extends Neo4jConnection {
  import Neo4jREST._

  private val headers = Seq(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json",
    "X-Stream" -> "true",
    "User-Agent" -> "AnormCypher/0.9.0"
  )

  protected val baseUrl = {
    val protocol = if (https) "https" else "http"
    s"$protocol://$host:$port/db/data/transaction"
  }

  // request body to begin a transaction
  val EmptyStatements = "{\"statements\":[]}"
  @inline private def txEndpoint(txid: String) = baseUrl + "/" + txid
  @inline private def txCommitUrl(txid: String) = txEndpoint(txid) + "/commit"

  val AutocommitEndpoint = baseUrl + "/commit"

  protected def request(endpoint: String): WSRequest = {
    val req = wsclient.url(endpoint).withHeaders(headers:_*)
    // TODO: allow different authentication schemes
    if (username.isEmpty) req else req.withAuth(username, password, WSAuthScheme.BASIC)
  }

  override def streamAutocommit(stmt: CypherStatement)(implicit mat: Materializer) = {
    val req = request(AutocommitEndpoint).withMethod(POST)
    val source: Future[StreamedResponse] = req.withBody[JsValue](Json.toJson(wrapCypher(stmt))).stream()

    // Enumerator.flatten(source map { case (resp, body) =>
    //     Neo4jStream.parse(body)
    // })
  }

  private[anormcypher] override def executeInTx(stmt: CypherStatement)(
    implicit tx: Neo4jTransaction, ec: ExecutionContext) =
    for {
      resp <- request(txEndpoint(tx.txId)).post(Json.toJson(wrapCypher(stmt)))
    } yield {
      raiseErrIfAny("execute", resp)
      val res = (resp.json \ "results").get.as[Array[JsObject]]
      val cypherRESTResult = Json.fromJson[CypherRESTResult](res(0))(cypherRESTResultReads).get
      val metaDataItems = cypherRESTResult.columns.map {
        c => MetaDataItem(c, false, "String")
      }.toList
      val metaData = MetaData(metaDataItems)
      cypherRESTResult.data.map {
        d => CypherResultRow(metaData, d.row.toList)
      }
    }

  private[anormcypher] def raiseErrIfAny(msg: String, resp: WSResponse) = {
    def raiseErr = throw new RuntimeException(
      s"${msg}: Server responded with code [${resp.status}] and body: ${resp.body}")

    if (resp.status != 200) raiseErr
      (resp.json \ "errors").get.asOpt[Array[JsValue]] match {
        case Some(arr) if !arr.isEmpty => raiseErr
        case _ =>
      }
  }

  private[anormcypher] override def beginTx(implicit ec: ExecutionContext) =
    for {
      resp <- request(baseUrl).post(EmptyStatements)
      endpoint = (resp.json \ "commit").get.as[String]
      part = endpoint.substring(baseUrl.length + 1)
      txid = part.substring(0, part.lastIndexOf('/'))
    } yield new Neo4jRestTransaction(txid)

  // TODO: configure timeout when waiting for server response
  case class Neo4jRestTransaction(override val txId: String) extends Neo4jTransaction {
    override def cypher(stmt: CypherStatement)(implicit ec: ExecutionContext) = executeInTx(stmt)(this, ec)

    override def cypherStream(stmt: CypherStatement)(implicit ec: ExecutionContext) =
      nosup("Streaming is not supported in open transactions")

    override def commit(implicit ec: ExecutionContext) =
      raiseErrIfAny(s"Unable to commit transaction [$txId]", Await.result(
        request(txCommitUrl(txId)).post(EmptyStatements), 10.seconds))

    // sometimes the transaction is rolled back automatically by a bad request
    // in this case, our call to rollback would also return an error
    override def rollback(implicit ec: ExecutionContext) =
      Await.result(request(txEndpoint(txId)).delete(), 10.seconds)
  }

}

object Neo4jREST {
  def apply(host: String = "localhost", port: Int = 7474, username: String = "", password: String = "", https: Boolean = false)(implicit wsclient: WSClient) =
    new Neo4jREST(wsclient, host, port, username, password, https)

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
      case (k, JsObject(o)) => k -> read(o.toSeq)
      case (k, JsArray(ss)) if (ss.forall(_.isInstanceOf[JsObject]))=>
        k -> ss.asInstanceOf[Seq[JsObject]].map(o => read(o.value.toSeq))
      case _ => throw new RuntimeException(s"unsupported type")
    }).toMap

    def reads(json: JsValue) = json match {
      case JsObject(xs) => JsSuccess(read(xs.toSeq))
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
            case sm: Seq[Map[_,_]] if (sm.forall(_.isInstanceOf[Map[String,Any]])) =>
              key -> JsArray(sm.map(m => writes(m.asInstanceOf[Map[String,Any]])))
            case xs: Seq[_] => throw new RuntimeException(s"unsupported Neo4j array type: $xs (mixed types?)")
            case x => throw new RuntimeException(s"unsupported Neo4j type: $x")
          }
          ret
        }
      }.toSeq: _*)
  }

  case class CypherStatements(statements: Seq[CypherStatement])

  @inline private[anormcypher] def wrapCypher(stmt: CypherStatement): CypherStatements = CypherStatements(Seq(stmt))
  implicit val cypherStatementWrites = Json.writes[CypherStatement]
  implicit val statementsWrites: Writes[CypherStatements] = Json.writes[CypherStatements]

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

  implicit val Neo4jResultSetReads = Json.reads[Neo4jResultSet]
  implicit val cypherRESTResultReads = Json.reads[CypherRESTResult]

  object IdURLExtractor {
    def unapply(s: String) = s.lastIndexOf('/') match {
      case pos if pos >= 0 => Some(s.substring(pos + 1).toLong)
      case _ => None
    }
  }

  def asNode(msa: Map[String, Any]): MayErr[CypherRequestError, NeoNode] =
    Right(NeoNode(msa))

  def asRelationship(msa: Map[String, Any]): MayErr[CypherRequestError, NeoRelationship] =
    Right(NeoRelationship(msa))
}

case class CypherRESTResult(columns: Vector[String], data: Seq[Neo4jResultSet])
case class Neo4jResultSet(row: Seq[Any])

case class NeoNode(props: Map[String, Any])

case class NeoRelationship(props: Map[String, Any])
