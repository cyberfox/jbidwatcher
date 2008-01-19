/*
 * Copyright (c) 2004-2007, P. Simon Tuffs (simon@simontuffs.com)
 * All rights reserved.
 *
 * See the full license at http://www.simontuffs.com/one-jar/one-jar-license.html
 * This license is also included in the distributions of this software
 * under doc/one-jar-license.txt
 */

/**
 * Many thanks to the following for their contributions to One-Jar:
 * 
 * Contributor: Christopher Ottley <xknight@users.sourceforge.net>
 * Contributor: Thijs Sujiten (www.semantica.nl)
 * Contributor: Gerold Friedmann
 */

package com.simontuffs.onejar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;

/**
 * Loads classes from pre-defined locations inside the jar file containing this
 * class.  Classes will be loaded from jar files contained in the following 
 * locations within the main jar file (on the classpath of the application 
 * actually, which when running with the "java -jar" command works out to be
 * the same thing).
 * <ul>
 * <li>
 *   /lib	Used to contain library jars.
 * </li>
 * <li>
 *   /main	Used to contain a default main jar.
 * </li>
 * </ul> 
 * @author simon@simontuffs.com (<a href="http://www.simontuffs.com">http://www.simontuffs.com</a>)
 */
public class JarClassLoader extends ClassLoader implements IProperties {
    
    public static final String DOT_CONFIRM = ".onejar.confirm";
    public final static String LIB_PREFIX = "lib/";
    public final static String BINLIB_PREFIX = "binlib/";
    public final static String MAIN_PREFIX = "main/";
    public final static String RECORDING = "recording";
    public final static String TMP = "tmp";
    public final static String UNPACK = "unpack";
    public final static String EXPAND = "One-Jar-Expand";
    public final static String EXPAND_TMP = "One-Jar-Expand-Tmp";
    public final static String EXPAND_DIR = "One-Jar-Expand-Dir";
    public final static String SHOW_EXPAND = "One-Jar-Show-Expand";
    public final static String CONFIRM_EXPAND = "One-Jar-Confirm-Expand";
    public final static String CLASS = ".class";
    
    public final static String NL = System.getProperty("line.separator");
    
    public final static String JAVA_PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
    
    protected String name;
    protected boolean noExpand, expanded;
    protected ClassLoader externalClassLoader;
    
    static {
        // Add our 'onejar:' protocol handler, but leave open the 
        // possibility of a subsequent class taking over the 
        // factory.  TODO: (how reasonable is this?)
        String handlerPackage = System.getProperty(JAVA_PROTOCOL_HANDLER);
        if (handlerPackage == null) handlerPackage = "";
        if (handlerPackage.length() > 0) handlerPackage = "|" + handlerPackage;
        handlerPackage = "com.simontuffs" + handlerPackage;
        System.setProperty(JAVA_PROTOCOL_HANDLER, handlerPackage);
        
    }
    
    protected String PREFIX() {
        return "JarClassLoader: ";
    }
    
    protected String NAME() {
        return (name != null? "'" + name + "' ": "");
    }
    
    protected void VERBOSE(String message) {
        if (verbose) System.out.println(PREFIX() + NAME() + message);
    }
    
    protected void WARNING(String message) {
        System.err.println(PREFIX() + "Warning: " + NAME() + message); 
    }
    
    protected void INFO(String message) {
        if (info) System.out.println(PREFIX() + "Info: " + NAME() + message);
    }
    
    protected void PRINTLN(String message) {
        System.out.println(message);
    }
    
    protected void PRINT(String message) {
        System.out.print(message);
    }
    
    // Synchronize for thread safety.  This is less important until we
    // start to do lazy loading, but it's a good idea anyway.
    protected Map byteCode = Collections.synchronizedMap(new HashMap());
    protected Map pdCache = Collections.synchronizedMap(new HashMap());
    protected Map binLibPath = Collections.synchronizedMap(new HashMap());
    protected Set jarNames = Collections.synchronizedSet(new HashSet());
    
    protected boolean record = false, flatten = false, unpackFindResource = false;
    protected boolean verbose = false, info = false;
    protected String recording = RECORDING;
    
    protected String jarName, mainJar, wrapDir;
    protected boolean delegateToParent;
    
