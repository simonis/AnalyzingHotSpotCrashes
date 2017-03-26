package org.simonis;

import java.lang.reflect.Field;
import java.util.Random;

import sun.misc.Unsafe;

public class Crash4 {

  final static Unsafe UNSAFE = getUnsafe();

  public static Unsafe getUnsafe () {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (Unsafe)field.get(null);
		} catch (Exception ex) {
			throw new RuntimeException("can't get Unsafe instance", ex);
		}
	}

  public static class X {
    boolean b = new Random() . nextBoolean();
    public boolean foo() {
      return b;
    }
  }

  public static class A {
    public static boolean crash(Object o) {
      if (o instanceof X) {
        return ((X)o).foo();
      }
      return true;
    }
  }

  public static void main(String[] args) {
    X x = new X();
    breakInstanceKlass(x);
    for (int i = 0; i < 20_000; i++) {
      A.crash(x);
    }
  }

  private static void breakInstanceKlass(Object o) {
    long ik = UNSAFE.getLong(o, 8L); // This only works for -XX:-UseCompressedOops
    System.out.format("InstanceKlass for %s is at 0x%016x\n", o.getClass().getName(), ik);
    for (int i = 4; i < 7; i++) {
      // this nulls the _secondary_super_cache up to _primary_supers fields in InstanceKlass
      UNSAFE.putLong(ik + i*8, 0);
    }
  }
}
