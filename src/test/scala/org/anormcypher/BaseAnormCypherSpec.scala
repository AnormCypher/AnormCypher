package org.anormcypher

import scala.concurrent.ExecutionContext

import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Minutes
import org.scalatest.time.Span

import play.api.libs.ws.ning.NingWSClient

trait BaseAnormCypherSpec
  extends FlatSpec
  with Matchers
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  val wsclient = NingWSClient()

  implicit val neo4jrest =
    Neo4jREST(scala.util.Properties.envOrElse("NEO4J_SERVER", "localhost"))(wsclient)

  implicit val ec = ExecutionContext.global

  override def afterAll: Unit = wsclient.close()
}

package async {

  trait BaseAsyncSpec extends BaseAnormCypherSpec with ScalaFutures {

    implicit override val patienceConfig =
      PatienceConfig(timeout = Span(2, Minutes))
  }
}
