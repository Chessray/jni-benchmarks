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

public abstract class GetJNIBenchmarkJavaFfm extends GetJNIBenchmark {
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

  public static abstract class GetJNIBenchmarkStateJavaFfm extends GetJNIBenchmarkState {
    private Arena arena;
    private MemorySegment keyMemorySegment;

    @Setup
    public final void setupJavaFfm() {
      super.setup();
      arena = Arena.ofShared();
      keyMemorySegment = allocateFromArena(keyBytes);
    }

    protected abstract MemorySegment allocateFromArena(byte[] keyBytes);

    protected final Arena getArena() {
      return arena;
    }

    @TearDown
    public final void tearDown() {
      if (arena != null) {
        arena.close();
      }
    }
  }

  @State(Scope.Thread)
  public static class GetJNIThreadStateJavaFfm extends GetJNIThreadState {
    private MemorySegmentCache memorySegmentCache = new MemorySegmentCache();

    @Setup
    public void setup(final GetJNIBenchmarkStateJavaFfm benchmarkState, final Blackhole blackhole) {
      if ("getIntoMemorySegment".equals(benchmarkState.getCaller().benchmarkMethod)) {
        memorySegmentCache.setup(benchmarkState.valueSize, benchmarkState.cacheMB * GetJNIBenchmarkState.MB,
                                 benchmarkState.cacheEntryOverhead, benchmarkState.readChecksum, blackhole);
      } else {
        super.setup(benchmarkState, blackhole);
      }
    }

    @TearDown
    public void tearDown(final GetJNIBenchmarkStateJavaFfm benchmarkState) {
      if ("getIntoMemorySegment".equals(benchmarkState.getCaller().benchmarkMethod)) {
        memorySegmentCache.tearDown();
      } else {
        super.tearDown(benchmarkState);
      }
    }
  }

  @Benchmark
  public void getIntoMemorySegment(GetJNIBenchmarkStateJavaFfm benchmarkState, GetJNIThreadStateJavaFfm threadState,
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
