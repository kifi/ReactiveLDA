package com.kifi.lda

import io.Source
import java.io._
import scala.math._
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import scala.collection.mutable

case class Word2Id(map: Map[String, Int])
case class WordCount(numDocs: Int, wordCounts: Map[String, Int])
case class Idf(map: Map[String, Float])
case class FreqCounts(wordProb: Map[Int, Float], jointProb: Map[(Int, Int), Float])

class CorpusUtil {
  /**
   * inFile: txt file, each line is a document
   * outCorpus: txt file, each line is a document. Words are replaced by their ids. 
   */
  def process(infile: String, outCorpus: String, word2idFile: String, idfFile: String, minIdf: Float, numOfWords: Int): Unit = {
    val wc = WordCounts.count(infile)
    val idf = WordCounts.idf(wc)
    val word2id = WordCounts.getWord2Id(idf, minIdf, numOfWords)
    println(s"final vocabulary size: ${word2id.map.size}")
    val word2idFilter = new Word2IdFilter(word2id.map)
    word2idFilter.transfer(infile, outCorpus)
    implicit val formats = DefaultFormats
    val jstr = write(word2id.map)
    val word2idSave = new BufferedWriter(new FileWriter(new File(word2idFile)))
    word2idSave.write(jstr)
    word2idSave.close()
    
    val idfSave = new BufferedWriter(new FileWriter(new File(idfFile)))
    val idfJstr = write(word2id.map.keysIterator.map{ w => (w, idf.map(w)) }.toMap)
    idfSave.write(idfJstr)
    idfSave.close()
  }
  
  // can be used to compute Pointwise Mutual Information later. Since we are only interested
  // in top words in topics, we specify wordPairs to save memory.
  def genFreqCounts(trainingCorpus: String, wordPairs: Set[(Int, Int)]): FreqCounts = {
    val words = wordPairs.flatMap{ pair => List(pair._1, pair._2)}.toSet
    val sortedPairs = wordPairs.toArray.sortBy(_._1)
    val srcPos = mutable.Map[Int, Int]()
    var i = 0 
    val N = sortedPairs.length
    var prevSrc = -1
    while (i < N){
      val src = sortedPairs(i)._1
      if (src != prevSrc){
        srcPos(src) = i
        prevSrc = src 
      }
      i += 1
    }
    
    val iter = new OnDiskDocIterator(trainingCorpus)
    val wordCnt = mutable.Map[Int, Float]().withDefaultValue(1e-10f)
    val jointCnt = mutable.Map[(Int, Int), Float]().withDefaultValue(1e-10f)
    var n = 0
    
    while(iter.hasNext){
      val wordsInDoc = iter.next.content.toSet
      val filtered = words & wordsInDoc
      filtered.foreach { w =>
        wordCnt(w) += 1f
        if (srcPos.keySet.contains(w)) {
          var p = srcPos(w)
          var src = sortedPairs(p)._1
          while (src == w && p < N) {
            val dest = sortedPairs(p)._2
            if (filtered.contains(dest)) jointCnt((src, dest)) += 1f
            p += 1
            if (p < N) src = sortedPairs(p)._1
          }

        }
      }
      
      n += 1
      if (n % 1000 == 0) printf(s"\r${n} files processed")
    }
    wordCnt.keysIterator.foreach{ k => wordCnt(k) /= n}
    jointCnt.keysIterator.foreach{ k => jointCnt(k) /= n}
    FreqCounts(wordCnt.toMap, jointCnt.toMap)
  }
}

object WordCounts {
  def count(inName: String): WordCount = {
    val wordCnt = mutable.Map[String, Int]()
    val lines = Source.fromFile(inName).getLines
    var cnt = 0

    while(lines.hasNext){
      val ln = lines.next()
      ln.split(" ").filter(!_.isEmpty()).toSet.foreach{ w: String =>
        wordCnt(w) = wordCnt.getOrElse(w, 0) + 1
      }

      cnt += 1
      if (cnt % 1000 == 0){
        printf(s"\r${cnt} files processed")
      }
    }
    WordCount(cnt, wordCnt.toMap)
  }
  
  def idf(wc: WordCount): Idf = {
    def log2(x: Double) = log(x)/log(2.0)
    val N = wc.numDocs
    Idf(wc.wordCounts.map{ case (w, n) => (w, log2(N * 1.0 / n).toFloat)})
  }
  
  def getWord2Id(idf: Idf, minIdf: Float, numOfWords: Int): Word2Id = {
    val filtered = idf.map.filter{ case (w, x) => x > minIdf}
    val rv = filtered.toArray.sortBy(_._2).map{_._1}.take(numOfWords).zipWithIndex.toMap
    Word2Id(rv)
  }
}

class Word2IdFilter(word2id: Map[String, Int]){

  private def filter(text: String): String = {
    val tokens = text.split("[\\s]").filter(!_.isEmpty)
    tokens.flatMap{ x => word2id.get(x)}.toArray.sorted.mkString(" ")      // tokens are sorted to speed up Gibbs Sampling (by reusing multinomial distribution) 
  }

  def transfer(inName: String, outName: String): Unit = {
    val lines = Source.fromFile(inName).getLines
    val out = new BufferedWriter(new FileWriter(new File(outName)))
    var cnt = 0L
    while(lines.hasNext){
      val ln = filter(lines.next())
      if (ln.trim() != ""){
        out.write(ln)
        out.write("\n")
      }
      cnt += 1
      if (cnt % 1000 == 0){
        printf(s"\r${cnt} files processed")
      }
    }
    out.close()
  }
}