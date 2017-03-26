package org.simonis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import sun.misc.Unsafe;

import com.sun.tools.attach.VirtualMachine;

public class SADemo {

    private static Instrumentation instrumentation;

    private static Unsafe UNSAFE = getUnsafe();
    private static Unsafe getUnsafe() {
    	try {
    		Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
    		unsafeConstructor.setAccessible(true);
    		return unsafeConstructor.newInstance();
    	}
    	catch (Exception e) {
    		System.out.println(e);
    		return null;
    	}
    }

    public void getID(CountDownLatch stop) {
        String id = "AAAAAAAA";
        System.out.println(id);
        try {
            stop.await();
            //Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(id);
        return;
    }

    private static class MyThread extends Thread {
        private SADemo dc;
        private CountDownLatch stop;

        public MyThread(SADemo dc, CountDownLatch stop) {
            this.dc = dc;
            this.stop = stop;
        }

        public void run() {
            dc.getID(stop);
        }
    }

    private static class ParallelLoadingThread extends Thread {
        private MyParallelClassLoader pcl;
        private CountDownLatch stop;
        private byte[] buf;

        public ParallelLoadingThread(MyParallelClassLoader pcl, byte[] buf, CountDownLatch stop) {
            this.pcl = pcl;
            this.stop = stop;
            this.buf = buf;
        }

        public void run() {
            try {
                stop.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                @SuppressWarnings("unchecked")
                Class<SADemo> dc = (Class<SADemo>) pcl.myDefineClass(SADemo.class.getName(), buf, 0, buf.length);
            }
            catch (LinkageError jle) {
                // Expected with a parallel capable class loader and
                // -XX:+UnsyncloadClass or -XX:+AllowParallelDefineClass
                pcl.incrementLinkageErrors();
            }

        }
    }

    private static class ForNameThread extends Thread {
        private CountDownLatch stop;
        private String cl_name;
        private AtomicInteger errors;

        public ForNameThread(String cl_name, CountDownLatch stop, AtomicInteger errors) {
            this.cl_name = cl_name;
            this.stop = stop;
            this.errors = errors;
        }

        public void run() {
            try {
                stop.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                Class.forName(cl_name, true, null);
            }
            catch (LinkageError jle) {
                // Expected with a parallel capable class loader and
                // -XX:+UnsyncloadClass or -XX:+AllowParallelDefineClass
                errors.incrementAndGet();
            }
            catch (ClassNotFoundException cnfe) {
                throw new RuntimeException("Should not happen!");
            }

        }
    }

    static private class MyClassLoader extends ClassLoader {
        public Class<?> myDefineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
            return defineClass(name, b, off, len, null);
        }
    }

