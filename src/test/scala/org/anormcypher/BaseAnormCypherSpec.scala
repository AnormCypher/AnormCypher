package org.anormcypher

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest._
import play.api.libs.ws._
import ning._

import scala.concurrent._

trait BaseAnormCypherSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  implicit val system = ActorSystem("anormcypher")
  implicit val materializer = ActorMaterializer()
  val wsclient = NingWSClient()
  implicit val neo4jrest = Neo4jREST(scala.util.Properties.envOrElse("NEO4J_SERVER", "localhost"))(wsclient, materializer)
  implicit val ec = ExecutionContext.global

  val Tag = "anormcyphertest"

  override def afterEach = {
    Cypher(s"match (n:${Tag}) optional match n-[r]-() delete n,r;")()
  }

  override def afterAll = {
    wsclient.close()
  }
}

package async {
  import org.scalatest.concurrent.ScalaFutures

  trait BaseAsyncSpec extends BaseAnormCypherSpec with ScalaFutures {
    import org.scalatest.time._
    implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Minutes))
  }
}
