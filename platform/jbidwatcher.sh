#!/bin/sh

if (java -version 2>&1 | head -1 | fgrep -q "1.8")
then
  java -cp lib/annotations.jar:lib/aopalliance.jar:lib/appbundler-1.0ea.jar:lib/apple.jar:lib/derby.jar:lib/guice-3.0.jar:lib/guice-assistedinject-3.0.jar:lib/jDeskMetrics.jar:lib/javax.inject.jar:lib/jdesktop.jar:lib/jl1.0.1.jar:lib/jline-2.11.jar:lib/jruby-incomplete.jar:lib/json_simple-1.1.jar:lib/jsoup-1.7.1.jar:lib/jsr305-1.3.9.jar:lib/l2fprod-common-fontchooser.jar:lib/mahalo.jar:lib/mysql-connector-java-5.1.7-bin.jar:lib/readline.jar:lib/txtmark.jar:lib/jbidwatcher:app/JBidwatcher.jar com.jbidwatcher.app.JBidWatch
else
  echo "You must install Oracle's Java 8 to run JBidwatcher."
  echo "Download it from here:\n\thttps://www.java.com/download"
fi
