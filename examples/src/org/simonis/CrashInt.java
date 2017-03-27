package org.simonis;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class CrashInt {
  int[] ia = new int[1];

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

  public int crash() {
    return ia.length;
  }

  public static void main(String[] args) {
    CrashInt ci = new CrashInt();
    UNSAFE.putLong(ci, 12L, 0xbadbabe);
    ci.crash();
  }
}
