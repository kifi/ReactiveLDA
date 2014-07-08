package com.kifi.lda

import akka.actor._

object LDA {

  def main(args: Array[String]) = {
    val system = ActorSystem("LDASystem")
    val config = LDAConfig.parseConfig(args)
    
    val docIter = if (config.inMem) new InMemoryDocIterator(config.trainFile) else new DocIteratorImpl(config.trainFile)
    val readerActor = system.actorOf(Props(new MiniBatchActor(docIter)), "readerActor")
    val betaActor = system.actorOf(Props(new BetaActor(readerActor, config)), "betaActor")
    
    betaActor ! StartTraining
  }

}
