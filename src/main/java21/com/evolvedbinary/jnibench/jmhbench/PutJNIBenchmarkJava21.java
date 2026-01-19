package com.evolvedbinary.jnibench.jmhbench;

import com.evolvedbinary.jnibench.jmhbench.cache.MemorySegmentCache;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class PutJNIBenchmarkJava21 extends PutJNIBenchmark {
  private static final MethodHandle PUT_FROM_MEMORY_SEGMENT_HANDLE;

  static {
    // 1. Initialize the Linker and Lookup
    Linker linker = Linker.nativeLinker();
    SymbolLookup loaderLookup = SymbolLookup.loaderLookup();

    // 2. Find the symbol and create the Downcall Handle once
    PUT_FROM_MEMORY_SEGMENT_HANDLE = loaderLookup.find("putFromMemorySegment")
                                                 .map(symbol -> linker.downcallHandle(symbol,
                                                                                      FunctionDescriptor.of(
                                                                                          ValueLayout.JAVA_INT,
                                                                                          ValueLayout.ADDRESS,
                                                                                          ValueLayout.ADDRESS,
                                                                                          ValueLayout.JAVA_INT)))
                                                 .orElseThrow();

  }

  @State(Scope.Benchmark)
  public static class PutJNIBenchmarkStateJava21 extends PutJNIBenchmarkState {
    MemorySegment keyMemorySegment;
    private Arena benchmarkArena;

    @Setup
    public void setup() {
      super.setup();
      benchmarkArena = Arena.ofShared();
      keyMemorySegment = benchmarkArena.allocateArray(ValueLayout.JAVA_BYTE, keyBytes);
    }

    @TearDown
    public void tearDown() {
      if (benchmarkArena != null) {
        benchmarkArena.close();
      }
    }
  }

  public static class PutJNIThreadStateJava21 extends PutJNIThreadState {
    private final MemorySegmentCache memorySegmentCache = new MemorySegmentCache();

    @Setup
    public void setup(final PutJNIBenchmarkStateJava21 benchmarkState, final Blackhole blackhole) {
      if (isPutFromMemorySegmentJava21(benchmarkState)) {
        memorySegmentCache.setup(valueSize, cacheSize, benchmarkState.cacheEntryOverhead,
                                 benchmarkState.writePreparation, blackhole);
      } else {
        super.setup(benchmarkState, blackhole);
      }
    }

    private static boolean isPutFromMemorySegmentJava21(final PutJNIBenchmarkStateJava21 benchmarkState) {
      return "putFromMemorySegment".equals(benchmarkState.getCaller().benchmarkMethod);
    }

    @TearDown
    public void tearDown(final PutJNIBenchmarkStateJava21 benchmarkState) {
      if (isPutFromMemorySegmentJava21(benchmarkState)) {
        memorySegmentCache.tearDown();
      } else {
        super.tearDown(benchmarkState);
      }
    }
  }

  @Benchmark
  public void putFromMemorySegment(PutJNIBenchmarkStateJava21 benchmarkState, PutJNIThreadStateJava21 threadState,
                                   Blackhole blackhole) {
    final var segment = threadState.memorySegmentCache.acquire();
    threadState.memorySegmentCache.prepareBuffer(segment, benchmarkState.fillByte);

    try {
      final var size = (int) PUT_FROM_MEMORY_SEGMENT_HANDLE.invokeExact(
          benchmarkState.keyMemorySegment, // Pre-allocated segment for key
          segment,
          benchmarkState.valueSize
      );
      blackhole.consume(size);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }

    threadState.memorySegmentCache.release(segment);
  }
}
