package com.kifi.lda

sealed trait LDAMessage
case object StartTraining extends LDAMessage
case class NextMiniBatchRequest(size: Int) extends LDAMessage
case class MiniBatchDocs(docs: Seq[Doc], wholeBatchEnded: Boolean) extends LDAMessage
case class UniformSampling(doc: Doc) extends LDAMessage
case class Sampling(doc: Doc, theta: Theta, beta: Beta) extends LDAMessage
case class SamplingResult(docIndex: Int, theta: Theta, wordTopicAssign: WordTopicAssigns) extends LDAMessage