    protected static class ByteCode {
		public ByteCode(String $name, String $original, ByteArrayOutputStream baos, String $codebase, Manifest $manifest) {
            name = $name;
            original = $original;
            bytes = baos.toByteArray();
            codebase = $codebase;
			manifest = $manifest;
        }
        public byte bytes[];
        public String name, original, codebase;
		public Manifest manifest;
    }
    
    
    /**
     * Create a non-delegating but jar-capable classloader for bootstrap
     * purposes.
     * @param $wrap  The directory in the archive from which to load a 
     * wrapping classloader.
     */
    public JarClassLoader(String $wrap) {
        wrapDir = $wrap;
        delegateToParent = wrapDir == null;
        init();
    }
    
    /**
     * The main constructor for the Jar-capable classloader.
     * @param $record	If true, the JarClassLoader will record all used classes
     * 					into a recording directory (called 'recording' by default)
     *				 	The name of each jar file will be used as a directory name
     *					for the recorded classes.
     * @param $flatten  Whether to flatten out the recorded classes (i.e. eliminate
     * 					the jar-file name from the recordings).
     * 
     * Example: Given the following layout of the one-jar.jar file
     * <pre>
     *    /
     *    /META-INF
     *    | MANIFEST.MF
     *    /com
     *      /simontuffs
     *        /onejar
     *          Boot.class
     *          JarClassLoader.class
     *    /main
     *        main.jar
     *        /com
     *          /main
     *            Main.class 
     *    /lib
     *        util.jar
     *          /com
     *            /util
     *              Util.clas
     * </pre>
     * The recording directory will look like this:
     * <ul>
     * <li>flatten=false</li>
     * <pre>
     *   /recording
     *     /main.jar
     *       /com
     *         /main
     *            Main.class
     *     /util.jar
     *       /com
     *         /util
     *            Util.class
     * </pre>
     *
     * <li>flatten = true</li>
     * <pre>
     *   /recording
     *     /com
     *       /main
     *          Main.class
     *       /util
     *          Util.class
     *   
     * </ul>
     * Flatten mode is intended for when you want to create a super-jar which can
     * be launched directly without using one-jar's launcher.  Run your application
     * under all possible scenarios to collect the actual classes which are loaded,
     * then jar them all up, and point to the main class with a "Main-Class" entry
     * in the manifest.  
     *       
     */
    public JarClassLoader(ClassLoader parent) {
        super(parent);
        delegateToParent = true;
        init();
        // System.out.println(PREFIX() + this + " parent=" + parent + " loaded by " + this.getClass().getClassLoader());
    }
    
    /**
     * Common initialization code: establishes a classloader for delegation
     * to one-jar.class.path resources.
     */
    protected void init() {
        String classpath = System.getProperty(Boot.P_ONE_JAR_CLASS_PATH);
        if (classpath != null) {
            String tokens[] = classpath.split("\\" + Boot.P_PATH_SEPARATOR);
            List list = new ArrayList();
            for (int i=0; i<tokens.length; i++) {
                String path = tokens[i];
                try {
                    list.add(new URL(path));
                } catch (MalformedURLException mux) {
                    // Try a file:/// prefix and an absolute path.
                    try {
                        list.add(new URL("file:///" + new File(path).getAbsolutePath()));
                    } catch (MalformedURLException ignore) {
                        Boot.WARNING("Unable to parse external path: " + path);
                    }
                }
            }
            URL urls[] = (URL[])list.toArray(new URL[0]);
            Boot.INFO("external URLs=" + Arrays.asList(urls));
            externalClassLoader = new URLClassLoader(urls);
        }
    }
    
    public String load(String mainClass) {
        // Hack: if there is a one-jar.jarname property, use it.
        String jarname = Boot.getMyJarPath();
        return load(mainClass, jarname);
    }
    
