/*
 * Copyright (c) 2004, P. Simon Tuffs (simon@simontuffs.com)
 * All rights reserved.
 *
 * See full license at http://one-jar.sourceforge.net/one-jar-license.txt
 * This license is also included in the distributions of this software
 * under doc/one-jar-license.txt
 */	 

package com.simontuffs.onejar;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Run a java application which requires multiple support jars from inside
 * a single jar file.
 * 
 * <p>
 * Developer time JVM properties:
 * <pre>
 *   -Done-jar.main-class={name}  Use named class as main class to run. 
 *   -Done-jar.record[=recording] Record loaded classes into "recording" directory.
 *                                Flatten jar-names into directory tree suitable 
 * 								  for use as a classpath.
 *   -Done-jar.jar-names          Record loaded classes, preserve jar structure
 *   -Done-jar.verbose            Run the JarClassLoader in verbose mode.
 * </pre>
 * @author simon@simontuffs.com (<a href="http://www.simontuffs.com">http://www.simontuffs.com</a>)
 */
public class Boot {
	
	/**
	 * The name of the manifest attribute which controls which class 
	 * to bootstrap from the jar file.  The boot class can
	 * be in any of the contained jar files.
	 */
	public final static String BOOT_CLASS = "Boot-Class";
	
	public final static String MANIFEST = "META-INF/MANIFEST.MF";
	public final static String MAIN_JAR = "main/main.jar";

	public final static String WRAP_CLASS_LOADER = "Wrap-Class-Loader";
    public final static String WRAP_DIR = "wrap";
	public final static String WRAP_JAR = "/" + WRAP_DIR + "/wraploader.jar";

	public final static String PROPERTY_PREFIX = "one-jar.";
	public final static String MAIN_CLASS = PROPERTY_PREFIX + "main-class";
	public final static String RECORD = PROPERTY_PREFIX + "record";
	public final static String JARNAMES = PROPERTY_PREFIX + "jar-names";
	public final static String VERBOSE = PROPERTY_PREFIX + "verbose";
	public final static String INFO = PROPERTY_PREFIX + "info";
	
	protected static boolean info, verbose;

	// Singleton loader.
	protected static JarClassLoader loader = null;
	
	public static JarClassLoader getClassLoader() {
		return loader;
	}
    
    public static void setClassLoader(JarClassLoader $loader) {
        if (loader != null) throw new RuntimeException("Attempt to set a second Boot loader");
        loader = $loader;
    }

	protected static void VERBOSE(String message) {
		if (verbose) System.out.println("Boot: " + message);
	}

	protected static void WARNING(String message) {
		System.err.println("Boot: Warning: " + message); 
	}
	
	protected static void INFO(String message) {
		if (info) System.out.println("Boot: Info: " + message);
	}

    public static void main(String[] args) throws Exception {
    	run(args);
    }
    
    public static void run(String args[]) throws Exception {
    	
		if (false) {
			// What are the system properties.
	    	Properties props = System.getProperties();
	    	Enumeration _enum = props.keys();
	    	
	    	while (_enum.hasMoreElements()) {
	    		String key = (String)_enum.nextElement();
	    		System.out.println(key + "=" + props.get(key));
	    	}
		}
	    	
    	// Is the main class specified on the command line?  If so, boot it.
    	// Othewise, read the main class out of the manifest.
		String mainClass = null, recording = null;
		boolean record = false, jarnames = false;

		{
			// Default properties are in resource 'one-jar.properties'.
			Properties properties = new Properties();
			String props = "/one-jar.properties";
			InputStream is = Boot.class.getResourceAsStream(props); 
			if (is != null) {
				INFO("loading properties from " + props);
				properties.load(is);
			}
				 
			// Merge in anything in a local file with the same name.
			props = "file:one-jar.properties";
			is = Boot.class.getResourceAsStream(props);
			if (is != null) {
				INFO("loading properties from " + props);
				properties.load(is);
			} 
			// Set system properties only if not already specified.
			Enumeration _enum = properties.propertyNames();
			while (_enum.hasMoreElements()) {
				String name = (String)_enum.nextElement();
				if (System.getProperty(name) == null) {
					System.setProperty(name, properties.getProperty(name));
				}
			}
		}		
		// Process developer properties:
		mainClass = System.getProperty(MAIN_CLASS);
		if (System.getProperties().containsKey(RECORD)) {
			record = true;
			recording = System.getProperty(RECORD);
			if (recording.length() == 0) recording = null;
    	} 
		if (System.getProperties().containsKey(JARNAMES)) {
			record = true;
			jarnames = true;
		}
		
		if (System.getProperties().containsKey(VERBOSE)) {
			verbose = true;
            info = true;
		} 
		if (System.getProperties().containsKey(INFO)) {
			info = true;
		} 

		// If no main-class specified, check the manifest of the main jar for
		// a Boot-Class attribute.
		if (mainClass == null) {
	    	// Hack to obtain the name of this jar file.
	    	String jar = System.getProperty(PROPERTY_PREFIX + "jarname"); 
            if (jar == null) jar = System.getProperty(JarClassLoader.JAVA_CLASS_PATH);

            // Fix from 'eleeptg' for OS-X problems: extract first entry from classpath 
            // 'test.jar:/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Classes/.compatibility/14compatibility.jar' 
            if ((jar!=null) && (jar.indexOf(System.getProperty("path.separator")) > 0)) 
                jar = jar.substring(0, jar.indexOf(System.getProperty("path.separator"))); 
            
	    	JarFile jarFile = new JarFile(jar);
	    	Manifest manifest = jarFile.getManifest();
			Attributes attributes = manifest.getMainAttributes();
			mainClass = attributes.getValue(BOOT_CLASS);
		}
		
		if (mainClass == null) {
			// Still don't have one (default).  One final try: look for a jar file in a
			// main directory.  There should be only one, and it's manifest 
			// Main-Class attribute is the main class.  The JarClassLoader will take
			// care of finding it.
			InputStream is = Boot.class.getResourceAsStream("/" + MAIN_JAR);
			if (is != null) {
				JarInputStream jis = new JarInputStream(is);
				Manifest manifest = jis.getManifest();
				Attributes attributes = manifest.getMainAttributes();
				mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
			}
		}
	
		// Do we need to create a wrapping classloader?  Check for the
		// presence of a "wrap" directory at the top of the jar file.
		URL url = Boot.class.getResource(WRAP_JAR);
		
		if (url != null) {
			// Wrap class loaders.
			JarClassLoader bootLoader = new JarClassLoader(WRAP_DIR);
			bootLoader.setRecord(record);
			bootLoader.setFlatten(!jarnames);
			bootLoader.setRecording(recording);
			bootLoader.setInfo(info);
            bootLoader.setVerbose(verbose);
			bootLoader.load(null);
			
			// Read the "Wrap-Class-Loader" property from the wraploader jar file.
			// This is the class to use as a wrapping class-loader.
			JarInputStream jis = new JarInputStream(Boot.class.getResourceAsStream(WRAP_JAR));
			String wrapLoader = jis.getManifest().getMainAttributes().getValue(WRAP_CLASS_LOADER);
			if (wrapLoader == null) {
				WARNING(url + " did not contain a " + WRAP_CLASS_LOADER + " attribute, unable to load wrapping classloader");
			} else {
				INFO("using " + wrapLoader);
				Class jarLoaderClass = bootLoader.loadClass(wrapLoader);
				Constructor ctor = jarLoaderClass.getConstructor(new Class[]{ClassLoader.class});
				loader = (JarClassLoader)ctor.newInstance(new Object[]{bootLoader});
			}
				
		} else {
			INFO("using JarClassLoader");
			loader = new JarClassLoader(Boot.class.getClassLoader());
		}
		loader.setRecord(record);
		loader.setFlatten(!jarnames);
		loader.setRecording(recording);
        loader.setInfo(info);
		loader.setVerbose(verbose);
		mainClass = loader.load(mainClass);
        
        if (mainClass == null) throw new Exception("main class was not found (fix: add main/main.jar with a Main-Class manifest attribute, or specify -D" + MAIN_CLASS + ")");

    	// Guard against the main.jar pointing back to this
    	// class, and causing an infinite recursion.
        String bootClass = Boot.class.getName();
    	if (mainClass.equals(Boot.class.getName()))
    		throw new Exception("main class would cause infinite recursion: check main.jar/META-INF/MANIFEST.MF/Main-Class attribute: " + mainClass);
    	
		// Set the context classloader in case any classloaders delegate to it.
		// Otherwise it would default to the sun.misc.Launcher$AppClassLoader which
		// is used to launch the jar application, and attempts to load through
		// it would fail if that code is encapsulated inside the one-jar.
		Thread.currentThread().setContextClassLoader(loader);
        
    	Class cls = loader.loadClass(mainClass);
    	
    	Method main = cls.getMethod("main", new Class[]{String[].class}); 
    	main.invoke(null, new Object[]{args});
    }
}
