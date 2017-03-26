package org.simonis;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class Crash {

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

  public static void crash(int x) {
    UNSAFE.putInt(0x99, x);
  }

  public static void main(String[] args) {
    crash(0x42);
  }
}
