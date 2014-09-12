package com.kifi.lda

import scala.math._

import org.apache.commons.math3.distribution.{GammaDistribution, NormalDistribution}
import org.apache.commons.math3.random.RandomGenerator

class MultinomialSampler(rng: RandomGenerator){
  // alphas need not be normalized. save one scan of the array.
  def sample(alphas: Seq[Float], sumTo: Float = 1f): Int = {
    val x = rng.nextFloat * sumTo
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

case class DirichletSampler(rng: RandomGenerator){
  
  def sample(alphas: Array[Float]): Array[Float] = {
    var s = 0.0
	val ys = alphas.map{ alpha => 
	  val sampler = new GammaDistribution(rng, alpha, 1)
	  val y = sampler.sample()
	  s += y
	  y
	}
	ys.map{ y => (y/s).toFloat}
  }
}


/**
 *  For gamma with shape (>= 2), it can be well approximated by Gaussian sampler.
 */  
case class FastDirichletSampler(rng: RandomGenerator){
  def sample(alphas: Array[Float]): Array[Float] = {
    var s = 0.0
    val ys = alphas.map{ alpha =>
      assume(alpha >= 0)
      
      val y = if (alpha < 0.01) alpha		// just return the mean
      else if (alpha > 2) {			// approximate by a Gaussian
        val gaussianGen = new NormalDistribution(rng, alpha.toDouble, sqrt(alpha).toDouble)
        val s = gaussianGen.sample() 
        abs(s) // avoid negative
      } else {
        val gammaGen = new GammaDistribution(rng, alpha, 1)
        gammaGen.sample()
      }
      s += y
      y
    }
    
	ys.map{y => (y/s).toFloat}
  }
}
