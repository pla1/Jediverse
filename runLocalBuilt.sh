#!/bin/bash
git pull
if [ $? -ne 0 ]
then
  echo -e "git pull failed.\n\nMake sure you have cloned Jediverse and you are in its directory."
  echo -e "Example:\n\ngit clone https://github.com/pla1/Jediverse.git\ncd Jediverse\n./runLocalBuilt.sh"
  exit -1
fi
guava="guava-23.0.jar"
jsoup="jsoup-1.11.3.jar"
if [ ! -f "$guava" ]
then
  echo "Downloading $guava."
  wget "http://search.maven.org/remotecontent?filepath=com/google/guava/guava/23.0/$guava" --output-document="$guava"
fi
if [ ! -f "$jsoup" ]
then
  echo "Downloading $jsoup."
  wget 'https://github.com/jhy/jsoup/releases/tag/jsoup-1.11.3' --output-document="$jsoup"
fi
java -cp .:* com.pla.jediverse.CommandLineInterface