    public String load(String mainClass, String jarName) {
        if (record) {
            new File(recording).mkdirs();
        }
        try {
            if (jarName == null) {
                jarName = Boot.getMyJarPath();
            }
            JarFile jarFile = new JarFile(jarName);
            Enumeration _enum = jarFile.entries();
            Manifest manifest = jarFile.getManifest();
            String expandPaths[] = null;
            // TODO: Allow a destination directory (relative or absolute) to 
            // be specified like this:
            // One-Jar-Expand: build=../expanded
            String expand = manifest.getMainAttributes().getValue(EXPAND);
            String expandtmp = manifest.getMainAttributes().getValue(EXPAND_TMP);
            String expanddir = System.getProperty(Boot.P_EXPAND_DIR);
            if (expanddir == null) expanddir = manifest.getMainAttributes().getValue(EXPAND_DIR);
            boolean shouldExpand = true;
            File tmpdir = new File(".");
            if (expandtmp != null && Boolean.valueOf(expandtmp).booleanValue()) {
                if (expanddir != null) {
                    tmpdir = new File(expanddir);
                } else {
                    File tmpfile = File.createTempFile("one-jar", ".tmp");
                    tmpfile.deleteOnExit();
                    tmpdir = new File(tmpfile.getParentFile() + "/" + new File(jarName).getName() + "/expand");
                }
            }
            if (noExpand == false && expand != null) {
                expanded = true;
                VERBOSE(EXPAND + "=" + expand);
                expandPaths = expand.split(",");
                boolean getconfirm = Boolean.TRUE.toString().equals(manifest.getMainAttributes().getValue(CONFIRM_EXPAND));
                if (getconfirm) {
                    String answer = getConfirmation(tmpdir);
                    if (answer == null) answer = "n";
                    answer = answer.trim().toLowerCase();
                    if (answer.startsWith("q")) {
                        PRINTLN("exiting without expansion.");
                        // Indicate (expected) failure with a non-zero return code.
                        System.exit(1);
                    } else if (answer.startsWith("n")) {
                        shouldExpand = false;
                    }
                }
            }
            boolean showexpand = Boolean.TRUE.toString().equals(manifest.getMainAttributes().getValue(SHOW_EXPAND));
            if (showexpand) {
                PRINTLN("Expanding to: " + tmpdir.getAbsolutePath());
            }
            while (_enum.hasMoreElements()) {
                JarEntry entry = (JarEntry)_enum.nextElement();
                if (entry.isDirectory()) continue;
                
                // The META-INF/MANIFEST.MF file can contain a property which names
                // directories in the JAR to be expanded (comma separated). For example:
                // One-Jar-Expand: build,tmp,webapps
                String name = entry.getName();
                if (expandPaths != null) {
                    // TODO: Can't think of a better way to do this right now.  
                    // This code really doesn't need to be optimized anyway.
                    if (shouldExpand && shouldExpand(expandPaths, name)) {
                        File dest = new File(tmpdir, name);
                        // Override if ZIP file is newer than existing.
                        if (!dest.exists() || dest.lastModified() < entry.getTime()) {
                            String msg = "Expanding:  " + name;
                            if (showexpand) {
                                PRINTLN(msg);
                            } else {
                                INFO(msg);
                            }
                            if (dest.exists()) INFO("Update because lastModified=" + new Date(dest.lastModified()) + ", entry=" + new Date(entry.getTime()));
                            File parent = dest.getParentFile();
                            if (parent != null) {
                                parent.mkdirs();
                            }
                            VERBOSE("using jarFile.getInputStream(" + entry + ")");
                            InputStream is = jarFile.getInputStream(entry);
                            FileOutputStream os = new FileOutputStream(dest); 
                            copy(is, os);
                            is.close();
                            os.close();
                        } else {
                            String msg = "Up-to-date: " + name;
                            if (showexpand) {
                                PRINTLN(msg);
                            } else {
                                VERBOSE(msg);
                            }
                        }
                    }
                }
                
                String jar = entry.getName();
                if (wrapDir != null && jar.startsWith(wrapDir) || jar.startsWith(LIB_PREFIX) || jar.startsWith(MAIN_PREFIX)) {
                    if (wrapDir != null && !entry.getName().startsWith(wrapDir)) continue;
                    // Load it! 
                    INFO("caching " + jar);
                    VERBOSE("using jarFile.getInputStream(" + entry + ")");
                    {
                        // Note: loadByteCode consumes the input stream, so make sure its scope
                        // does not extend beyond here.
                        InputStream is = jarFile.getInputStream(entry);
                        if (is == null) 
                            throw new IOException("Unable to load resource /" + jar + " using " + this);
						loadByteCode(is, jar, manifest);
                    }
                    
                    // Do we need to look for a main class?
                    if (jar.startsWith(MAIN_PREFIX)) {
                        if (mainClass == null) {
                            JarInputStream jis = new JarInputStream(jarFile.getInputStream(entry));
                            Manifest m = jis.getManifest();
                            jis.close();
                            // Is this a jar file with a manifest?
                            if (m != null) {
                                mainClass = jis.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                                mainJar = jar;
                            }
                        } else if (mainJar != null) {
                            WARNING("A main class is defined in multiple jar files inside " + MAIN_PREFIX + mainJar + " and " + jar);
                            WARNING("The main class " + mainClass + " from " + mainJar + " will be used");
                        }
                    } 
                } else if (wrapDir == null && name.startsWith(UNPACK)) {
                    // Unpack into a temporary directory which is on the classpath of
                    // the application classloader.  Badly designed code which relies on the
                    // application classloader can be made to work in this way.
                    InputStream is = this.getClass().getResourceAsStream("/" + jar);
                    if (is == null) throw new IOException(jar);
                    // Make a sentinel.
                    File dir = new File(TMP);
                    File sentinel = new File(dir, jar.replace('/', '.'));
                    if (!sentinel.exists()) {
                        INFO("unpacking " + jar + " into " + dir.getCanonicalPath());
						loadByteCode(is, jar, TMP, manifest);
                        sentinel.getParentFile().mkdirs();
                        sentinel.createNewFile();
                    }
                } else if (name.endsWith(CLASS)) {
                    // A plain vanilla class file rooted at the top of the jar file.
					loadBytes(entry, jarFile.getInputStream(entry), "/", null, manifest);
                } else {
                    // A resource? 
                   INFO("resource: " + jarFile.getName() + "!/" + entry.getName());
                }
            }
            // If mainClass is still not defined, return null.  The caller is then responsible
            // for determining a main class.
            
        } catch (IOException iox) {
            System.err.println("Unable to load resource: " + iox);
            iox.printStackTrace(System.err);
        }
        return mainClass;
    }

