package com.kifi.lda

import akka.actor._

class MiniBatchActor(docIter: DocIterator) extends Actor {

  private def nextBatch(size: Int): MiniBatchDocs = {
    var buf = Vector[Doc]()

    while(docIter.hasNext && buf.size < size){
      buf :+= {
        val doc = docIter.next
        Doc(docIter.getPosition, doc)
      }
    }

    if (!docIter.hasNext){
      docIter.gotoHead()
      MiniBatchDocs(buf, wholeBatchEnded = true)
    }
    else MiniBatchDocs(buf, wholeBatchEnded = false)
  }

  def receive = {
    case NextMiniBatchRequest(size) => {
      printf(s"\rstart miniBatch from doc ${docIter.getPosition}")
      sender ! nextBatch(size)
    }
  }
}
