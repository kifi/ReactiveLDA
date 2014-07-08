package com.kifi.lda

import scala.math.sqrt
import scala.util.Random

import org.apache.commons.math3.distribution.GammaDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.random.RandomGenerator
import org.apache.commons.math3.random.Well19937c


object Sampler {
  private val rng = new Well19937c()

  def dirichlet(alphas: Array[Float]): Array[Double] = {
    val dir = FastDirichletSampler(alphas, rng)
    dir.sample
  }

  def multiNomial(alphas: Seq[Double]): Int = {
    val x = Random.nextFloat
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
  
  def sample(): Array[Double] = {
	val ys = gammaSamplers.map{ sampler => sampler.sample()}
	val s = ys.sum
	ys.map{ y => y/s}
  }
  
}

// in practice, the only relevant alpha is the one that comes from word counting, which are integers (easily > 2 )
// for gamma with shape ( >= 2), it can be well approximated by Gaussian sampler. 
case class FastDirichletSampler(alphas: Array[Float], rng: RandomGenerator){
  def sample(): Array[Double] = {
    val ys = alphas.map{ alpha =>
      assume(alpha >= 0)
      
      if (alpha < 0.01) alpha		// just return the mean
      else if (alpha > 2) {			// approximate by a Gaussian
        val gaussianGen = new NormalDistribution(rng, alpha.toDouble, sqrt(alpha).toDouble)
        var s = gaussianGen.sample()
        while (s.isNaN()) s = gaussianGen.sample()
        
        s max 1e-3 // avoid negative
      } else {
        val gammaGen = new GammaDistribution(rng, alpha, 1)
        var s = gammaGen.sample()
        while(s.isNaN()) s = gammaGen.sample()
        s
      }
    }
    
    val s = ys.sum
    
	ys.map{ y => y/s}
  }
}
