package org.anormcypher

import concurrent.{Await, Future}
import concurrent.duration._
import org.scalatest.{BeforeAndAfterEach, FlatSpec}
import org.scalatest.matchers.ShouldMatchers

trait CommonTest extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

  val timeout = 10.seconds

  def waitIO[A](block: ⇒ Future[A]) = Await.result(block, timeout)

}
