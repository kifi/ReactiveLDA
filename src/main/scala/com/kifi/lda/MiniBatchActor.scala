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


case class BatchProgressTracker(val totalIters: Int){
  private var batchCounter = 0
  private var currMiniBatchSize: Int = 0
  private var miniBatchCounter: Int = 0
  private var _isLastMiniBatch: Boolean = false
  
  def isLastMiniBatch = _isLastMiniBatch
  
  def isLastMiniBatch_=(isLast: Boolean) = _isLastMiniBatch = isLast

  def initialUniformSampling: Boolean = batchCounter < 1

  def startTrackingMiniBatch(miniBatchSize: Int) = {
    currMiniBatchSize = miniBatchSize
    miniBatchCounter = 0
  }
  
  def increMiniBatchCounter() = miniBatchCounter += 1
  
  def miniBatchFinished = miniBatchCounter == currMiniBatchSize

  def increBatchCounter() = {
    println(s"\none whole batch finished!!!")
    batchCounter += 1
  }
  
  def getBatchCounter = batchCounter
  
  def batchFinished = batchCounter == totalIters
}
