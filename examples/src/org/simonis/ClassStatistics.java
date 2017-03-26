package org.simonis;

import sun.jvm.hotspot.classfile.ClassLoaderData;
import sun.jvm.hotspot.classfile.ClassLoaderDataGraph;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

public class ClassStatistics  extends Tool {

  @Override
  public void run() {
    ClassLoaderDataGraph cldg = VM.getVM().getClassLoaderDataGraph();
    for (ClassLoaderData cld = cldg.getClassLoaderGraphHead(); cld != null; cld = cld.next()) {
      for (Klass k = cld.getKlasses(); k != null; k = k.getNextLinkKlass()) {
        if (k instanceof InstanceKlass) {
          InstanceKlass ik = (InstanceKlass)k;
          Oop cl = ik.getClassLoader();
          System.out.println(k.getName().asString() + " (" +
              (cl == null ? "null" : cl.getKlass().getName().asString()) + ")");
        }
      }
    }
  }

  public static void main(String[] args) {
    ClassStatistics cs = new ClassStatistics();
    cs.execute(args);
  }
}
