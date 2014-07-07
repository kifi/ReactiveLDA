package com.kifi.lda

import akka.actor._
import scala.io.Source
import akka.routing.RoundRobinRouter
import scala.collection.mutable
import scala.math._
import scala.util.Random


import java.io._

case class Doc(index: Int, content: Array[Int])

// beta: topic-word distribution. T x V matrix. Each row is a topic-word distribution.
case class Beta(value: Array[Float], numTopics: Int, vocSize: Int){
  def get(topic: Int, word: Int): Float = value(topic * vocSize + word)
  def set(topic: Int, word: Int, x: Float): Unit = value(topic * vocSize + word) = x
  def setRow(topic: Int, row: Array[Float]): Unit = {
    (0 until vocSize).map{ i => set(topic, i, row(i))}
  }
}

object Beta {
  def toBytes(beta: Beta): Array[Byte] = {
    val numBytes = 4 * 2 + 4 * beta.value.size
    var i = 0
    var N = beta.value.size

    val bs = new ByteArrayOutputStream(numBytes)
    val os = new DataOutputStream(bs)
    os.writeInt(beta.numTopics)
    os.writeInt(beta.vocSize)
    beta.value.foreach{os.writeFloat(_)}
    os.close()
    val rv = bs.toByteArray()
    bs.close()
    rv
  }

  def toFile(beta: Beta, path: String) = {
    val os = new FileOutputStream(path)
    val bytes = toBytes(beta)
    os.write(bytes)
    os.close()
  }

  def fromFile(path: String) = {
    val is = new DataInputStream(new FileInputStream(new File(path)))
    val numTopics = is.readInt()
    val vocSize = is.readInt()
    val value = new Array[Float](numTopics * vocSize)
    val N = value.size
    var i = 0
    while ( i < N){
      value(i) = is.readFloat()
      i += 1
    }
    Beta(value, numTopics, vocSize)
  }
}

case class WordTopicCounts(value: Array[Int], numTopics: Int, vocSize: Int){
  def get(topic: Int, word: Int): Int = value(topic * vocSize + word)
  def incre(topic: Int, word: Int): Unit = { value(topic * vocSize + word) = value(topic * vocSize + word) + 1 }
  def getRow(topicId: Int): Array[Int] = value.slice( topicId * vocSize, (topicId + 1) * vocSize)
  def clearAll(){
    var i = 0
    while (i < value.size) {value(i) = 0; i += 1 }
  }
}

// theta: doc-topic distribution
case class Theta(value: Array[Float])
case class WordTopicAssigns(value: Array[(Int, Int)])  // (wordId, topicId)

trait DocIterator {
  def hasNext: Boolean
  def next: Array[Int]
  def gotoHead(): Unit
  def getPosition(): Int
}

class DocIteratorImpl(fileName: String) extends DocIterator {
  private var lineIter = Source.fromFile(fileName).getLines
  private var p = 0
  def hasNext = lineIter.hasNext
  def next = {
    p += 1
    lineIter.next.split(" ").map{_.toInt}
  }
  def gotoHead() = {
    lineIter = Source.fromFile(fileName).getLines
    p = 0
  }
  def getPosition(): Int = p
}


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

case class TopicConfig(numTopics: Int, vocSize: Int, iterations: Int, discount: Boolean, miniBatchSize: Int, saveModelPath: String)

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
    println(s"one whole batch finished!!! batchCounter = ${batchCounter}")
    batchCounter += 1
  }
  def batchFinished = batchCounter == totalIters
}


class BetaActor(batchReader: ActorRef, numOfWorkers: Int, topicConfig: TopicConfig) extends Actor {

  val workerRouter = context.actorOf(Props[DocSamplingActors].withRouter(RoundRobinRouter(numOfWorkers)), name = "workerRouter")
  val thetas = mutable.Map.empty[Int, Array[Float]]
  val beta: Beta = Beta(new Array[Float](topicConfig.numTopics * topicConfig.vocSize), topicConfig.numTopics, topicConfig.vocSize)
  val wordTopicCounts: WordTopicCounts = WordTopicCounts(new Array[Int](topicConfig.numTopics * topicConfig.vocSize), topicConfig.numTopics, topicConfig.vocSize)
  val miniBatchSize = topicConfig.miniBatchSize
  val eta = 0.1f
  val wordCounts = new Array[Int](topicConfig.vocSize)
  var updateWordCount = true		// will be false once we finish one round

  val tracker = BatchProgressTracker(topicConfig.iterations)


