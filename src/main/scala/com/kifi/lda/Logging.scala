package com.kifi.lda

case class LoggerLevel(detailLevel: Int) extends Ordered[LoggerLevel]{
  def compare(that: LoggerLevel): Int = this.detailLevel.compare(that.detailLevel)
}

object LoggerLevel {
  val OFF = LoggerLevel(0)
  val INFO  = LoggerLevel(1)
  val DEBUG = LoggerLevel(2) 
}

class Logger() {
  private var _level: LoggerLevel = LoggerLevel.INFO
  def setLevel(level: LoggerLevel): Unit = { _level = level }
  def info(msg: String): Unit = if (_level >= LoggerLevel.INFO) println(msg)
  def debug(msg: String): Unit = if (_level >= LoggerLevel.DEBUG) println(msg) 
}

trait Logging {
  val log = new Logger()
}
