ReactiveLDA
===========

__ReactiveLDA__ is a lightweight implementation of the Latent Dirichlet Allocation (LDA) algorithm. The main ingredient is a parallel vanilla Gibbs sampling algorithm, and the parallelism is done via Akka actors (hence the naming 'reactive'). This approach is very different from a more popular algorithm based on collapsed Gibbs sampler, which is difficult to be parallelized, unless one assumes weak dependencies in sampling latent varialbes. 

# Build the Jar
Assuming you are at project's root directory. Start `sbt` console and enter `assembly`. You should have `LDA.Jar` built under the folder `/target/scala-x.xx/LDA.jar`.

# Use the Jar
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
- -betaFile: the file name to store the `beta` parameter. `beta` is the 'topic-word' distribution. 

For a comprehensive argument lists, please see documentations is source code, or type `java -jar LDA.jar` for usage. 

# training file format
For simplicity and efficiency reasons, we assuem the input file has the following format:
- Each line in the txt is a document
- Each line is a sequence of `word-id`s, seperated by a space. 
- `word-ids` ranges from 0 (inclusive) to `V-1` (exclusive), where `V` is the vocabulary size. 

We provide a util class to convert regular txt file corpus into this format. See `CorpusUtil.scala`. It should be straigtforward to use the class from `sbt` console and perform the conversion. 

## example training file
We provide a toy training file in the source code `trivial.test.txt` under the `test` folder. This represents a corpus with 6 documents and 4 words.

```
0 0 0 0 1 1 1 1
1 1 1 1 1 0 0 0
1 1 1 0 0 0 0 0
2 2 2 2 2 3 3 3
3 2 2 2 3 3 3 2
3 3 3 2 2 3 3 2
```

You can train an lda model on this simple corpus like this:
```
java -jar LDA.jar -nw 10 -t 2 -voc 4 -iter 50 -b 3 -in trivial.txt -betaFile trivial_beta.bin -verbose true
```
At the end of training, you would see some console outputs like this
```
INFO: sampling dirichlet with 12.1 12.1 0.1 0.1
INFO: sampled beta for topic 0: 0.45371073 0.5395452 2.3765434E-4 0.006506452
INFO: sampling dirichlet with 0.1 0.1 12.1 12.1
INFO: sampled beta for topic 1: 0.0011156916 1.17734814E-7 0.46746284 0.53142136
```

You can intepretate the training result this way: we asked the model to train a 2-topic model on this toy corpus, and the topic-word vectors are 
```
topic 0: 0.45371073 0.5395452 2.3765434E-4 0.006506452
topic 1: 0.0011156916 1.17734814E-7 0.46746284 0.53142136
```
so, topic 0 is defined by word `0` and word `1`, and topic 1 is defined by word `2` and `3`. 





