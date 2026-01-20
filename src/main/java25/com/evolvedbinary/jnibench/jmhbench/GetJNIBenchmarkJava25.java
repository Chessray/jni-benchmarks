package com.evolvedbinary.jnibench.jmhbench;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class GetJNIBenchmarkJava25 extends GetJNIBenchmarkJavaFfm {
  @State(Scope.Benchmark)
  public static class GetJNIBenchmarkStateJava25 extends GetJNIBenchmarkStateJavaFfm {
    protected final MemorySegment allocateFromArena(byte[] keyBytes) {
      return getArena().allocateFrom(ValueLayout.JAVA_BYTE, keyBytes);
    }
  }
}