    static private class MyParallelClassLoader extends ClassLoader {
        static {
            System.out.println("parallelCapable : " + registerAsParallelCapable());
        }
        public Class<?> myDefineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
            return defineClass(name, b, off, len, null);
        }
        public void incrementLinkageErrors() {
            linkageErrors++;
        }
        public int getLinkageErrors() {
            return linkageErrors;
        }
        private int linkageErrors;
    }

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("Loading Java Agent.");
        instrumentation = inst;
    }


    private static void loadInstrumentationAgent(String myName, byte[] buf) throws Exception {
        // Create agent jar file on the fly
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        m.getMainAttributes().put(new Attributes.Name("Agent-Class"), myName);
        m.getMainAttributes().put(new Attributes.Name("Can-Redefine-Classes"), "true");
        File jarFile = File.createTempFile("agent", ".jar");
        jarFile.deleteOnExit();
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile), m);
        jar.putNextEntry(new JarEntry(myName.replace('.', '/') + ".class"));
        jar.write(buf);
        jar.close();
        //System.out.println(jarFile);
        String self = ManagementFactory.getRuntimeMXBean().getName();
        String pid = self.substring(0, self.indexOf('@'));
        System.out.println("Our pid is = " + pid);
        VirtualMachine vm = VirtualMachine.attach(pid);
        //System.out.println(jarFile.getAbsolutePath());
        vm.loadAgent(jarFile.getAbsolutePath());
    }

    private static byte[] getBytecodes(String myName) throws Exception {
        InputStream is = SADemo.class.getResourceAsStream(myName + ".class");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
        buf = baos.toByteArray();
        System.out.println("sizeof(" + myName + ".class) == " + buf.length);
        return buf;
    }

    private static int getStringIndex(String needle, byte[] buf) {
        return getStringIndex(needle, buf, 0);
    }

    private static int getStringIndex(String needle, byte[] buf, int offset) {
        outer:
        for (int i = offset; i < buf.length - offset - needle.length(); i++) {
            for (int j = 0; j < needle.length(); j++) {
                if (buf[i + j] != (byte)needle.charAt(j)) continue outer;
            }
            return i;
        }
        return 0;
    }

    private static void replaceString(byte[] buf, String name, int index) {
        for (int i = index; i < index + name.length(); i++) {
            buf[i] = (byte)name.charAt(i - index);
        }
    }

    private static MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();

    private static int getClassStats(String pattern) {
        try {
            ObjectName diagCmd = new ObjectName("com.sun.management:type=DiagnosticCommand");

            String result = (String)mbserver.invoke(diagCmd , "gcClassStats" , new Object[] { null }, new String[] {String[].class.getName()});
            Scanner s = new Scanner(result);
            if (s.hasNextLine()) {
                System.out.println(s.nextLine());
            }
            int count = 0;
            while (s.hasNextLine()) {
                String l = s.nextLine();
                if (l.endsWith(pattern)) {
                    count++;
                    System.out.println(l);
                }
            }
            s.close();
            return count;
        }
        catch (Exception e) {
            System.out.println(e);
            System.out.println(e.getCause());
            e.getCause().printStackTrace();
        }
        return 0;
    }

    private static String myName = SADemo.class.getName();
    private static String java = System.getProperty("java.home") + "/bin/java";
    private static String cp = System.getProperty("java.class.path");

    public static void execute(String[] args, String[] vmargs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File("/tmp"));
        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd, java, "-cp", cp);
        Collections.addAll(cmd, vmargs);
        cmd.add(myName);
        Collections.addAll(cmd, args);
    	pb.command(cmd);
    	pb.redirectErrorStream();
    	Process p = pb.start();
    	BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
    	String l;
    	while ((l = br.readLine()) != null) {
    		System.out.println(l);
    	}
    	System.out.println("Process returned with: " + p.waitFor());
    }

    public static void main(String[] args) throws Exception {
    	byte[] buf = getBytecodes(myName.substring(myName.lastIndexOf(".") + 1));

        if (args.length == 0 || "defineClass".equals(args[0])) {
            MyClassLoader cl = new MyClassLoader();
            for (int i = 0; i < (args.length > 1 ? Integer.parseInt(args[1]) : 10); i++) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<SADemo> dc = (Class<SADemo>) cl.myDefineClass(myName, buf, 0, buf.length);
                    //System.out.println(dc);
                }
                catch (LinkageError jle) {
                    // Can only define once!
                    if (i == 0) throw new Exception("Should succeed the first time.");
                }
            }
            int count = getClassStats("org.simonis.SADemo");
            // We expect to have two instances of SADemo here: the initial version in which we are
            // executing and one another version which was loaded into our own classloader 'MyClassLoader'.
            // All the subsequent attempts to reload SADemo into our 'MyClassLoader' should have failed.
            System.out.println("Should have 2 SADemo instances and we have: " + count);
            System.gc();
            System.out.println("System.gc()");
            count = getClassStats("org.simonis.SADemo");
            // At least after System.gc() the failed loading attempts should leave no instances around!
            System.out.println("Should have 2 SADemo instances and we have: " + count);
            UNSAFE.putInt(0, 0);
        }
        else if ("defineSystemClass".equals(args[0])) {
            MyClassLoader cl = new MyClassLoader();
            int index = getStringIndex("org/simonis/SADemo", buf);
            replaceString(buf, "java/simoni/SADemo", index);
            while ((index = getStringIndex("Lorg/simonis/SADemo;", buf, index + 1)) != 0) {
                replaceString(buf, "Ljava/simoni/SADemo;", index);
            }
            index = getStringIndex("org.simonis.SADemo", buf);
            replaceString(buf, "java.simoni.SADemo", index);

            for (int i = 0; i < (args.length > 1 ? Integer.parseInt(args[1]) : 10); i++) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<SADemo> dc = (Class<SADemo>) cl.myDefineClass(null, buf, 0, buf.length);
                    System.out.println(dc);
                }
                catch (java.lang.SecurityException jlse) {
                    // Expected
                }
            }
            int count = getClassStats("org.simonis.SADemo");
            // We expect to have two instances of SADemo here: the initial version in which we are
            // executing and one another version which was loaded into our own classloader 'MyClassLoader'.
            // All the subsequent attempts to reload SADemo into our 'MyClassLoader' should have failed.
            System.out.println("Should have 1 SADemo instances and we have: " + count);
            System.gc();
            System.out.println("System.gc()");
            count = getClassStats("org.simonis.SADemo");
            // At least after System.gc() the failed loading attempts should leave no instances around!
            System.out.println("Should have 1 SADemo instances and we have: " + count);
        }
        else if ("defineClassParallel".equals(args[0])) {
            MyParallelClassLoader pcl = new MyParallelClassLoader();
            CountDownLatch stop = new CountDownLatch(1);

            int length = args.length > 1 ? Integer.parseInt(args[1]) : 10;
            Thread[] threads = new Thread[length];
            for (int i = 0; i < length; i++) {
                (threads[i] = new ParallelLoadingThread(pcl, buf, stop)).start();
            }
            stop.countDown(); // start parallel class loading..
            // ..and wait until all threads loaded the class
            for (int i = 0; i < length; i++) {
                threads[i].join();
            }
            System.out.print("Counted " + pcl.getLinkageErrors() + " LinkageErrors ");
            System.out.println(pcl.getLinkageErrors() == 0 ?
                    "" : "(use -XX:+UnsyncloadClass and/or -XX:+AllowParallelDefineClass to avoid this)");
            System.gc();
            System.out.println("System.gc()");
            int count = getClassStats("org.simonis.SADemo");
            // After System.gc() we expect to remain with two instances: one is the initial version which is
            // kept alive by this main method and another one in the parallel class loader.
            System.out.println("Should have 2 SADemo instances and we have: " + count);
        }
        else if ("classForName".equals(args[0])) {
            CountDownLatch stop = new CountDownLatch(1);
            AtomicInteger errors = new AtomicInteger();

            int length = args.length > 1 ? Integer.parseInt(args[1]) : 10;
            Thread[] threads = new Thread[length];
            for (int i = 0; i < length; i++) {
                (threads[i] = new ForNameThread("java.lang.ProcessBuilder", stop, errors)).start();
            }
            stop.countDown(); // start parallel class loading..
            // ..and wait until all threads loaded the class
            for (int i = 0; i < length; i++) {
                threads[i].join();
            }
            System.out.print("Counted " + errors.get() + " LinkageErrors ");
            System.out.println(errors.get() == 0 ?
                    "" : "(use -XX:+UnsyncloadClass and/or -XX:+AllowParallelDefineClass to avoid this)");
            System.gc();
            System.out.println("System.gc()");
            int count = getClassStats("java.lang.ProcessBuilder");
            // After System.gc() we expect to remain with two instances: one is the initial version which is
            // kept alive by this main method and another one in the parallel class loader.
            System.out.println("Should have 2 SADemo$LazyLoad instances and we have: " + count);
        }
        else if ("redefineClass".equals(args[0])) {
            loadInstrumentationAgent(myName, buf);
            int index = getStringIndex("AAAAAAAA", buf);
            CountDownLatch stop = new CountDownLatch(1);

            int length = args.length > 1 ? Integer.parseInt(args[1]) : 10;
            Thread[] threads = new Thread[length];
            for (int i = 0; i < length; i++) {
                buf[index] = (byte) ('A' + i + 1); // Change string constant in getID() which is legal in redefinition
                instrumentation.redefineClasses(new ClassDefinition(SADemo.class, buf));
                SADemo dc = SADemo.class.newInstance();
                (threads[i] = new MyThread(dc, stop)).start();
                Thread.sleep(100); // Give the new thread a chance to start
            }
            int count = getClassStats("org.simonis.SADemo");
            // We expect to have one instance for each redefinition because they are all kept alive by an activation
            // plus the initial version which is kept active by this main method.
            System.out.println("Should have " + (length + 1) + " SADemo instances and we have: " + count);
            stop.countDown(); // let all threads leave the SADemo.getID() activation..
            // ..and wait until really all of them returned from SADemo.getID()
            for (int i = 0; i < length; i++) {
                threads[i].join();
            }
            System.gc();
            System.out.println("System.gc()");
            count = getClassStats("org.simonis.SADemo");
            // After System.gc() we expect to remain with two instances: one is the initial version which is
            // kept alive by this main methidf and another one which is the latest redefined version.
            System.out.println("Should have 2 SADemo instances and we have: " + count);
            System.in.read();
        }
        else if ("redefineClassWithError".equals(args[0])) {
            loadInstrumentationAgent(myName, buf);
            int index = getStringIndex("getID", buf);

            for (int i = 0; i < (args.length > 1 ? Integer.parseInt(args[1]) : 10); i++) {
                buf[index] = (byte) 'X'; // Change getID() to XetID() which is illegal in redefinition
                try {
                    instrumentation.redefineClasses(new ClassDefinition(SADemo.class, buf));
                }
                catch (UnsupportedOperationException uoe) {
                    // Expected
                }
            }
            int count = getClassStats("org.simonis.SADemo");
            // We expect just a single SADemo instance because failed redefinitions should
            // leave no garbage around.
            System.out.println("Should have 1 SADemo instances and we have: " + count);
            System.gc();
            System.out.println("System.gc()");
            count = getClassStats("org.simonis.SADemo");
            // At least after a System.gc() we should definitely stay with a single instance!
            System.out.println("Should have 1 SADemo instances and we have: " + count);
            //System.in.read();
        }
        else if ("localClasses".equals(args[0])) {
        	// From "Java in a Nutshell , 3rd ed., 3.11.5
        	IntHolder[] holders = new IntHolder[10];
        	for (int i = 0; i < 10; i++) {
        		final int fi = i;
        		// This implicitly creates a closure over 'fi'. 'MyIntHolder' actually has a
        		// constructor which takes 'fi' as argument and stores it in a private field.
        		class MyIntHolder implements IntHolder {
        			public int getValue() {
        				return fi;
        			}
        		}
        		holders[i] = new MyIntHolder();
        	}
        	System.out.println("Enclosing class: " + holders[0].getClass().getEnclosingClass());
        	System.out.println("Declaring class: " + holders[0].getClass().getDeclaringClass());
        	for (int i = 0; i < 10; i++) {
        		System.out.print(holders[i].getValue() + (i == 9 ? "\n" : ", "));
        	}
        }
        else if ("execute".equals(args[0])) {
        	execute(Arrays.copyOfRange(args, 1, args.length - 1),
        			new String[] {"-Xmx100m", "-XX:+UnlockDiagnosticVMOptions"});
        }
    }

    private interface IntHolder { public int getValue(); }
}
