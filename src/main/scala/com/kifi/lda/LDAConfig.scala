package com.kifi.lda

case class LDAConfig(
  nworker: Int,
  numTopics: Int, 
  vocSize: Int, 
  iterations: Int, 
  discount: Boolean,
  inMem: Boolean,
  miniBatchSize: Int,
  trainFile: String,
  saveBetaPath: String, 
  saveCountsPath: String
)

object LDAConfig {
  
  val usage = """
    Usage: java -jar LDA.jar -nw nworker -t numTopics -voc vocSize -iter iterations -disc discountWordFreq -inMem inMemoryCorpus -b miniBatchSize -in trainFile -betaFile betaFilePath -countsFile countsFilePath 
    """
    
  val requiredArgs = Set("nworker", "numTopics", "vocSize", "iterations", "discount", "miniBatchSize", "trainFile", "betaFile", "countsFile")

  private def consume(map: Map[String, String], list: List[String]): Map[String, String] = {
    list match {
      case Nil => map
      case "-nw" :: value :: tail => consume(map ++ Map("nworker" -> value), tail)
      case "-t" :: value :: tail => consume(map ++ Map("numTopics" -> value), tail)
      case "-voc" :: value :: tail => consume(map ++ Map("vocSize" -> value), tail)
      case "-iter" :: value :: tail => consume(map ++ Map("iterations" -> value), tail)
      case "-disc":: value :: tail => consume(map ++ Map("discount" -> value), tail)
      case "-inMem":: value :: tail => consume(map ++ Map("inMemoryCorpus" -> value), tail)
      case "-b" :: value :: tail => consume(map ++ Map("miniBatchSize" -> value), tail)
      case "-in" :: value :: tail => consume(map ++ Map("trainFile" -> value), tail)
      case "-betaFile" :: value :: tail => consume(map ++ Map("betaFile" -> value), tail)
      case "-countsFile" :: value :: tail => consume(map ++ Map("countsFile" -> value), tail)
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
    
    LDAConfig(
      nworker = map("nworker").toInt,  
      numTopics = map("numTopics").toInt,
      vocSize = map("vocSize").toInt,
      iterations = map("iterations").toInt,
      discount = map("discount").toBoolean,
      miniBatchSize = map("miniBatchSize").toInt,
      trainFile = map("trainFile"),
      saveBetaPath = map("betaFile"),
      saveCountsPath = map("countsFile"),
      inMem = map.get("inMemoryCorpus").getOrElse("false").toBoolean)
  }
}
