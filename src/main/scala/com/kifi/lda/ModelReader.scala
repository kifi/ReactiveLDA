package com.kifi.lda

import java.io._
import net.liftweb.json._
import scala.collection.mutable
import scala.math._
import scala.io.Source

// some utility functions to examine the model
class ModelReader(beta: Beta, word2id: Word2Id, idf: Idf) {
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
  
  def showTopics(topic: Int, topK: Int) = {
    val t = topic; 
    val v = (0 until voc).map{w => beta.get(t, w)}; 
    val s = v.sum; val v_n = v.map{_/s}; 
    v_n.zipWithIndex.sortBy(-1f*_._1).take(topK).map{ x => (id2word(x._2), x._1) }
  }
  
  def showWordTopic(word: String, topK: Int) = {
    wordvecs.get(word).map{ v => 
      v.zipWithIndex.sortBy(-1f*_._1).take(topK)
    }
  }
  
  def showTopicWithIdfDiscount(topic: Int, topK: Int, prefetch: Int = 1000) = {
    showTopics(topic, prefetch).map{ case (w, x) => (w, x/(exp(idf.map(w))))}.sortBy(-1f*_._2).take(topK)
  }
  
  def getAllTopics(): String = {
    (0 until T).map{ i => val t = showTopicWithIdfDiscount(i, 100).map{_._1}; i + " " + t.mkString(", ")}.mkString("\n\n")
  }
  
  // (num_words_labeled_as_topic, topic). This is helpful in identifying big 'trivial' topics
  def topicSize(): Array[(Int, Int)] = {
    val count = new Array[Int](T)
    wordvecs.foreach{ case (_, v) => val idx = v.zipWithIndex.sortBy(-1f*_._1).head._2; count(idx) = count(idx) + 1}
    count.zipWithIndex.sortBy(-1f * _._1)
  } 
  
  def topicRelation() = {
    val m = mutable.Map.empty[(Int, Int), Float]
    wordvecs.keySet.foreach{ w => 
      val Array((s1, idx1), (s2, idx2)) = showWordTopic(w, 5).get.take(2) 
      val r = s2/s1
      val k = if (idx1 < idx2) (idx1, idx2) else (idx2, idx1)
      m(k) = m(k) + r 
    }
    m.toMap
  }
  
  def classify(txt: String, topK: Int): Seq[(Float, Int)] = {
    val tokens = txt.toLowerCase.split("[\\s,.:;\"\'()]").filter(!_.isEmpty)
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
    
    val tokens = txt.toLowerCase.split("[\\s,.:;\"\'()]").filter(!_.isEmpty).flatMap{ w => word2id.map.get(w)}
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
