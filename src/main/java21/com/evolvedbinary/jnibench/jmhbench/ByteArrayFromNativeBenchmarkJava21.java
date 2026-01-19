package com.evolvedbinary.jnibench.jmhbench;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import org.openjdk.jmh.annotations.Benchmark;

public class ByteArrayFromNativeBenchmarkJava21 extends ByteArrayFromNativeBenchmark {
  private static final MethodHandle ffmGetByteArrayHandle;

  static {
    try {
      Linker linker = Linker.nativeLinker();
      SymbolLookup symbolLookup = SymbolLookup.loaderLookup();

      var symbolOpt = symbolLookup.find("GetByteArray_getFFM")
                                  .or(() -> symbolLookup.find("_GetByteArray_getFFM"))  // macOS prefix
                                  .or(() -> symbolLookup.find("GetByteArray_getFFM@28")); // Windows mangled name

      if (symbolOpt.isPresent()) {
        FunctionDescriptor ffmDescriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT
        );

        ffmGetByteArrayHandle = linker.downcallHandle(
            symbolOpt.get(),
            ffmDescriptor
        );
      } else {
        ffmGetByteArrayHandle = null;
      }
    } catch (Exception e) {
      System.err.println("FFM Setup failed: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }


  @Benchmark
  public byte[] ffmGetByteArray(BenchmarkState benchmarkState) throws Throwable {
    if (ffmGetByteArrayHandle == null) {
      throw new RuntimeException("FFM not available");
    }

    try (Arena arena = Arena.ofConfined()) {
      final var keySegment = arena.allocateArray(ValueLayout.JAVA_BYTE, benchmarkState.keyBytes);

      final var valueSegment = arena.allocate(benchmarkState.valueSize);

      int bytesRead = (int) ffmGetByteArrayHandle.invoke(
          keySegment,
          0,
          benchmarkState.keyBytes.length,
          valueSegment,
          0,
          benchmarkState.valueSize
      );

      byte[] result = new byte[bytesRead];
      MemorySegment.copy(valueSegment, ValueLayout.JAVA_BYTE, 0, result, 0, bytesRead);

      return result;
    }
  }

  @Benchmark
  public int ffmGetByteArrayPreallocated(BenchmarkState benchmarkState, ThreadState threadState) throws Throwable {
    if (ffmGetByteArrayHandle == null) {
      throw new RuntimeException("FFM not available");
    }

    try (Arena arena = Arena.ofConfined()) {
      final var keySegment = arena.allocateArray(ValueLayout.JAVA_BYTE, benchmarkState.keyBytes);

      final var valueSegment = arena.allocate(benchmarkState.valueSize);

      int bytesRead = (int) ffmGetByteArrayHandle.invoke(
          keySegment,
          0,
          benchmarkState.keyBytes.length,
          valueSegment,
          0,
          benchmarkState.valueSize
      );

      final var offset = threadState.getValueOffset();
      MemorySegment.copy(valueSegment, ValueLayout.JAVA_BYTE, 0,
                         threadState.batchValueBuffer, offset,
                         Math.min(bytesRead, benchmarkState.valueSize));

      return bytesRead;
    }
  }
}
