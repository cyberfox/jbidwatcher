# Simplistic Makefile for JBidWatcher
#
# Presumes jikes for Java Compiler right now.  Works equally well with
# the standard 'javac'. I use jikes because when I compile with javac,
# it takes 22 seconds.  jikes takes 2.7 seconds.
#
# javac will complain about deprecation.  Sun deprecated several
# methods on java.util.Date without providing an adequate migration
# path.  (Specifically, GregorianCalendar doesn't support the
# millisecond accuracy used by JBidWatcher.)  I'm still using them.
# It's purely for the Yahoo! auction server, which doesn't provide a
# year for it's auctions, so JBidWatcher has to guess.  Sun also
# deprecated the 'encode' function on the URLEncoder, but the replacement
# is only in 1.4.  *sigh*
#
# For the deprecation in JDropListener, once again, Sun decided that
# there was only one use for DataFlavor.PlainTextFlavor, when in fact it's
# very useful to test to see if that flavor is supported during an accept
# test for a drop.  This is, imho, a faulty deprecation.  Understandable,
# but bad.
#
# If you are comfortable with those issues, feel free to use javac!
#

JAR = jar
VER = 1.0.2
JAVAC = javac
PRODUCT= JBidWatcher
TARSRC = jbidwatcher-$(VER)
BINARY = $(PRODUCT)-$(VER).jar
OPT_BIN= $(PRODUCT)-$(VER)_o.jar
UNOPT_BIN= $(PRODUCT)-$(VER)_u.jar
TARFILE = $(TARSRC).tar.gz
MANIFEST = META-INF/MANIFEST.MF
NULLDEV = /dev/null

compile:
	@echo Compiling all classes.
	@mkdir -p classes
	@$(JAVAC) -d classes/ -g JBidWatch.java

release: optimize tar

prerelease: jar tar

jar: compile
	@echo Building executable .jar file.
	@cp $(HOME)/.jbidwatcher/display.cfg .
	@$(JAR) cfm $(BINARY) $(MANIFEST) *.jpg *.ser *.xsl display.cfg icons/*.gif help/*.jbh help/*.jpg com org jbidwatcher.properties -C classes/ .
	@rm display.cfg

tar:
	@echo Building source .tar.gz file.
	@ln -s `pwd` $(TARSRC)
	@cp $(HOME)/.jbidwatcher/display.cfg .
	@tar -czf $(TARFILE) $(TARSRC)/*.java $(TARSRC)/icons/*.gif $(TARSRC)/help/*.jbh $(TARSRC)/help/*.jpg $(TARSRC)/*.ser $(TARSRC)/*.xsl $(TARSRC)/*.jpg $(TARSRC)/display.cfg $(TARSRC)/auctions.dtd $(TARSRC)/$(MANIFEST) $(TARSRC)/TODO $(TARSRC)/Makefile $(TARSRC)/build.xml $(TARSRC)/jbidwatcher.jnlp $(TARSRC)/com $(TARSRC)/org $(TARSRC)/javazoom $(TARSRC)/jbidwatcher.properties
	@rm display.cfg
	@rm $(TARSRC)

run: jar
	@java -Xmx256m -jar $(BINARY)

clean:
	@rm -f $(BINARY) $(UNOPT_BIN) $(TARFILE)
	@rm -rf classes

optimize: jar
	@if [ -e Jopt.jar ]; then \
	  echo Optimizing .jar file \(takes some time\).; \
	  java -jar Jopt.jar $(BINARY) | tail -1; \
	  mv $(BINARY) $(UNOPT_BIN); \
	  mv $(OPT_BIN) $(BINARY); \
    else true; fi
