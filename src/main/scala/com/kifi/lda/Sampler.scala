package com.kifi.lda

import scala.math.sqrt

import org.apache.commons.math3.distribution.{GammaDistribution, NormalDistribution}
import org.apache.commons.math3.random.RandomGenerator

class MultinomialSampler(rng: RandomGenerator){
  def sample(alphas: Seq[Double]): Int = {
    val x = rng.nextFloat
    var s = 0.0
    var i = 0
    alphas.foreach{ a =>
      s += a
      if (s > x) return i
      i += 1
    }
    alphas.size - 1
  }
}

case class DirichletSampler(alphas: Array[Float], rng: RandomGenerator){
  
  private val gammaSamplers = alphas.map{ alpha => new GammaDistribution(rng, alpha, 1)}
  
  def sample(alphas: Array[Float]): Array[Float] = {
	val ys = alphas.map{ alpha => 
	  val sampler = new GammaDistribution(rng, alpha, 1)
	  sampler.sample()
	}
	val s = ys.sum
	ys.map{ y => (y/s).toFloat}
  }
}

// in practice, the alphas are from word counting, which are integers (easily > 2)
// for gamma with shape (>= 2), it can be well approximated by Gaussian sampler. 
case class FastDirichletSampler(rng: RandomGenerator){
  def sample(alphas: Array[Float]): Array[Float] = {
    val ys = alphas.map{ alpha =>
      assume(alpha >= 0)
      
      if (alpha < 0.01) alpha		// just return the mean
      else if (alpha > 2) {			// approximate by a Gaussian
        val gaussianGen = new NormalDistribution(rng, alpha.toDouble, sqrt(alpha).toDouble)
        val s = gaussianGen.sample() 
        s max (s * -1) // avoid negative
      } else {
        val gammaGen = new GammaDistribution(rng, alpha, 1)
        gammaGen.sample()
      }
    }
    
    val s = ys.sum
    
	ys.map{y => (y/s).toFloat}
  }
}
