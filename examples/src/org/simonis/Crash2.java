package org.simonis;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class Crash2 {

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

  public static boolean crash2(String s) {
    return s instanceof String;
  }

  public static void main(String[] args) {
    String s = "JBreak";
    UNSAFE.putLong(s, 8L, 0xffffffff);
    crash2(s);
  }
}
