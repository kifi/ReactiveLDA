package com.kifi.lda

import scala.io.Source
import java.io._
import net.liftweb.json._
import scala.math._

class ModelReader(beta: Beta, word2id: Word2Id, idf: Idf) {
  val id2word = word2id.map.map{ case (w, id) => (id, w)}
  val voc = beta.vocSize
  val T = beta.numTopics
  
  def showTopics(topic: Int, topK: Int) = {
    val t = topic; 
    val v = (0 until voc).map{w => beta.get(t, w)}; 
    val s = v.sum; val v_n = v.map{_/s}; 
    v_n.zipWithIndex.sortBy(-1f*_._1).take(topK).map{ x => (id2word(x._2), x._1) }
  }
  
  def showWordTopic(word: String, topK: Int) = {
    word2id.map.get(word).map{ wid =>
      val v = (0 until T).map{ t => beta.get(t, wid)}; 
      val s = v.sum; 
      val v_n = v.map{_/s}; 
      v_n.zipWithIndex.sortBy(-1f*_._1).take(topK)
    }
  }
  
  def showTopicWithIdfDiscount(topic: Int, topK: Int) = {
    val prefetch = 1000
    showTopics(topic, prefetch).map{ case (w, x) => (w, x/(exp(idf.map(w))))}.sortBy(-1f*_._2).take(topK)
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
