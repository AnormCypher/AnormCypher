package org.anormcypher

import org.scalatest._

import play.api.libs.ws._, ning._

trait BaseAnormCypherSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val wsclient = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new DefaultWSClientConfig()).build)
  val neo4jrest = Neo4jREST(scala.util.Properties.envOrElse("NEO4J_SERVER", "localhost"))(wsclient)

  override def afterAll = {
    wsclient.close()
  }
}
