package com.kifi.lda

import io.Source
import java.io._
import scala.math._
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import scala.Array.canBuildFrom
import scala.Option.option2Iterable


case class Word2Id(map: Map[String, Int])
case class WordCount(numDocs: Int, wordCounts: Map[String, Int])
case class Idf(map: Map[String, Float])

class CorpusUtil {
  def process(infile: String, outCorpus: String, word2idFile: String, minIdf: Float = 3f, maxIdf: Float = 15f): Unit = {
    val wc = WordCounts.count(infile)
    val idf = WordCounts.idf(wc)
    val word2id = WordCounts.getWord2Id(idf, minIdf, maxIdf)
    val word2idFilter = new Word2IdFilter(word2id.map)
    word2idFilter.transfer(infile, outCorpus)
    implicit val formats = DefaultFormats
    val jstr = write(word2id.map)
    val word2idSave = new BufferedWriter(new FileWriter(new File(word2idFile)))
    word2idSave.write(jstr)
    word2idSave.close()
  }
}

object WordCounts {
  def count(inName: String): WordCount = {
    val wordCnt = scala.collection.mutable.Map[String, Int]()
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
    val N = wc.numDocs
    Idf(wc.wordCounts.map{ case (w, n) => (w, log(N/n).toFloat)})
  }
  
  def getWord2Id(idf: Idf, minIdf: Float, maxIdf: Float): Word2Id = {
    val filtered = idf.map.filter{ case (w, x) => x > minIdf && x < maxIdf}
    val rv = filtered.toArray.sortBy(_._2).map{_._1}.zipWithIndex.toMap
    Word2Id(rv)
  }
}

class Word2IdFilter(word2id: Map[String, Int]){

  private def filter(text: String): String = {
    val tokens = text.split("[\\s]").filter(!_.isEmpty)
    tokens.flatMap{ x => word2id.get(x)}.mkString(" ")
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