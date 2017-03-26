package org.simonis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.sun.tools.attach.VirtualMachine;

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.memory.SystemDictionary.ClassVisitor;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.runtime.VM;

public class ReplayDemo {

	private void getID(CountDownLatch start, CountDownLatch stop) {
        String id = MyThread.aaaaaaaa();
        System.out.println(id);
        try {
            // Signal that we've entered the activation..
            start.countDown();
            //..and wait until we can leave it.
            stop.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(id);
        return;
    }

    private static class MyThread extends Thread {
        private ReplayDemo dc;
        private CountDownLatch start, stop;

        public MyThread(ReplayDemo dc, CountDownLatch start, CountDownLatch stop) {
            this.dc = dc;
            this.start = start;
            this.stop = stop;
        }

        public void run() {
            dc.getID(start, stop);
        }
    	public static String aaaaaaaa() {
    		return "AAAAAAAA";
    	}
    	public static String bbbbbbbb() {
    		return "BBBBBBBB";
    	}
    	public static String cccccccc() {
    		return "CCCCCCCC";
    	}
    	public static String dddddddd() {
    		return "DDDDDDDD";
    	}
    }

    private static Instrumentation instrumentation;

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("Loading Java Agent.");
        instrumentation = inst;
    }

    public static String getPID() {
        String self = ManagementFactory.getRuntimeMXBean().getName();
        return self.substring(0, self.indexOf('@'));
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
        VirtualMachine vm = VirtualMachine.attach(getPID());
        vm.loadAgent(jarFile.getAbsolutePath());
        vm.detach();
    }

    private static byte[] getBytecodes(String myName) throws Exception {
        InputStream is = ReplayDemo.class.getResourceAsStream(myName + ".class");
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

            String result = (String)mbserver.invoke(diagCmd,
            		"gcClassStats", new Object[] { null }, new String[] {String[].class.getName()});
            int count = 0;
            try (Scanner s = new Scanner(result)) {
                if (s.hasNextLine()) {
                    System.out.println(s.nextLine());
                }
                while (s.hasNextLine()) {
                    String l = s.nextLine();
                    if (l.endsWith(pattern)) {
                        count++;
                        System.out.println(l);
                    }
                }
            }
            return count;
        }
        catch (Exception e) {
            throw new RuntimeException("Test failed because we can't read the class statistics!", e);
        }
    }

    private static void printClassStats(String name, int expectedCount, boolean reportError) {
        int count = getClassStats(name);
        String res = "Should have " + expectedCount + " " + name +
                     " instances and we have: " + count;
        System.out.println(res);
        if (reportError && count != expectedCount) {
            throw new RuntimeException(res);
        }
    }

    private static final String myName = ReplayDemo.class.getName();
    private static String java = System.getProperty("java.home") + "/bin/java";
    private static String cp = System.getProperty("java.class.path");

    private static volatile String clientPID;

    public static void execute(String[] args, String[] vmargs, boolean waitFor) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File("/tmp"));
        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd, java, "-cp", cp);
        Collections.addAll(cmd, vmargs);
        cmd.add(myName);
        Collections.addAll(cmd, args);
    	pb.command(cmd);
    	Process p = pb.start();
    	p.getErrorStream().close();
    	p.getOutputStream().close();
    	BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
    	new Thread() {
    		public void run() {
    			try {
    				String l;
    				while ((l = br.readLine()) != null) {
    					String needle = "Our pid is = ";
    					if (l.contains(needle)) {
    						clientPID = l.substring(needle.length());
    						br.close();
    						break;
    					}
    					System.out.println(l);
    				}
    			} catch (IOException ioe) {
    				System.out.println(ioe);
    			}
    		}
    	} . start();
    	new Thread() {
    		public void run() {
    			try {
    		    	System.out.print(p.isAlive());
    				System.out.println("Process returned with: " + p.waitFor());
    			} catch (InterruptedException ie) {
    				System.out.println(ie);
    			}
    		}
    	} . start();
    	while (clientPID == null) {
    		Thread.sleep(100);
    	}
    	System.out.println("Client started with pid: " + clientPID);
    }

    private static final int ITERATIONS = 3;

    public static void main(String[] args) throws Exception {
        System.out.println("Our pid is = " + getPID());

        if (args.length == 0 || "redefineClass".equals(args[0])) {
            byte[] buf = getBytecodes(myName.substring(myName.lastIndexOf(".") + 1));
            //loadInstrumentationAgent(myName, buf);
            int index = getStringIndex("aaaaaaaa", buf);
            CountDownLatch stop = new CountDownLatch(1);

            Thread[] threads = new Thread[ITERATIONS];
            String[] newNames = new String[] {"bbbbbbbb", "cccccccc", "dddddddd"};
            for (int i = 0; i < ITERATIONS; i++) {
            	replaceString(buf, newNames[i % newNames.length], index);
                //instrumentation.redefineClasses(new ClassDefinition(ReplayDemo.class, buf));
                ReplayDemo dc = ReplayDemo.class.newInstance();
                CountDownLatch start = new CountDownLatch(1);
                (threads[i] = new MyThread(dc, start, stop)).start();
                start.await(); // Wait until the new thread entered the getID() method
            }
            // We expect to have one instance for each redefinition because they are all kept alive by an activation
            // plus the initial version which is kept active by this main method.
            //printClassStats(myName, ITERATIONS + 1, false);
            //System.in.read();
            Thread.sleep(10_000);
            stop.countDown(); // Let all threads leave the ReplayDemo.getID() activation..
            // ..and wait until really all of them returned from ReplayDemo.getID()
            for (int i = 0; i < ITERATIONS; i++) {
                threads[i].join();
            }
            System.gc();
            System.out.println("System.gc()");
            // After System.gc() we expect to remain with two instances: one is the initial version which is
            // kept alive by this main method and another one which is the latest redefined version.
            //printClassStats(myName, 2, true);
            System.exit(0);
        }
        else if ("execute".equals(args[0])) {
        	execute(Arrays.copyOfRange(args, 1, args.length),
        			new String[] {"-Xmx100m", "-XX:-UnlockDiagnosticVMOptions"}, false);

        	HotSpotAgent agent = new HotSpotAgent();
        	System.out.println("Attaching to: " + Integer.parseInt(clientPID));
            	agent.attach(Integer.parseInt(clientPID));
        	System.out.println("Attached  to: " + Integer.parseInt(clientPID));
        	VM vm = VM.getVM();
        	vm.getSystemDictionary().classesDo(new ClassVisitor() {
				@Override
				public void visit(Klass klass) {
					if (klass.getName().asString().contains(myName)) {
						System.out.println(klass);
					}
				}
			});
        }
        else if ("attach".equals(args[0])) {
        	int pid = Integer.parseInt(args[1]);
        	HotSpotAgent agent = new HotSpotAgent();
        	System.out.println("Attaching to: " + pid);
        	agent.attach(pid);
        	System.out.println("Attached  to: " + pid);
        	VM vm = VM.getVM();
        	vm.getSystemDictionary().classesDo(new ClassVisitor() {
				@Override
				public void visit(Klass klass) {
					if (klass.getName().asString().contains("ReplayDemo")) {
						System.out.println(klass.getName().asString() + " : " + klass);
					}
				}
			});
        	agent.detach();
        }
	}

}
