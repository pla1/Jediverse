#!/bin/bash
java -cp /home/htplainf/.m2/repository/com/google/code/gson/gson/2.8.5/*:\
/home/htplainf/.m2/repository/com/google/guava/guava/19.0/*:\
/home/htplainf/.m2/repository/org/jsoup/jsoup/1.11.3/*:\
/home/htplainf/projects/Jediverse/target/classes:. \
com.pla.jediverse.CommandLineInterface

