package com.kifi.lda

import akka.actor._
import scala.io.Source
import akka.routing.RoundRobinRouter
import scala.collection.mutable
import scala.math._
import scala.util.Random


case class Doc(index: Int, content: Array[Int])
case class WordTopicAssigns(value: Array[(Int, Int)])  // (wordId, topicId)


sealed trait LDAMessage
case object StartTraining extends LDAMessage
case class NextMiniBatchRequest(size: Int) extends LDAMessage
case class MiniBatchDocs(docs: Seq[Doc], wholeBatchEnded: Boolean) extends LDAMessage
case class UniformSampling(doc: Doc, numTopics: Int) extends LDAMessage
case class Sampling(doc: Doc, theta: Theta, beta: Beta) extends LDAMessage
case class SamplingResult(docIndex: Int, theta: Theta, wordTopicAssign: WordTopicAssigns) extends LDAMessage

class MiniBatchLineReader(docIter: DocIterator) extends Actor {

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


class BetaActor(batchReader: ActorRef, LDAConfig: LDAConfig) extends Actor {

  val workerRouter = context.actorOf(Props(classOf[DocSamplingActor], LDAConfig.numTopics).withRouter(RoundRobinRouter(LDAConfig.nworker)), name = "workerRouter")
  val thetas = mutable.Map.empty[Int, Array[Float]]
  val beta: Beta = Beta(new Array[Float](LDAConfig.numTopics * LDAConfig.vocSize), LDAConfig.numTopics, LDAConfig.vocSize)
  val wordTopicCounts: WordTopicCounts = WordTopicCounts(new Array[Int](LDAConfig.numTopics * LDAConfig.vocSize), LDAConfig.numTopics, LDAConfig.vocSize)
  val miniBatchSize = LDAConfig.miniBatchSize
  val eta = 0.1f
  val wordCounts = new Array[Int](LDAConfig.vocSize)
  var updateWordCount = true		// will be false once we finish one round

  val tracker = BatchProgressTracker(LDAConfig.iterations)


  private def dispatchJobs(docs: Seq[Doc]) = {
    if (tracker.initalUniformSampling) docs.foreach{ doc => workerRouter ! UniformSampling(doc, LDAConfig.numTopics) }
    else docs.foreach{ doc => workerRouter ! Sampling(doc, Theta(thetas(doc.index)), beta) }
  }
      
  private def updateBeta(): Unit = {
    println(self.path.name + ": updating beta")
    
    if (updateWordCount == true){
      println(s"word count finished: ${wordCounts.take(10).mkString(", ")}")
      updateWordCount = false
    }
    
    (0 until LDAConfig.numTopics).par.foreach { t =>
      val counts = if (LDAConfig.discount){
        
       val v = (wordTopicCounts.getRow(t) zip wordCounts).map{ case (a, b) => (a + eta)/b}
       val s = v.min.toDouble
       v.map{ x => (x/s min Float.MaxValue).toFloat}
       
      } else {
        wordTopicCounts.getRow(t).map{_ + eta}
      }
      println(s"sampling dirichlet with ${counts.take(10).mkString(" ")}")
      val b = Sampler.dirichlet(counts).map{_.toFloat}
      println(s"sampled beta for topic $t: ${b.take(10).mkString(" ")}")
      beta.setRow(t, b)
    }

    println(self.path.name + ": beta updated")
    println(s"batch counter = ${tracker.getBatchCounter}")
  }
  
  private def saveModel(): Unit = {
    println("saving model...")
    Beta.toFile(beta, LDAConfig.saveBetaPath)
    WordTopicCounts.toFile(wordTopicCounts, LDAConfig.saveCountsPath)
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

class DocSamplingActor(numTopics: Int) extends Actor {

  private val alpha = 0.1f
  private val topicCounts = Array.fill(numTopics)(alpha)
  private val multinomial = new Array[Double](numTopics)
  
  private def resetTopicCounts() {
    var i = 0
    while (i < numTopics) { topicCounts(i) = alpha; i+= 1 }
  }

  private def sampleTheta(zs: Seq[Int], numTopics: Int): Array[Float] = {
    var i = 0
    while (i < zs.size){ topicCounts(zs(i)) += 1; i += 1 }
    
    val sample = Sampler.dirichlet(topicCounts).map{_.toFloat}
    resetTopicCounts()
    sample
  }

  private def uniformSampling(doc: Doc, numTopics: Int): SamplingResult = {
    val z = (0 until doc.content.size).map{ x => Random.nextInt(numTopics)}
    SamplingResult(doc.index, Theta(sampleTheta(z, numTopics)), WordTopicAssigns((doc.content zip z)))
  }

  private def sampling(doc: Doc, theta: Theta, beta: Beta): SamplingResult = {

    val zs = doc.content.map{ w =>
      var i = 0
      while (i < numTopics){ multinomial(i) = theta.value(i).toDouble * beta.get(i, w); i += 1 }
      val s = multinomial.sum
      i = 0
      while (i < numTopics){ multinomial(i) /= s; i+=1 }
      val z = Sampler.multiNomial(multinomial)
      z
    }

    SamplingResult(doc.index, Theta(sampleTheta(zs, numTopics)), WordTopicAssigns((doc.content zip zs)))
  }

  def receive = {
    case UniformSampling(doc, numTopics) => sender ! uniformSampling(doc, numTopics)

    case Sampling(doc, theta, beta) => sender ! sampling(doc, theta, beta)
  }
}


object LDA {

  def main(args: Array[String]) = {
    val system = ActorSystem("LDASystem")
    val config = LDAConfig.parseConfig(args)
    
    val docIter = if (config.inMem) new InMemoryDocIterator(config.trainFile) else new DocIteratorImpl(config.trainFile)
    val readerActor = system.actorOf(Props(new MiniBatchLineReader(docIter)), "readerActor")
    val betaActor = system.actorOf(Props(new BetaActor(readerActor, config)), "betaActor")
    
    betaActor ! StartTraining
  }

}
