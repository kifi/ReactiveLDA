package com.kifi.lda

import java.io.File

/**
 * Configs:
 * - nworker: num of DocSamplingActors. This is the main factor of parallel speed up.
 * - numTopic: num of topics
 * - vocSize: vocabulary size
 * - iterations: how many iterations on the corpus
 * - inMem: If true, load entire corpus into an in-memory iterator. Otherwise, corpus stays on disk.
 * - miniBatchSize: A whole batch means a Gibbs sampling for the entire corpus. Since loading entire corpus into memory may not be feasible, miniBatchSize
 * controls how many documents to be loaded into memory. Bigger values require more memory consumption. Small values may have an impact on paralle speed up.
 * - eta: smooth factor for computing beta (topic-word distribution). default = 0.1
 * - alpha: smooth factor for computing theta (document-topic distribution). default = 0.1
 * - burnIn: burn in steps.
 * - skip: after burned in, gather one sample point (of beta) every skip size. 
 * - trainFile: path to training file.
 * - saveBetaPath: path to saved Beta file.
 */

case class LDAConfig(
  nworker: Int,
  numTopics: Int, 
  vocSize: Int, 
  iterations: Int, 
  inMem: Boolean,
  miniBatchSize: Int,
  eta: Float,
  alpha: Float,
  burnIn: Int,
  skip: Int,
  trainFile: String,
  saveBetaPath: String,
  loglevel: LoggerLevel
)

object LDAConfig {
  
  val usage = """
    Usage: java -jar LDA.jar -nw nworker -t numTopics -voc vocSize -iter iterations [-verbose verbose] 
    [-inMem inMemoryCorpus] -b miniBatchSize [-eta eta -alpha alpha -burnIn burnIn -skip skipSize] 
    -in trainFile -betaFile betaFilePath 
    """
    
  val requiredArgs = Set("nworker", "numTopics", "vocSize", "iterations", "miniBatchSize", "trainFile", "betaFile")

  private def consume(map: Map[String, String], list: List[String]): Map[String, String] = {
    list match {
      case Nil => map
      case "-nw" :: value :: tail => consume(map ++ Map("nworker" -> value), tail)
      case "-t" :: value :: tail => consume(map ++ Map("numTopics" -> value), tail)
      case "-voc" :: value :: tail => consume(map ++ Map("vocSize" -> value), tail)
      case "-iter" :: value :: tail => consume(map ++ Map("iterations" -> value), tail)
      case "-verbose" :: value :: tail => consume(map ++ Map("verbose" -> value), tail)
      case "-inMem":: value :: tail => consume(map ++ Map("inMemoryCorpus" -> value), tail)
      case "-b" :: value :: tail => consume(map ++ Map("miniBatchSize" -> value), tail)
      case "-eta" :: value :: tail => consume(map ++ Map("eta" -> value), tail)
      case "-alpha" :: value :: tail => consume(map ++ Map("alpha" -> value), tail)
      case "-burnIn" :: value :: tail => consume(map ++ Map("burnIn" -> value), tail)
      case "-skip" :: value :: tail => consume(map ++ Map("skip" -> value), tail)
      case "-in" :: value :: tail => consume(map ++ Map("trainFile" -> value), tail)
      case "-betaFile" :: value :: tail => consume(map ++ Map("betaFile" -> value), tail)
      case option :: tail => println("unknown option " + option); exit(1)
    }
  }

  def parseArgs(args: Array[String]): Map[String, String] = {
    if (args.isEmpty) {
      println(usage)
      exit(1)
    }
    
    val arglist = args.toList
    consume(Map(), arglist)
  }
  
  def parseConfig(args: Array[String]): LDAConfig = {
    val map = parseArgs(args)
    
    val missing = requiredArgs -- map.keys
    if (!missing.isEmpty){
      println(s"not enough arguments! The following are missing: ${missing.mkString(", ")}")
      exit(1)
    }
    
    val logLevel = if (map.get("verbose").getOrElse("false").toBoolean) LoggerLevel.DEBUG else LoggerLevel.INFO
    
    val conf = LDAConfig(
      nworker = map("nworker").toInt,  
      numTopics = map("numTopics").toInt,
      vocSize = map("vocSize").toInt,
      iterations = map("iterations").toInt,
      miniBatchSize = map("miniBatchSize").toInt,
      burnIn = map.get("burnIn").getOrElse(map("iterations")).toInt,
      eta = map.get("eta").getOrElse("0.1").toFloat,
      alpha = map.get("alpha").getOrElse("0.1").toFloat,
      skip = map.get("skip").getOrElse("5").toInt,
      trainFile = map("trainFile"),
      saveBetaPath = map("betaFile"),
      inMem = map.get("inMemoryCorpus").getOrElse("false").toBoolean,
      loglevel = logLevel
      )
      
    val f = new File(conf.trainFile)
    if (!f.exists()){
      println(s"training file ${conf.trainFile} does not exist!")
      exit(1)
    }
      
    conf  
  }
}
