package com.kifi.lda

import scala.io.Source
import scala.collection.mutable.ListBuffer

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

class InMemoryDocIterator(fileName: String) extends DocIterator {
  private val lines = {
    println("init in-memory doc iterator...")
    val lines = ListBuffer.empty[Array[Int]]
    var lineIter = Source.fromFile(fileName).getLines
    while(lineIter.hasNext){
      lines += lineIter.next.split(" ").map{_.toInt}
    }
    println("in-memory doc iterator initialized")
    lines
  }
  
  private var p = 0
  private var iter = lines.iterator
  
  def hasNext = iter.hasNext
  
  def next = { 
    p += 1
    iter.next
  }
  
  def gotoHead() = {
    p = 0
    iter = lines.iterator
  }
  
  def getPosition(): Int = p
}
