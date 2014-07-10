package com.kifi.lda

import akka.actor._
import scala.util.Random
import org.apache.commons.math3.random.Well19937c

class DocSamplingActor(numTopics: Int) extends Actor {

  private val alpha = 0.1f
  private val topicCounts = new Array[Float](numTopics)
  private val multinomial = new Array[Float](numTopics)
  private val rng = new Well19937c()
  private val dirSampler = new FastDirichletSampler(rng)
  private val multiSampler = new MultinomialSampler(rng)
  
  private def resetTopicCounts() {
    var i = 0
    while (i < numTopics) { topicCounts(i) = alpha; i+= 1 }
  }

  private def sampleTheta(zs: Seq[Int]): Array[Float] = {
    var i = 0
    resetTopicCounts()
    while (i < zs.size){ topicCounts(zs(i)) += 1; i += 1 }
    dirSampler.sample(topicCounts)
  }

  private def uniformSampling(doc: Doc, numTopics: Int): SamplingResult = {
    val z = (0 until doc.content.size).map{ x => Random.nextInt(numTopics)}
    SamplingResult(doc.index, Theta(sampleTheta(z)), WordTopicAssigns((doc.content zip z)))
  }

  private def sampling(doc: Doc, theta: Theta, beta: Beta): SamplingResult = {

    val zs = doc.content.map{ w =>
      var i = 0
      var s = 0f
      while (i < numTopics){ multinomial(i) = theta.value(i) * beta.get(i, w); s += multinomial(i); i += 1}
      multiSampler.sample(multinomial, s)
    }

    SamplingResult(doc.index, Theta(sampleTheta(zs)), WordTopicAssigns((doc.content zip zs)))
  }

  def receive = {
    case UniformSampling(doc) => sender ! uniformSampling(doc, numTopics)

    case Sampling(doc, theta, beta) => sender ! sampling(doc, theta, beta)
  }
}
