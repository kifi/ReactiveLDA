package com.kifi.lda

import akka.actor._
import scala.io.Source
import akka.routing.RoundRobinRouter
import scala.collection.mutable
import scala.math._
import scala.util.Random
import org.apache.commons.math3.random.Well19937c

class BetaActor(batchReader: ActorRef, config: LDAConfig) extends Actor {

  val workerRouter = context.actorOf(Props(classOf[DocSamplingActor], config.numTopics).withRouter(RoundRobinRouter(config.nworker)), name = "workerRouter")
  val thetas = mutable.Map.empty[Int, Array[Float]]
  val beta: Beta = Beta(new Array[Float](config.numTopics * config.vocSize), config.numTopics, config.vocSize)
  val wordTopicCounts: WordTopicCounts = WordTopicCounts(new Array[Int](config.numTopics * config.vocSize), config.numTopics, config.vocSize)
  val miniBatchSize = config.miniBatchSize
  val eta = 0.1f
  val wordCounts = new Array[Int](config.vocSize)
  var updateWordCount = true		// will be false once we finish one round
  val tracker = BatchProgressTracker(config.iterations)
  val rng = new Well19937c()
  private val dirSampler = new FastDirichletSampler(rng)

  private def dispatchJobs(docs: Seq[Doc]) = {
    if (tracker.initalUniformSampling) docs.foreach{ doc => workerRouter ! UniformSampling(doc) }
    else docs.foreach{ doc => workerRouter ! Sampling(doc, Theta(thetas(doc.index)), beta) }
  }
      
  private def updateBeta(): Unit = {
    println(self.path.name + ": updating beta")
    
    if (updateWordCount == true){
      println(s"word count finished: ${wordCounts.take(10).mkString(", ")}")
      updateWordCount = false
    }
    
    (0 until config.numTopics).foreach { t =>
      val counts = if (config.discount){
        
       val v = (wordTopicCounts.getRow(t) zip wordCounts).map{ case (a, b) => (a + eta)/b}
       val s = v.min.toDouble
       v.map{ x => (x/s min Float.MaxValue).toFloat}
       
      } else {
        wordTopicCounts.getRow(t).map{_ + eta}
      }
      println(s"sampling dirichlet with ${counts.take(10).mkString(" ")}")
      val b = dirSampler.sample(counts)
      println(s"sampled beta for topic $t: ${b.take(10).mkString(" ")}")
      beta.setRow(t, b)
    }

    println(self.path.name + ": beta updated")
    println(s"batch counter = ${tracker.getBatchCounter}")
  }
  
  private def saveModel(): Unit = {
    println("saving model...")
    Beta.toFile(beta, config.saveBetaPath)
    WordTopicCounts.toFile(wordTopicCounts, config.saveCountsPath)
  }

  private def handleSamplingResult(result: SamplingResult){
    tracker.increMiniBatchCounter()
    updateWordTopicCounts(result)
    thetas(result.docIndex) = result.theta.value

    (tracker.miniBatchFinished, tracker.isLastMiniBatch) match {
      case (false, _) =>
      case (true, false) => batchReader ! NextMiniBatchRequest(miniBatchSize)
      case (true, true) => {
        tracker.increBatchCounter()
        updateBeta()
        if (tracker.batchFinished) {
          saveModel()
          context.system.shutdown()
        }  else {
          wordTopicCounts.clearAll()
          batchReader ! NextMiniBatchRequest(miniBatchSize)
        }
      }
    }
  }

  private def updateWordTopicCounts(result: SamplingResult){
    result.wordTopicAssign.value.map{ case (wordId, topicId) =>
      wordTopicCounts.incre(topicId, wordId)
      if (updateWordCount) wordCounts(wordId) = wordCounts(wordId) + 1
    }
  }

  def receive = {
    case StartTraining => batchReader ! NextMiniBatchRequest(miniBatchSize)
    case MiniBatchDocs(docs, wholeBatchEnded) => {
      if (wholeBatchEnded) tracker.isLastMiniBatch = true
      else tracker.isLastMiniBatch = false

      tracker.startTrackingMiniBatch(docs.size)
      dispatchJobs(docs)
    }
    case result: SamplingResult => handleSamplingResult(result)
  }
}

case class BatchProgressTracker(val totalIters: Int){
  private var batchCounter = 0
  private var currMiniBatchSize: Int = 0
  private var miniBatchCounter: Int = 0
  private var _isLastMiniBatch: Boolean = false
  def isLastMiniBatch = _isLastMiniBatch
  def isLastMiniBatch_=(isLast: Boolean) = _isLastMiniBatch = isLast

  def initalUniformSampling: Boolean = batchCounter < 1

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