    public static boolean shouldExpand(String expandPaths[], String name) {
        for (int i=0; i<expandPaths.length; i++) {
            if (name.startsWith(expandPaths[i])) return true;
        }
        return false;
    }        

	protected void loadByteCode(InputStream is, String jar, Manifest man) throws IOException {
		loadByteCode(is, jar, null, man);
    }
    
	protected void loadByteCode(InputStream is, String jar, String tmp, Manifest man) throws IOException {
        JarInputStream jis = new JarInputStream(is);
        JarEntry entry = null;
        // TODO: implement lazy loading of bytecode.
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.isDirectory()) continue;
			loadBytes(entry, jis, jar, tmp, man);
        }
    }
    
	protected void loadBytes(JarEntry entry, InputStream is, String jar, String tmp, Manifest man) throws IOException {
        String entryName = entry.getName().replace('/', '.');
        int index = entryName.lastIndexOf('.');
        String type = entryName.substring(index+1);
        
        // agattung: patch (for one-jar 0.95)
        // add package handling to avoid NullPointer exceptions
        // after calls to getPackage method of this ClassLoader
        int index2 = entryName.lastIndexOf('.', index-1);
        if (index2 > -1) {
            String packageName = entryName.substring(0, index2);
            if (getPackage(packageName) == null) {
                definePackage(packageName, "", "", "", "", "", "", null);
            }
        }
        // end patch
        
        // Because we are doing stream processing, we don't know what
        // the size of the entries is.  So we store them dynamically.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(is, baos);
        
        if (tmp != null) {
            // Unpack into a temporary working directory which is on the classpath.
            File file = new File(tmp, entry.getName());
            file.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(baos.toByteArray());
            fos.close();
            
        } else {
            // If entry is a class, check to see that it hasn't been defined
            // already.  Class names must be unique within a classloader because
            // they are cached inside the VM until the classloader is released.
            if (type.equals("class")) {
                if (alreadyCached(entryName, jar, baos)) return;
				byteCode.put(entryName, new ByteCode(entryName, entry.getName(), baos, jar, man));
                VERBOSE("cached bytes for class " + entryName);
            } else {
                // Another kind of resource.  Cache this by name, and also prefixed
                // by the jar name.  Don't duplicate the bytes.  This allows us
                // to map resource lookups to either jar-local, or globally defined.
                String localname = jar + "/" + entryName;
				byteCode.put(localname, new ByteCode(localname, entry.getName(), baos, jar, man));
                // Keep a set of jar names so we can do multiple-resource lookup by name
                // as in findResources().
                jarNames.add(jar);
                VERBOSE("cached bytes for local name " + localname);
                // Only keep the first non-local entry: this is like classpath where the first
                // to define wins.  
                if (alreadyCached(entryName, jar, baos)) return;

                byteCode.put(entryName, new ByteCode(entryName, entry.getName(), baos, jar, man));
                VERBOSE("cached bytes for entry name " + entryName);
                
            }
        }
    }
    
    protected boolean classPool = false;
    
    /**
     * Locate the named class in a jar-file, contained inside the
     * jar file which was used to load <u>this</u> class.
     */
    protected Class findClass(String name) throws ClassNotFoundException {
        // Delegate to external paths first
        Class cls = null;
        if (externalClassLoader != null) {
            try {
            return externalClassLoader.loadClass(name);
            } catch (ClassNotFoundException cnfx) {
                // continue...
            }
        }

        // Make sure not to load duplicate classes.
        cls = findLoadedClass(name);
        if (cls != null) return cls;
        
        // Look up the class in the byte codes.
        // Translate path?
        VERBOSE("findClass(" + name + ")");
        String cache = name + CLASS;
        ByteCode bytecode = (ByteCode)byteCode.get(cache);
        if (bytecode != null) {
            VERBOSE("found " + name + " in codebase '" + bytecode.codebase + "'");
            if (record) {
                record(bytecode);
            }
            // Use a protectionDomain to associate the codebase with the
            // class.
            ProtectionDomain pd = (ProtectionDomain)pdCache.get(bytecode.codebase);
            if (pd == null) {
                ProtectionDomain cd = JarClassLoader.class.getProtectionDomain();
                URL url = cd.getCodeSource().getLocation();
                try {
                    url = new URL("jar:" + url + "!/" + bytecode.codebase);
                } catch (MalformedURLException mux) {
                    mux.printStackTrace(System.out);    			
                }
                
                CodeSource source = new CodeSource(url, (Certificate[])null);
                pd = new ProtectionDomain(source, null, this, null);
                pdCache.put(bytecode.codebase, pd);
            }
            
            // Do it the simple way.
            byte bytes[] = bytecode.bytes;
			
			int i = name.lastIndexOf('.');
			if (i != -1) {
				String pkgname = name.substring(0, i);
				// Check if package already loaded.
				Package pkg = getPackage(pkgname);
				Manifest man = bytecode.manifest;
				if (pkg != null) {
					// Package found, so check package sealing.
					if (pkg.isSealed()) {
						// Verify that code source URL is the same.
						if (!pkg.isSealed()) {
							throw new SecurityException("sealing violation: package " + pkgname + " is sealed");
						}

					} else {
						// Make sure we are not attempting to seal the package
						// at this code source URL.
						if ((man != null) && isSealed(pkgname, man)) {
							throw new SecurityException("sealing violation: can't seal package " + pkgname + ": already loaded");
						}
					}
				} else {
					if (man != null) {
						definePackage(pkgname, man);
					} else {
						definePackage(pkgname, null, null, null, null, null, null, null);
					}
				}
			}
			
            return defineClass(name, bytes, pd);
        }
        VERBOSE(name + " not found");
        throw new ClassNotFoundException(name);
        
    }
    
    private boolean isSealed(String name, Manifest man) {
		String path = name.replace('.', '/').concat("/");
		Attributes attr = man.getAttributes(path);
		String sealed = null;
		if (attr != null) {
			sealed = attr.getValue(Name.SEALED);
		}
		if (sealed == null) {
			if ((attr = man.getMainAttributes()) != null) {
				sealed = attr.getValue(Name.SEALED);
			}
		}
		return "true".equalsIgnoreCase(sealed);
	}

    /**
	 * Defines a new package by name in this ClassLoader. The attributes
	 * contained in the specified Manifest will be used to obtain package
	 * version and sealing information. For sealed packages, the additional URL
	 * specifies the code source URL from which the package was loaded.
	 * 
	 * @param name
	 *            the package name
	 * @param man
	 *            the Manifest containing package version and sealing
	 *            information
	 * @param url
	 *            the code source url for the package, or null if none
	 * @exception IllegalArgumentException
	 *                if the package name duplicates an existing package either
	 *                in this class loader or one of its ancestors
	 * @return the newly defined Package object
	 */
	protected Package definePackage(String name, Manifest man) throws IllegalArgumentException {
		String path = name.replace('.', '/').concat("/");
		String specTitle = null, specVersion = null, specVendor = null;
		String implTitle = null, implVersion = null, implVendor = null;
		String sealed = null;
		URL sealBase = null;

		Attributes attr = man.getAttributes(path);
		if (attr != null) {
			specTitle = attr.getValue(Name.SPECIFICATION_TITLE);
			specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
			specVendor = attr.getValue(Name.SPECIFICATION_VENDOR);
			implTitle = attr.getValue(Name.IMPLEMENTATION_TITLE);
			implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
			implVendor = attr.getValue(Name.IMPLEMENTATION_VENDOR);
			sealed = attr.getValue(Name.SEALED);
		}
		attr = man.getMainAttributes();
		if (attr != null) {
			if (specTitle == null) {
				specTitle = attr.getValue(Name.SPECIFICATION_TITLE);
			}
			if (specVersion == null) {
				specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
			}
			if (specVendor == null) {
				specVendor = attr.getValue(Name.SPECIFICATION_VENDOR);
			}
			if (implTitle == null) {
				implTitle = attr.getValue(Name.IMPLEMENTATION_TITLE);
			}
			if (implVersion == null) {
				implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
			}
			if (implVendor == null) {
				implVendor = attr.getValue(Name.IMPLEMENTATION_VENDOR);
			}
			if (sealed == null) {
				sealed = attr.getValue(Name.SEALED);
			}
		}
        if (sealed != null) {
            try {
                sealBase = new URL(sealed);
            } catch (MalformedURLException mux) {
                // Would use IllegalArgumentException, but it don't have the chained constructor.
                throw new RuntimeException("Error in " + Name.SEALED + " manifest attribute: " + sealed, mux);
            }
        }
		return definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
	}
	
    protected Class defineClass(String name, byte[] bytes, ProtectionDomain pd) throws ClassFormatError {
        // Simple, non wrapped class definition.
        return defineClass(name, bytes, 0, bytes.length, pd);
    }
    
    protected void record(ByteCode bytecode) {
        String fileName = bytecode.original;
        // Write out into the record directory.
        File dir = new File(recording, flatten? "": bytecode.codebase);
        File file = new File(dir, fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            VERBOSE("" + file);
            try {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bytecode.bytes);
                fos.close();
                
            } catch (IOException iox) {
                System.err.println(PREFIX() + "unable to record " + file + ": " + iox);
            }
            
        }
    }
    
    /**
     * Overriden to return resources from the appropriate codebase.
     * There are basically two ways this method will be called: most commonly
     * it will be called through the class of an object which wishes to 
     * load a resource, i.e. this.getClass().getResourceAsStream().  Before
     * passing the call to us, java.lang.Class mangles the name.  It 
     * converts a file path such as foo/bar/Class.class into a name like foo.bar.Class, 
     * and it strips leading '/' characters e.g. converting '/foo' to 'foo'.
     * All of which is a nuisance, since we wish to do a lookup on the original
     * name of the resource as present in the One-Jar jar files.  
     * The other way is more direct, i.e. this.getClass().getClassLoader().getResourceAsStream().
     * Then we get the name unmangled, and can deal with it directly. 
     *
     * The problem is this: if one resource is called /foo/bar/data, and another 
     * resource is called /foo.bar.data, both will have the same mangled name, 
     * namely 'foo.bar.data' and only one of them will be visible.  Perhaps the
     * best way to deal with this is to store the lookup names in mangled form, and
     * simply issue warnings if collisions occur.  This is not very satisfactory,
     * but is consistent with the somewhat limiting design of the resource name mapping
     * strategy in Java today.
     */
    public InputStream getByteStream(String resource) {
        
        InputStream result = null;
        // Look up without resolving first.  This allows jar-local 
        // resolution to take place.
        ByteCode bytecode = (ByteCode)byteCode.get(resource);
        if (bytecode == null) {
            // Try again with a resolved name.
            bytecode = (ByteCode)byteCode.get(resolve(resource));
        }
        if (bytecode != null) result = new ByteArrayInputStream(bytecode.bytes);
        // Special case: if we are a wrapping classloader, look up to our
        // parent codebase.  Logic is that the boot JarLoader will have 
        // delegateToParent = false, the wrapping classloader will have 
        // delegateToParent = true;
        if (result == null && delegateToParent) {
            result = ((JarClassLoader)getParent()).getByteStream(resource);
        }
        VERBOSE("getByteStream(" + resource + ") -> " + result);
        return result;
    }
    
    /**
     * Resolve a resource name.  Look first in jar-relative, then in global scope.
     * @param resource
     * @return
     */
    protected String resolve(String $resource) {
        
        if ($resource.startsWith("/")) $resource = $resource.substring(1);
        $resource = $resource.replace('/', '.');
        String resource = null;
        String caller = getCaller();
        ByteCode callerCode = (ByteCode)byteCode.get(caller + ".class");
        
        if (callerCode != null) {
            // Jar-local first, then global.
            String tmp = callerCode.codebase + "/" + $resource;
            if (byteCode.get(tmp) != null) {
                resource = tmp; 
            } 
        }
        if (resource == null) {
            // One last try.
            if (byteCode.get($resource) == null) {
                resource = null; 
            } else {
                resource = $resource;
            }
        }
        VERBOSE("resource " + $resource + " resolved to " + resource);
        return resource;
    }
    
    protected boolean alreadyCached(String name, String jar, ByteArrayOutputStream baos) {
        // TODO: check resource map to see how we will map requests for this
        // resource from this jar file.  Only a conflict if we are using a
        // global map and the resource is defined by more than
        // one jar file (default is to map to local jar).
        byte[] bytes = baos.toByteArray();
        ByteCode existing = (ByteCode)byteCode.get(name);
        if (existing != null) {
            // If bytecodes are identical, no real problem.  Likewise if it's in
            // META-INF.
            if (!Arrays.equals(existing.bytes, bytes) && !name.startsWith("/META-INF")) {
                INFO(existing.name + " in " + jar + " is hidden by " + existing.codebase + " (with different bytecode)");
            } else {
                VERBOSE(existing.name + " in " + jar + " is hidden by " + existing.codebase + " (with same bytecode)");
            }
            return true;
        }
        return false;
    }
    
    
    protected String getCaller() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        // Search upward until we get to a known class, i.e. one with a non-null
        // codebase.
        String caller = null;
        for (int i=0; i<stack.length; i++) {
            if (byteCode.get(stack[i].getClassName() + ".class") != null) {
                caller = stack[i].getClassName();
                break;
            }
        }
        return caller;
    }
    
    /**
     * Sets the name of the used  classes recording directory.
     * 
     * @param $recording A value of "" will use the current working directory 
     * (not recommended).  A value of 'null' will use the default directory, which
     * is called 'recording' under the launch directory (recommended).
     */
    public void setRecording(String $recording) {
        recording = $recording;
        if (recording == null) recording = RECORDING;
    }
    
    public String getRecording() {
        return recording;
    }
    
    public void setRecord(boolean $record) {
        record = $record;
    }
    public boolean getRecord() {
        return record;
    }
    
    public void setFlatten(boolean $flatten) {
        flatten = $flatten;
    }
    public boolean isFlatten() {
        return flatten;
    }
    
    public void setVerbose(boolean $verbose) {
        verbose = $verbose;
        if (verbose) info = true;
    }
    
    public boolean getVerbose() {
        return verbose;
    }
    
    public void setInfo(boolean $info) {
        info = $info;
    }
    public boolean getInfo() {
        return info;
    }
    
    /* (non-Javadoc)
     * @see java.lang.ClassLoader#findResource(java.lang.String)
     */
    protected URL findResource(String $resource) {
        try {
            INFO("findResource(" + $resource + ")");
            // Do we have the named resource in our cache?  If so, construct a 
            // 'onejar:' URL so that a later attempt to access the resource
            // will be redirected to our Handler class, and thence to this class.
            String resource = resolve($resource);
            if (resource != null) {
                // We know how to handle it.
                INFO("findResource() found: " + $resource);
                return new URL(Handler.PROTOCOL + ":" + resource); 
            }
            INFO("findResource(): unable to locate " + $resource);
            // If all else fails, return null.
            return null;
        } catch (MalformedURLException mux) {
            WARNING("unable to locate " + $resource + " due to " + mux);
        }
        return null;
        
    }
    
    public Enumeration findResources(String name) throws IOException {
        INFO("findResources(" + name + ")");
        INFO("findResources: looking in " + jarNames);
        Iterator iter = jarNames.iterator();
        final List resources = new ArrayList();
        // Mangle name to match our dot separated format.  This still 
        // seems a bit flakey to me.  TODO: revisit.
        name = name.replace('/', '.');
        while (iter.hasNext()) {
            String resource = iter.next().toString() + "/" + name;
            if (byteCode.containsKey(resource)) {
                resources.add(new URL(Handler.PROTOCOL + ":" + resource));
            }
        }
        final Iterator ri = resources.iterator();
        return new Enumeration() {
            public boolean hasMoreElements() {
                return ri.hasNext();
            }
            public Object nextElement() {
                return ri.next();
            }
        };
    }
    
    /**
     * Utility to assist with copying InputStream to OutputStream.  All
     * bytes are copied, but both streams are left open.
     * @param in Source of bytes to copy.
     * @param out Destination of bytes to copy.
     * @throws IOException
     */
    protected void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024];
        while (true) {
            int len = in.read(buf);
            if (len < 0) break;
            out.write(buf, 0, len);
        }
    }
    
    public String toString() {
        return super.toString() + (name != null? "(" + name + ")": "");
    }
    
    /**
     * Returns name of the classloader.
     * @return
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets name of the classloader.  Default is null.
     * @param string
     */
    public void setName(String string) {
        name = string;
    }
    
    public void setExpand(boolean expand) {
        noExpand = !expand;
    }
    
    public boolean isExpanded() {
        return expanded;
    }
    
    /**
     * If the system specific library exists in the JAR, expand it and return the path
     * to the expanded library to the caller. Otherwise return null so the caller
     * searches the java.library.path for the requested library.
     *
     *
     * @author Christopher Ottley
     * @param name the (system specific) name of the requested library
     * @return the full pathname to the requested library, or null
     * @see Runtime#loadLibrary()
     * @since 1.2
     */
    protected String findLibrary(String name) {
        String result = null; // By default, search the java.library.path for it
        
        String resourcePath = BINLIB_PREFIX + System.mapLibraryName(name);
        
        // If it isn't in the map, try to expand to temp and return the full path
        // otherwise, remain null so the java.library.path is searched.
        
        // If it has been expanded already and in the map, return the expanded value
        if (binLibPath.get(resourcePath) != null) {
            result = (String)binLibPath.get(resourcePath);
        } else {
            
            // See if it's a resource in the JAR that can be extracted
            try {
                int lastdot = resourcePath.lastIndexOf('.');
                String suffix = null;
                if (lastdot >= 0) {
                    suffix = resourcePath.substring(lastdot);
                }
                InputStream is = this.getClass().getResourceAsStream("/" + resourcePath);
                File tempNativeLib = File.createTempFile(name + "-", suffix);
                FileOutputStream os = new FileOutputStream(tempNativeLib);
                copy(is, os);
                os.close();
                
                VERBOSE("Stored native library " + name + " at " + tempNativeLib);
                
                tempNativeLib.deleteOnExit();
                
                binLibPath.put(resourcePath, tempNativeLib.getPath());
                
                result = tempNativeLib.getPath();
                if (result != null) {
                    VERBOSE("Found " + result + " in native binary library " + resourcePath);
                }
            } catch(Throwable e)  {
                // Couldn't load the library
                // Return null by default to search the java.library.path
                WARNING("Unable to load native library: " + e);
            }
            
        }
        
        return result;
    }

    protected String getConfirmation(File location) throws IOException {
        File dotconfirm = new File(location, DOT_CONFIRM);
        String answer = "";
        if (dotconfirm.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(dotconfirm));
            answer = br.readLine();
            br.close();
            PRINTLN("Previous confirmation for file expansion (" + answer + ") was read from " + dotconfirm);
            return answer;
        }
        while (answer == null || (!answer.startsWith("n") && !answer.startsWith("y") && !answer.startsWith("q"))) {
            promptForConfirm(location);
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            answer = br.readLine();
            br.close();
        }
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(dotconfirm));
            bw.write(answer + NL);
            bw.close();
            PRINTLN("Your response has been stored in " + dotconfirm.getAbsolutePath() + ".  Please remove this file if you wish to change your mind.");
        } catch (IOException iox) {
            WARNING("Unable to store confirmation response in " + dotconfirm.getAbsolutePath() + ": " + iox);
        }
        return answer;
    }
    
    protected void promptForConfirm(File location) {
        PRINTLN("Do you want to allow '" + Boot.getMyJarName() + "' to expand files into the file-system at the following location?");
        PRINTLN("  " + location);
        PRINT("Answer y(es) to expand files, n(o) to continue without expanding, or q(uit) to exit: ");
    }
    
}
