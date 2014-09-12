package com.kifi.lda

/**
 * Configs:
 * - nworker: num of DocSamplingActors. This is the main factor of parallel speed up.
 * - numTopic: num of topics
 * - vocSize: vocabulary size
 * - iterations: how many iterations on the corpus
 * - discount: This is used when we update Beta after one whole corpus update is finished. Default to false. For standard LDA algorithm, this should be false.
 * For each topic, we have a topic-word count vector, which represents how many times a word is assigned to a topic during Gibbs
 * sampling. However, frequent words (in the corpus) will have more opportunities to sample a topic. Thus, the topic-word counts may be biased towards
 * more frequent words, leads to a biased estimation that those words are "important" for that topic. Set this to true if you want to discount that bias. 
 * - inMem: If true, load entire corpus into an in-memory iterator. Otherwise, corpus stays on disk.
 * - miniBatchSize: A whole batch means a Gibbs sampling for the entire corpus. Since loading entire corpus into memory may not be feasible, miniBatchSize
 * controls how many documents to be loaded into memory. Bigger values require more memory consumption. Small values may have an impact on paralle speed up.
 * - eta: smooth factor for computing beta (topic-word distribution). default = 0.1
 * - alpha: smooth factor for computing theta (document-topic distribution). default = 0.1
 * - burnIn: burn in steps.
 * - skip: after burned in, gather one sample point (of beta) every skip size. 
 * - trainFile: path to training file.
 * - saveBetaPath: path to saved Beta file.
 * - saveCountsPath: path to saved topic-word counts file.   
 */

case class LDAConfig(
  nworker: Int,
  numTopics: Int, 
  vocSize: Int, 
  iterations: Int, 
  discount: Boolean,
  inMem: Boolean,
  miniBatchSize: Int,
  eta: Float,
  alpha: Float,
  burnIn: Int,
  skip: Int,
  trainFile: String,
  saveBetaPath: String, 
  saveCountsPath: String
)

object LDAConfig {
  
  val usage = """
    Usage: java -jar LDA.jar -nw nworker -t numTopics -voc vocSize -iter iterations [-disc discountWordFreq] 
    [-inMem inMemoryCorpus] -b miniBatchSize [-eta eta -alpha alpha -burnIn burnIn -skip skipSize] 
    -in trainFile -betaFile betaFilePath -countsFile countsFilePath 
    """
    
  val requiredArgs = Set("nworker", "numTopics", "vocSize", "iterations", "miniBatchSize", "trainFile", "betaFile", "countsFile")

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
      case "-eta" :: value :: tail => consume(map ++ Map("eta" -> value), tail)
      case "-alpha" :: value :: tail => consume(map ++ Map("alpha" -> value), tail)
      case "-burnIn" :: value :: tail => consume(map ++ Map("burnIn" -> value), tail)
      case "-skip" :: value :: tail => consume(map ++ Map("skip" -> value), tail)
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
    
    val conf = LDAConfig(
      nworker = map("nworker").toInt,  
      numTopics = map("numTopics").toInt,
      vocSize = map("vocSize").toInt,
      iterations = map("iterations").toInt,
      discount = map.get("discount").getOrElse("false").toBoolean,
      miniBatchSize = map("miniBatchSize").toInt,
      burnIn = map.get("burnIn").getOrElse("50").toInt,
      eta = map.get("eta").getOrElse("0.1").toFloat,
      alpha = map.get("alpha").getOrElse("0.1").toFloat,
      skip = map.get("skip").getOrElse("5").toInt,
      trainFile = map("trainFile"),
      saveBetaPath = map("betaFile"),
      saveCountsPath = map("countsFile"),
      inMem = map.get("inMemoryCorpus").getOrElse("false").toBoolean)
    
    if (conf.burnIn > conf.iterations){
      println(s"invalid burnIn size: burnIn size should be less or equal to total number of iterations")
      exit(1)
    }
      
    conf  
  }
}
