package com.kifi.lda

import java.io._

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

object WordTopicCounts {
  def toBytes(wtc: WordTopicCounts): Array[Byte] = {
    val numBytes = 4 * 2 + 4 * wtc.value.size
    var i = 0
    var N = wtc.value.size

    val bs = new ByteArrayOutputStream(numBytes)
    val os = new DataOutputStream(bs)
    os.writeInt(wtc.numTopics)
    os.writeInt(wtc.vocSize)
    wtc.value.foreach{os.writeInt(_)}
    os.close()
    val rv = bs.toByteArray()
    bs.close()
    rv
  }

  def toFile(wtc: WordTopicCounts, path: String) = {
    val os = new FileOutputStream(path)
    val bytes = toBytes(wtc)
    os.write(bytes)
    os.close()
  }

  def fromFile(path: String) = {
    val is = new DataInputStream(new FileInputStream(new File(path)))
    val numTopics = is.readInt()
    val vocSize = is.readInt()
    val value = new Array[Int](numTopics * vocSize)
    val N = value.size
    var i = 0
    while ( i < N){
      value(i) = is.readInt()
      i += 1
    }
    WordTopicCounts(value, numTopics, vocSize)
  }
}

// theta: doc-topic distribution
case class Theta(value: Array[Float])