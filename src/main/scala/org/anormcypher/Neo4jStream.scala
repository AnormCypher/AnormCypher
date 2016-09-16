package org.anormcypher

import akka.stream._, scaladsl._, stage._
import akka.util.ByteString
import com.sorrentocorp.akka.stream._
import play.api.libs.json._
import scala.collection.mutable
>
class CypherResultRowFraming extends GraphStage[FlowShape[Neo4jRespToken, CypherResultRow]] {
  override protected def initialAttributes: Attributes = Attributes.name("CypherResultRowFraming.scanner")

  val in = Inlet[Neo4jRespToken]("CypherResultRowFraming.in")
  val out = Outlet[CypherResultRow]("CypherResultRowFraming.out")
  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    setHandlers(in, out, this)
    import Neo4jREST._

    var metadata: MetaData = null
    var noresult = false
    val rowbuf: mutable.Queue[Neo4jResultSet] = mutable.Queue()

    def nextRow: CypherResultRow = new CypherResultRow(metadata, rowbuf.dequeue.row)

    override def onPush(): Unit = {
      val curr = grab(in)
      curr match {
        case EmptyResult =>
          noresult = true
          // continue on to see if we get any errors

        case ResultColumn(meta) =>
          if (metadata != null)
            throw new IllegalStateException(s"Got another metadata ${meta.utf8String}, received ${metadata}")
          else
            metadata = MetaData(
              for (colname <- Json.parse(meta.utf8String).as[List[String]])
              yield (MetaDataItem(colname, false, "String")))

        case DataRow(row) =>
          rowbuf.enqueue(Json.parse(row.utf8String).as[Neo4jResultSet])

        case ErrorObj(errors) =>
          val err = Json.parse(errors.utf8String).as[Seq[JsObject]]
          if (!err.isEmpty)
            throw new RuntimeException(s"""Neo4j server error: message is ${err(0).value("message")}""")
      }

      tryPopBuffer()
    }

    override def onPull(): Unit = {
      tryPopBuffer()
    }

    override def onUpstreamFinish(): Unit = {
      if (noresult || rowbuf.isEmpty)
        completeStage()
      else
        emit(out, nextRow)
    }

    def tryPopBuffer(): Unit = {
      if (!rowbuf.isEmpty)
        emit(out, nextRow)
      else if (isClosed(in))
        completeStage()
      else
        pull(in)
    }
  }
}

object Neo4jStream {
  def parse[T](source: Source[ByteString, T])(implicit mat: Materializer): Source[CypherResultRow, T] =
    source.via(new Neo4jRespFraming).via(new CypherResultRowFraming)
}
