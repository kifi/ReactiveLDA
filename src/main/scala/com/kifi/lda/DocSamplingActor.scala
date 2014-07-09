package com.kifi.lda

import akka.actor._
import scala.util.Random
import org.apache.commons.math3.random.Well19937c

class DocSamplingActor(numTopics: Int) extends Actor {

  private val alpha = 0.1f
  private val topicCounts = Array.fill(numTopics)(alpha)
  private val multinomial = new Array[Double](numTopics)
  private val rng = new Well19937c()
  private val dirSampler = new FastDirichletSampler(rng)
  private val multiSampler = new MultinomialSampler(rng)
  
  private def resetTopicCounts() {
    var i = 0
    while (i < numTopics) { topicCounts(i) = alpha; i+= 1 }
  }

  private def sampleTheta(zs: Seq[Int], numTopics: Int): Array[Float] = {
    var i = 0
    while (i < zs.size){ topicCounts(zs(i)) += 1; i += 1 }
    
    val sample = dirSampler.sample(topicCounts)
    resetTopicCounts()
    sample
  }

  private def uniformSampling(doc: Doc, numTopics: Int): SamplingResult = {
    val z = (0 until doc.content.size).map{ x => Random.nextInt(numTopics)}
    SamplingResult(doc.index, Theta(sampleTheta(z, numTopics)), WordTopicAssigns((doc.content zip z)))
  }

  private def sampling(doc: Doc, theta: Theta, beta: Beta): SamplingResult = {

    val zs = doc.content.map{ w =>
      var i = 0
      while (i < numTopics){ multinomial(i) = theta.value(i).toDouble * beta.get(i, w); i += 1 }
      val s = multinomial.sum
      i = 0
      while (i < numTopics){ multinomial(i) /= s; i+=1 }
      val z = multiSampler.sample(multinomial)
      z
    }

    SamplingResult(doc.index, Theta(sampleTheta(zs, numTopics)), WordTopicAssigns((doc.content zip zs)))
  }

  def receive = {
    case UniformSampling(doc) => sender ! uniformSampling(doc, numTopics)

    case Sampling(doc, theta, beta) => sender ! sampling(doc, theta, beta)
  }
}
