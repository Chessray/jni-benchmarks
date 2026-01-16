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

public class GetJNIBenchmarkJava25 extends GetJNIBenchmark {
  private static final MethodHandle GET_INTO_MEMORY_SEGMENT_HANDLE;

  static {
    // 1. Initialize the Linker and Lookup
    Linker linker = Linker.nativeLinker();
    SymbolLookup loaderLookup = SymbolLookup.loaderLookup();

    // 2. Find the symbol and create the Downcall Handle once
    GET_INTO_MEMORY_SEGMENT_HANDLE = loaderLookup.find("getIntoMemorySegment")
                                                 .map(symbol -> linker.downcallHandle(symbol,
                                                                                      FunctionDescriptor.of(
                                                                                          ValueLayout.JAVA_INT,
                                                                                          ValueLayout.ADDRESS,
                                                                                          ValueLayout.ADDRESS,
                                                                                          ValueLayout.JAVA_INT)))
                                                 .orElseThrow();

  }

  @State(Scope.Benchmark)
  public static class GetJNIBenchmarkStateJava25 extends GetJNIBenchmarkState {
    private Arena arena;
    private MemorySegment keyMemorySegment;

    @Setup
    public void setupJava25() {
      super.setup();
      arena = Arena.ofShared();
      keyMemorySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, keyBytes);

    }

    @TearDown
    public void tearDown() {
      if (arena != null) {
        arena.close();
      }
    }
  }

  @State(Scope.Thread)
  public static class GetJNIThreadStateJava25 extends GetJNIThreadState {
    private MemorySegmentCache memorySegmentCache = new MemorySegmentCache();

    @Setup
    public void setup(final GetJNIBenchmarkStateJava25 benchmarkState, final Blackhole blackhole) {
      if ("getIntoMemorySegment".equals(benchmarkState.getCaller().benchmarkMethod)) {
        memorySegmentCache.setup(benchmarkState.valueSize, benchmarkState.cacheMB * GetJNIBenchmarkState.MB,
                                 benchmarkState.cacheEntryOverhead, benchmarkState.readChecksum, blackhole);
      } else {
        super.setup(benchmarkState, blackhole);
      }
    }

    @TearDown
    public void tearDown(final GetJNIBenchmarkStateJava25 benchmarkState) {
      if ("getIntoMemorySegment".equals(benchmarkState.getCaller().benchmarkMethod)) {
        memorySegmentCache.tearDown();
      } else {
        super.tearDown(benchmarkState);
      }
    }
  }

  @Benchmark
  public void getIntoMemorySegment(GetJNIBenchmarkStateJava25 benchmarkState, GetJNIThreadStateJava25 threadState,
                                   Blackhole blackhole) {
    final var segment = threadState.memorySegmentCache.acquire();

    try {
      final var size = (int) GET_INTO_MEMORY_SEGMENT_HANDLE.invokeExact(
          benchmarkState.keyMemorySegment, // Pre-allocated segment for key
          segment,
          benchmarkState.valueSize
      );
      blackhole.consume(size);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }

    threadState.memorySegmentCache.checksumBuffer(segment);
    threadState.memorySegmentCache.release(segment);
  }
}
