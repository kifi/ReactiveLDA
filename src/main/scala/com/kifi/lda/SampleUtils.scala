package com.kifi.lda

import org.apache.commons.math3.distribution.GammaDistribution
import org.apache.commons.math3.random.RandomGenerator
import scala.math.sqrt
import org.apache.commons.math3.distribution.NormalDistribution
import scala.Array.canBuildFrom


case class DirichletSampler(alphas: Array[Float], rng: RandomGenerator){
  
  private val gammaSamplers = alphas.map{ alpha => new GammaDistribution(rng, alpha, 1)}
  
  def sample(): Array[Double] = {
	val ys = gammaSamplers.map{ sampler => sampler.sample()}
	val s = ys.sum
	ys.map{ y => y/s}
  }
  
}

// in practice, the only relevant alpha is the one that comes from word counting, which are integers (easily > 2 )
// for gamma with shape ( >= 2), it can be well approximated by gaussian sampler. 
case class FastDirichletSampler(alphas: Array[Float], rng: RandomGenerator){
  def sample(): Array[Double] = {
    val ys = alphas.map{ alpha =>
      
      if (alpha < 0.01) alpha		// just return the mean
      else if (alpha > 2) {			// approximate by a gaussian
        val gaussianGen = new NormalDistribution(rng, alpha.toDouble, sqrt(alpha).toDouble)
        gaussianGen.sample() max 1e-3
      } else {
        val gammaGen = new GammaDistribution(rng, alpha, 1)
        gammaGen.sample()
      }
    }
    
    val s = ys.sum
	ys.map{ y => y/s}
  }
}
