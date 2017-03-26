package org.simonis;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.simonis.Crash5.Y;

import com.sun.tools.attach.VirtualMachine;

public class Crash6 {

  private static Instrumentation instrumentation;

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
    InputStream is = Crash6.class.getResourceAsStream(myName + ".class");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int len;
    while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
    buf = baos.toByteArray();
    System.out.println("sizeof(" + myName + ".class) == " + buf.length);
    return buf;
  }

  public static class X {}
  public static class Y extends X {}

  public static class A {
    public static void crash(Object src) {
      System.arraycopy(src, 0, new Y[1], 0, 1);
    }
  }

  public static void main(String[] args) throws Exception {
    byte[] buf = getBytecodes("Crash6");
    loadInstrumentationAgent("org.simonis.Crash6", buf);
    buf = getBytecodes("Crash6$A");
    for (int i = 0; i < buf.length; i++) {
      // Change type of locale 'dst' in A.crash() from 'Y[]' to 'X[]'
      if (buf[i] == 'Y') buf[i] = 'X';
    }
    buf[7] = 50; // Reset class file format to Java 6
    java.nio.file.Files.write((new File("/tmp/Crash6$A.class")).toPath(), buf);
    instrumentation.redefineClasses(new ClassDefinition(A.class, buf));

    for (int i = 0; i < 20_000; i++) {
      A.crash(new Y[1]);
    }
  }
}
