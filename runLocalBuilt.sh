#!/bin/bash
#
# Build Jediverse locally using git, wget, javac commands and then run it.
#
# There is a bug with HttpClient in OpenJDK 11.0.3+7 that creates a CPU spike.
# Use OpenJDK 12 from https://adoptopenjdk.net
#
git pull
if [ $? -ne 0 ]
then
  echo -e "git pull failed.\n\nMake sure you have cloned Jediverse and you are in its directory."
  echo -e "Example:\n\ngit clone https://github.com/pla1/Jediverse.git\ncd Jediverse\n./runLocalBuilt.sh"
  exit -1
fi
guava="guava-23.0.jar"
jsoup="jsoup-1.11.3.jar"
gson="gson-2.8.5.jar"
if [ ! -f "$guava" ]
then
  echo "Downloading $guava."
  wget "http://search.maven.org/remotecontent?filepath=com/google/guava/guava/23.0/$guava" --output-document="$guava"
fi
if [ ! -f "$jsoup" ]
then
  echo "Downloading $jsoup."
  wget 'https://jsoup.org/packages/jsoup-1.11.3.jar' --output-document="$jsoup"
fi
if [ ! -f "$gson" ]
then
  echo "Downloading $gson."
  wget 'https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar' --output-document="$gson"
fi
javac  -encoding UTF-8 -cp .:* src/main/java/com/pla/jediverse/*.java
java -cp src/main/java:.:* com.pla.jediverse.CommandLineInterface
