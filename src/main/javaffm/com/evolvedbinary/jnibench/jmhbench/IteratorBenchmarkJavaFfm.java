package com.evolvedbinary.jnibench.jmhbench;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class IteratorBenchmarkJavaFfm extends IteratorBenchmark {
  private static final MethodHandle ITERATOR_CREATE_HANDLE;
  private static final MethodHandle ITERATOR_HAS_NEXT_HANDLE;
  private static final MethodHandle ITERATOR_NEXT_HANDLE;
  private static final MethodHandle ITERATOR_DISPOSE_HANDLE;

  static {
    try {
      Linker linker = Linker.nativeLinker();
      SymbolLookup symbolLookup = SymbolLookup.loaderLookup();

      ITERATOR_CREATE_HANDLE = findHandle(symbolLookup, linker, "iterator_create",
                                          FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                ValueLayout.JAVA_LONG));
      ITERATOR_HAS_NEXT_HANDLE = findHandle(symbolLookup, linker, "iterator_has_next",
                                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      ITERATOR_NEXT_HANDLE = findHandle(symbolLookup, linker, "iterator_next",
                                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                                              ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      ITERATOR_DISPOSE_HANDLE = findHandle(symbolLookup, linker, "iterator_dispose",
                                           FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    } catch (Exception e) {
      System.err.println("FFM Setup failed: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private static MethodHandle findHandle(SymbolLookup lookup, Linker linker, String symbol,
                                         FunctionDescriptor descriptor) {
    var symbolOpt = lookup.find(symbol)
                          .or(() -> lookup.find("_" + symbol)); // macOS prefix
    return symbolOpt.map(memorySegment -> linker.downcallHandle(memorySegment, descriptor))
                    .orElse(null);
  }

  @Benchmark
  public void iterateFfm(BenchmarkState benchmarkState, Blackhole blackhole) throws Throwable {
    if (ITERATOR_CREATE_HANDLE == null || ITERATOR_HAS_NEXT_HANDLE == null
        || ITERATOR_NEXT_HANDLE == null || ITERATOR_DISPOSE_HANDLE == null) {
      throw new RuntimeException("FFM not available");
    }

    MemorySegment iteratorHandle = null;
    try (Arena arena = Arena.ofConfined()) {
      iteratorHandle = (MemorySegment) ITERATOR_CREATE_HANDLE.invokeExact(benchmarkState.numElements,
                                                                          benchmarkState.elementSize);
      MemorySegment buffer = arena.allocate(benchmarkState.elementSize);
      int bufferSize = Math.toIntExact(buffer.byteSize());

      while (((int) ITERATOR_HAS_NEXT_HANDLE.invokeExact(iteratorHandle)) != 0) {
        int bytesRead = (int) ITERATOR_NEXT_HANDLE.invokeExact(iteratorHandle, buffer, bufferSize);
        if (bytesRead < 0) {
          throw new IllegalStateException("Iterator exhausted unexpectedly");
        }
        byte[] result = new byte[bytesRead];
        MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, result, 0, bytesRead);
        blackhole.consume(result);
      }
    } finally {
      if (iteratorHandle != null) {
        ITERATOR_DISPOSE_HANDLE.invokeExact(iteratorHandle);
      }
    }
  }
}
