package org.simonis;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class CrashOOM {

  static private class MyClassLoader extends ClassLoader {
    public Class<?> myDefineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
      return defineClass(name, b, off, len, null);
    }
  }

  private static byte[] getBytecodes(String myName) throws Exception {
    InputStream is = CrashOOM.class.getResourceAsStream(myName + ".class");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int len;
    while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
    buf = baos.toByteArray();
    System.out.println("sizeof(" + myName + ".class) == " + buf.length);
    return buf;
  }

  public static void crash(int count) throws Exception {
    byte[] buf = getBytecodes("CrashOOM");
    MyClassLoader cl = new MyClassLoader();
    for (int i = 0; i < count; i++) {
      try {
        cl.myDefineClass("org.simonis.CrashOOM", buf, 0, buf.length);
      }
      catch (LinkageError jle) {
        // Can only define once!
        if (i == 0) throw new Exception("Should succeed the first time.");
      }
    }
  }

  public static void main(String[] args) throws Exception {
    crash(args.length > 0 ? Integer.parseInt(args[0]) : 10);
  }
}
