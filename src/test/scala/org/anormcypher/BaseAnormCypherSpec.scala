package org.anormcypher

import akka.actor._
import akka.stream._
import org.scalatest._
import play.api.libs.ws._

trait BaseAnormCypherSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  implicit val system = ActorSystem("anormcypher-test")
  implicit val materializer = ActorMaterializer()
  implicit val ec = materializer.executionContext

  val wsclient = ahc.AhcWSClient()
  implicit val neo4jrest = Neo4jREST(scala.util.Properties.envOrElse("NEO4J_SERVER", "localhost"))(wsclient)

  val Tag = "anormcyphertest"

  override def afterEach = {
    Cypher(s"match (n:${Tag}) optional match (n)-[r]-() delete n,r;")()
  }

  override def afterAll = {
    wsclient.close()
    materializer.shutdown()
    system.terminate()
  }
}

package async {
  import org.scalatest.concurrent.ScalaFutures

  trait BaseAsyncSpec extends BaseAnormCypherSpec with ScalaFutures {
    import org.scalatest.time._
    implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Minutes))
  }
}
