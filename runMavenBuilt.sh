#!/bin/bash
#
# Build Jediverse with git, mvn commands and run it.
#
# Java property setting required to stop high CPU utilization after closing a WebSocket.
# Reference:  -Djdk.tls.disabledAlgorithms=TLSv1.3 https://stackoverflow.com/questions/54485755/java-11-httpclient-leads-to-endless-ssl-loop
#
git pull
mvn clean install
java -cp "${HOME}/.m2/repository/com/google/code/gson/gson/2.8.5/*:\
${HOME}/.m2/repository/com/google/guava/guava/19.0/*:\
${HOME}/.m2/repository/org/jsoup/jsoup/1.11.3/*:\
./target/classes:." \
com.pla.jediverse.CommandLineInterface

