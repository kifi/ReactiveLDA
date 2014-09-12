package com.kifi.lda

import akka.actor._

/**
 * fetch next batch of documents for the workers. 
 */
class MiniBatchActor(docIter: DocIterator, batchSize: Int) extends Actor {
  
  val buf = new Array[Doc](batchSize)

  private def nextBatch(): MiniBatchDocs = {
    var p = 0

    while(docIter.hasNext && p < batchSize){
      buf(p) = docIter.next
      p += 1
    }

    if (!docIter.hasNext){
      docIter.gotoHead()
      MiniBatchDocs(buf.take(p), wholeBatchEnded = true)		// entire corpus scanned once. 
    }
    else MiniBatchDocs(buf, wholeBatchEnded = false)
  }

  def receive = {
    case NextMiniBatchRequest => {
      printf(s"\rstart miniBatch from doc ${docIter.getPosition}")
      sender ! nextBatch
    }
  }
}
