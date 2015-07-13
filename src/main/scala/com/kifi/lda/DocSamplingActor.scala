package com.kifi.lda

import akka.actor._
import scala.util.Random
import org.apache.commons.math3.random.Well19937c

class DocSamplingActor(numTopics: Int, alph: Float) extends Actor {

  private val alpha = alph
  private val topicCounts = new Array[Float](numTopics)
  private val multinomial = new Array[Float](numTopics)
  private val rng = new Well19937c()
  private val dirSampler = new FastDirichletSampler(rng)
  private val multiSampler = new MultinomialSampler(rng)
  
  def receive = {
    case UniformSampling(doc) => sender ! uniformSampling(doc, numTopics)
    case Sampling(doc, theta, beta) => sender ! sampling(doc, theta, beta)
  }
  
  private def resetTopicCounts() {
    var i = 0
    while (i < numTopics) { topicCounts(i) = alpha; i+= 1 }
  }

  private def sampleTheta(zs: Seq[Int]): Array[Float] = {
    resetTopicCounts()
    var i = 0
    while (i < zs.size){ topicCounts(zs(i)) += 1; i += 1 }
    dirSampler.sample(topicCounts)
  }

  private def uniformSampling(doc: Doc, numTopics: Int): SamplingResult = {
    val z = doc.content.map{ _ => Random.nextInt(numTopics)}
    SamplingResult(doc.index, Theta(sampleTheta(z)), WordTopicAssigns((doc.content zip z)))
  }

  private def sampling(doc: Doc, theta: Theta, beta: Beta): SamplingResult = {
    var prev = -1
    var s = 0f
    var i = 0
    
    val zs = doc.content.map{ w =>
      if (w != prev){
        i = 0
      	s = 0f
        while (i < numTopics){ multinomial(i) = theta.value(i) * beta.get(i, w); s += multinomial(i); i += 1}  
      }
      prev = w
      multiSampler.sample(multinomial, s)		// reuse these two parameters whenever possible
    }

    SamplingResult(doc.index, Theta(sampleTheta(zs)), WordTopicAssigns((doc.content zip zs)))
  }

}
