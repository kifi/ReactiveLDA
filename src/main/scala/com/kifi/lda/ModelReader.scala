package com.kifi.lda

import java.io._
import net.liftweb.json._
import scala.collection.mutable
import scala.math._
import scala.io.Source

// some utility functions to examine the model
class ModelReader(beta: Beta, word2id: Word2Id) {
  val id2word = word2id.map.map{ case (w, id) => (id, w)}
  val voc = beta.vocSize
  val T = beta.numTopics
  val wordvecs: Map[String, Array[Float]] = {
    val words = word2id.map.keySet
    val wordvecs = words.map { w: String =>
	  val wid = word2id.map(w)
	  val v = (0 until T).map { t => beta.get(t, wid) };
	  val s = v.sum;
	  w -> v.map { _ / s }.toArray;
    }
    wordvecs.toMap
  }
  var freqCntsForPMI: Option[FreqCounts] = None
  
  def showTopics(topic: Int, topK: Int) = {
    getTopKWordScoreAndIds(topic, topK).map{ case (score, id) => (id2word(id), score) }
  }
  
  private def getTopKWordScoreAndIds(topic: Int, topK: Int): Seq[(Float, Int)] = {
    val t = topic; 
    val v = (0 until voc).map{w => beta.get(t, w)}; 
    val s = v.sum; val v_n = v.map{_/s}; 
    v_n.zipWithIndex.sortBy(-1f*_._1).take(topK)
  }
  
  def showWordTopic(word: String, topK: Int) = {
    wordvecs.get(word).map{ v => 
      v.zipWithIndex.sortBy(-1f*_._1).take(topK)
    }
  }
  
  def getAllTopics(topK: Int = 100): String = {
    (0 until T).map{ i => val t = showTopics(i, topK).map{_._1}; i + " " + t.mkString(", ")}.mkString("\n\n")
  }
  
  private def tokenize(txt: String) = {
    txt.toLowerCase.split("[\\s,.:;\"\'()]").filter(!_.isEmpty)
  }
  
  private def setFreqCntsForPMI(corpus: String, topKWords: Int): Unit = {
    val util = new CorpusUtil()
    val pairs = Array.tabulate(T){ t =>
      val ids = getTopKWordScoreAndIds(t, topKWords).map{_._2}
      var s = 0.0
      for{
        i <- ids
        j <- ids if i < j
      } yield (i, j)
    }.flatten.toSet
    println(s"scanning corpus for ${pairs.size} pairs")
    val freq = util.genFreqCounts(corpus, pairs)
    freqCntsForPMI = Some(freq)
  }
  
  
  def evaluateModelPMI(corpus: String, topKwords: Int = 50): Array[Double] = {
    setFreqCntsForPMI(corpus, topKwords)
    Array.tabulate(T){ t =>
      val ids = getTopKWordScoreAndIds(t, topKwords).map{_._2}
      var s = 0.0
      for(i <- ids){
        for(j <-ids){
          if ( i < j ) s += computePMI(i, j)
        }
      }
      s 
    }
  } 
  
  private def computePMI(word1: Int, word2: Int): Double = {
    assume(word1 != word2, "PMI should apply to different words")
    val freq = freqCntsForPMI.get
    val y = freq.wordProb(word1) * freq.wordProb(word2)
    val x = if (word1 < word2) freq.jointProb.getOrElse((word1, word2), 1e-10.toFloat) else freq.jointProb.getOrElse((word2, word1), 1e-10.toFloat) 
    log(x/y.toDouble)
  }
  
  def classify(txt: String, topK: Int): Seq[(Float, Int)] = {
    val tokens = tokenize(txt)
    val topic = new Array[Float](T)
    tokens.flatMap{ w => wordvecs.get(w)}.foreach{ arr => (0 until T).foreach{ i => topic(i) = topic(i) + arr(i)}}
    val s = topic.sum
    val topic_n = topic.map{ t => t/s}
    topic_n.zipWithIndex.sortBy(-1f*_._1).take(topK)
  }
  
  def EM_inference(txt: String, topK: Int, maxIter: Int = 50): Seq[(Float, Int)] = {
    
    def hasConverged(a: Array[Float], b: Array[Float]) = {
      (a zip b).map{ case (x, y) => abs(x - y)}.max < 1e-2
    }
    
    val tokens = tokenize(txt).flatMap{ w => word2id.map.get(w)}
    val prior = Array.fill(T)(1.0f/T)
    val posterior = new Array[Float](T)
    val rangeT = 0 until T
    var converged = false
    var n = 0
    while(!converged && n < maxIter){
      rangeT.foreach{ t => posterior(t) = 0f}
      tokens.foreach{ id =>
        val z = rangeT.map{ t => prior(t) * beta.get(t, id) }
        val s = z.sum
        rangeT.foreach{ t => posterior(t) += z(t)/s}
      }
      val s = posterior.sum
      rangeT.foreach{t => posterior(t) /= s}
      converged = hasConverged(prior, posterior)
      rangeT.foreach{t => prior(t) = posterior(t)}
      n += 1
    }
    println(s"iterated ${n} steps")
    posterior.zipWithIndex.sortBy(-1f*_._1).take(topK)
  }
}

object ModelReader {
  def parseBeta(file: String) = {
    Beta.fromFile(file)
  }
  
  def parseWord2id(file: String) = {
    implicit val formats = DefaultFormats
    val jstr = Source.fromFile(file).mkString
    val map = parse(jstr).extract[Map[String, Int]]
    Word2Id(map)
  }
  
  def parseIdf(file: String) = {
    implicit val formats = DefaultFormats
    val jstr = Source.fromFile(file).mkString
    val map = parse(jstr).extract[Map[String, Float]]
    Idf(map)
  }
}