  private def dispatchJobs(docs: Seq[Doc]) = {
    if (tracker.initalUniformSampling) docs.foreach{ doc => workerRouter ! UniformSampling(doc, topicConfig.numTopics) }
    else docs.foreach{ doc => workerRouter ! Sampling(doc, Theta(thetas(doc.index)), beta) }
  }
      
  private def updateBeta(): Unit = {
    println(self.path.name + ": updating beta")
    
    if (updateWordCount == true){
      println(s"word count finished: ${wordCounts.take(10).mkString(", ")}")
      updateWordCount = false
    }
    
    (0 until topicConfig.numTopics).par.foreach { t =>
      val counts = if (topicConfig.discount){
        
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

    // clear wordTopicCounts
    //println("clear word topic counts")
    wordTopicCounts.clearAll()
  }
  private def saveModel(): Unit = {
    println("saving model...")
    Beta.toFile(beta, topicConfig.saveModelPath)
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

class DocSamplingActors extends Actor {

  private val alpha = 0.1f

  private def sampleTheta(zs: Seq[Int], numTopics: Int): Array[Float] = {
    val topics = new Array[Int](numTopics)
    var i = 0
    var t = 0
    while (i < zs.size){
      t = zs(i)
      topics(t) += 1
      i += 1
    }
    Sampler.dirichlet(topics.map{ _ + alpha}).map{_.toFloat}
  }

  private def uniformSampling(doc: Doc, numTopics: Int): SamplingResult = {
    val z = (0 until doc.content.size).map{ x => Random.nextInt(numTopics)}
    SamplingResult(doc.index, Theta(sampleTheta(z, numTopics)), WordTopicAssigns((doc.content zip z)))
  }

  private def sampling(doc: Doc, theta: Theta, beta: Beta): SamplingResult = {
    val numTopics = theta.value.size

    val zs = doc.content.map{ w =>
      val multi = (0 until numTopics).map{ i => theta.value(i).toDouble * beta.get(i, w)}
      val s = multi.sum
      val z = Sampler.multiNomial(multi.map{ x => x/s})
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

  val usage = """
    Usage: java -jar LDA.jar -nw nWorker -t topicSize -voc vocSize -iter iters -disc discountWordFreq -b miniBatchSize -in trainFile -out modelFile
    """

  private def consume(map: Map[String, String], list: List[String]): Map[String, String] = {
    list match {
      case Nil => map
      case "-nw" :: value :: tail => consume(map ++ Map("nworker" -> value), tail)
      case "-t" :: value :: tail => consume(map ++ Map("topicSize" -> value), tail)
      case "-voc" :: value :: tail => consume(map ++ Map("vocSize" -> value), tail)
      case "-iter" :: value :: tail => consume(map ++ Map("iters" -> value), tail)
      case "-b" :: value :: tail => consume(map ++ Map("miniBatchSize" -> value), tail)
      case "-in" :: value :: tail => consume(map ++ Map("trainFile" -> value), tail)
      case "-out" :: value :: tail => consume(map ++ Map("modelFile" -> value), tail)
      case "-disc":: value :: tail => consume(map ++ Map("discount" -> value), tail)
      case option :: tail => println("unknown option " + option); exit(1)
    }
  }

  private def parseArgs(args: Array[String]): Map[String, String] = {
    val arglist = args.toList
    consume(Map(), arglist)
  }

  def main(args: Array[String]) = {

    println("hello lda")

    if (args.isEmpty) {
      println(usage)
      exit(1)
    }

    val map = consume(Map(), args.toList)
    println(map)

    if (map.keySet != Set("nworker", "topicSize", "vocSize", "trainFile", "modelFile", "iters", "miniBatchSize", "discount")) {
      println("not enough arguments")
      exit(1)
    }

    val system = ActorSystem("LDASystem")

    val config = TopicConfig(
      numTopics = map("topicSize").toInt,
      vocSize = map("vocSize").toInt,
      iterations = map("iters").toInt,
      discount = map("discount").toBoolean,
      miniBatchSize = map("miniBatchSize").toInt,
      saveModelPath = map("modelFile"))

    val docIter = new DocIteratorImpl(map("trainFile"))

    val readerActor = system.actorOf(Props(new MiniBatchLineReader(docIter)), "readerActor")
    val betaActor = system.actorOf(Props(new BetaActor(readerActor, map("nworker").toInt, config)), "betaActor")

    betaActor ! StartTraining
  }

}
