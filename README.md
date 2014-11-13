ReactiveLDA
===========

__ReactiveLDA__ is a lightweight implementation of the [Latent Dirichlet Allocation](http://en.wikipedia.org/wiki/Latent_Dirichlet_allocation) (LDA) algorithm. The main ingredient is a parallel vanilla Gibbs sampling algorithm, and the parallelism is done via Akka actors (hence the naming 'reactive'). This approach is very different from a more popular algorithm based on collapsed Gibbs sampler, which is difficult to be parallelized, unless one assumes weak dependencies in sampling latent variables. 

# Introduction

## The LDA Model
The LDA model assumes each word in a document is generated in two steps:
- Sample a topic from the document-topic distribution (each document has its own document-topic distribution);
- From the sampled topic, sample a word from the topic-word distribution (each topic has its own topic-word distribution). 
 
Both the document-topic distribution and topic-word distribution are multinomial distributions. The model assumes that these distributions themselves are sampled from Dirichlet distributions. The objective of LDA learning algorithm is to infer the topic-word distribution, given observed words in a corpus. Such topic-word distribution can later give vector representation of words and documents. In addition to text analysis, it has been reported that LDA model can be applied to perform collabarative filtering.

## The Gibbs Sampler
In this implementation, we use the vanilla Gibbs sampler. We sample word topics, docuemnt-topic distribution, and topic-word distribution alternatively. The sampling iteration goes like this:
- Given topic-word distributions and the document-topic distribution for a document, we sample topic variables for words in the document.
- After sampling topic variables for the document, we can sample (update) document-topic distribution for that document.
- After we have sampled topic variables for all words in the corpus, we can sample topic-word distributions.

To kick off the iteration, in the first iteration, we perform uniform sampling for topic variables for all words in the corpus.



## The Actor System
There are 3 types of actors:
- `BetaActor` (master)
  - Retrieve jobs from `MiniBatchActor`
  - Distribute jobs to `DocSamplingActor`s
  - Update `beta`, which gives topic-word distributions
- `DocSamplingActor` (worker)
  - Sample latent topic variable for each word in the document
  - Sample document's topic distribution
- `MiniBatchActor`
  - Send batches of documents to `BetaActor`

## Performance
ReactiveLDA has the following features:
- Highly scalable Map-Reduce like job distribution. It would be interesting to extend the work by using remote Akka actors.
- Memory friendly: no need to hold the entire corpus in memory. The major part of memory footprint is from model variables: topic-word distributions and document-topic distributions. We also provide an in-memory option, which can save I/O when the corpus can fit into memory. 
- Good speedup: empirical results suggest that ReactiveLDA achieves near-perfect parallel speedup (we have only performed tests up to 32 CPU cores).
- Good speed: we have done some experiments with English wikipedia corpus (3M documents, 100K vocabulary size, 1.5 billion tokens, after filtering out redirected articles and low frequencey words). We train a topic model with 512 dimensions on an Amazon instance with 32 virtual CPUs, one iteration of Gibbs sampling takes about 10 minutes. A total number of 50 iterations usually gives reasonably good model. That is less than half a day (with a strong machine)! 

 
# How to Use the Library

## Build the Jar
Assuming you are at the project's root directory. Start `sbt` console and enter `assembly`. You should have `LDA.Jar` built under the folder `/target/scala-x.xx`.

## Use the Jar
You can run the jar with minimal required arguments like this:
```
java -jar LDA.jar -nw 32 -t 100 -voc 123456 -iter 50 -b 10000 -in trainFile.txt -betaFile betaFile.bin
```

The parameters aboves are:
- -nw: number of workers
- -t: number of topics
- -voc: vocabulary size of your corpus
- -iter: number of iterations of Gibbs sampling
- -b: This defines a 'mini-batch' size. This is because corpus may not fit into memory. In this case, use an appropriate mini-batch size helps to hold a small portion of corpus in memory and perform sampling just for this part. If this value is too low, I/O overhead may reduce performance. 
- -in: path to the training file (training file format is explained below)
- -betaFile: the file name to store the `beta` parameter. `beta` gives topic-word distributions. 

For a comprehensive arguments list, please see documentations in the source code, or type `java -jar LDA.jar` for usage. 

## Training File Format
For simplicity and efficiency reasons, we assume the input file has the following format:
- The file represents the whole corpus.
- Each line represents a document in the corpus.
- Each line is a sequence of `word-id`s, seperated by space. 
- word-ids form the integer set {0, 1, 2, ..., V-1}, where `V` is the vocabulary size. 

We provide a util class to convert regular txt file corpus into this format. See `CorpusUtil.scala`. It should be straigtforward to use the class from `sbt` console. 

## Sample Run
We provide a toy training file `trivial.test.txt` under the `test` folder. This represents a corpus with 6 documents and 4 words. Word 0 and 1 form a topic, 2 and 3 form another topic. 

```
0 0 0 0 1 1 1 1
1 1 1 1 1 0 0 0
1 1 1 0 0 0 0 0
2 2 2 2 2 3 3 3
3 2 2 2 3 3 3 2
3 3 3 2 2 3 3 2
```

You can train an LDA model on this simple corpus like this:
```
java -jar LDA.jar -nw 10 -t 2 -voc 4 -iter 50 -b 3 -in trivial.txt -betaFile trivial_beta.bin -verbose true
```
At the end of training, you would see some console outputs like this
```
DEBUG: sampling dirichlet with 12.1 12.1 0.1 0.1
DEBUG: sampled beta for topic 0: 0.45371073 0.5395452 2.3765434E-4 0.006506452
DEBUG: sampling dirichlet with 0.1 0.1 12.1 12.1
DEBUG: sampled beta for topic 1: 0.0011156916 1.17734814E-7 0.46746284 0.53142136
```

That is, the final model consists of two topic-word distributions:
```
topic 0: 0.45371073 0.5395452 2.3765434E-4 0.006506452
topic 1: 0.0011156916 1.17734814E-7 0.46746284 0.53142136
```
So, topic 0 is defined by word 0 and word 1, and topic 1 is defined by word 2 and 3. 

Of course, this is an overly simplified example, and we know the correct number of topics a priori. In practice, one has to try a few different topic sizes and evaluate the quality of the model (e.g. by computing perplexity, or examine if similar words have similar topic distributions, etc). 

## Use the Trained Model
We provide a simple util class `ModelReader` to read the trained `beta` file. With that util class you can do the following:
- Examine topic-word distributions, e.g. top words in a topic.
- Examine word-topic distributions. This helps to evaluate model qualtiy, e.g. simialr words should have similar topic distribution.
- Text classification: One can use the model to generate a low dimensional representation of a document. The vector representation is a probability distribution over topics. We provide two methods:
  - A naive summation of word vectors. This is fast, but we ignore context information.
  - An EM style inference. This takes account of word context. It's slightly slower than the first method, yet it potentailly gives better classification result. The iterative algorithm usually converges in a few steps. In fact, the naive summation alogrithm is just a special case of EM inference, with only one iteration.

Examples (in `sbt` console):
```
import com.kifi.lda.ModelReader

val beta = ModelReader.parseBeta("beta.bin")                    // generated by running the Jar
val word2id = ModelReader.parseWord2id("word2id.json")          // can be generated by CorpusUtil
val reader = new ModelReader(beta, w2id)


// show top 20 words in topic 1
reader.showTopics(topic = 1, topK = 20)

// show the top 10 topics associated with the word "akka"
reader.showWordTopic("akka", topK = 10)

// write topic and topic words to a file
val topics = reader.getAllTopics(topK: Int = 100)
scala.tools.nsc.io.File("topic_words.txt").writeAll(topics)

// classification
reader.classify("akka actor documentation", topK = 3)
reader.EM_inference("akka actor documentation", topK = 3)
```



