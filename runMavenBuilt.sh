#!/bin/bash
#
# Build Jediverse with git, mvn commands and run it.
#
git pull
mvn clean install
java -cp "${HOME}/.m2/repository/com/google/code/gson/gson/2.8.5/*:\
${HOME}/.m2/repository/com/google/guava/guava/19.0/*:\
${HOME}/.m2/repository/org/jsoup/jsoup/1.11.3/*:\
./target/classes:." \
com.pla.jediverse.CommandLineInterface

