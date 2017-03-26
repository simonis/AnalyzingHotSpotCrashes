package org.simonis;

public class Crash5 {

  public static class X {}
  public static class Y extends X {}

  public static class A {
    public static void crash(Object src) {
      System.arraycopy(src, 0, new X[1], 0, 1);
    }
  }

  public static void main(String[] args) {
    for (int i = 0; i < 20_000; i++) {
      A.crash(new Y[1]);
    }
  }
}
