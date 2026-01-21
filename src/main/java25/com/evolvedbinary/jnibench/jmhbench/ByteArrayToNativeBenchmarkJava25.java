package com.evolvedbinary.jnibench.jmhbench;

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

public class ByteArrayToNativeBenchmarkJava25 extends ByteArrayToNativeBenchmark {
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

  @State(Scope.Benchmark)
  public static class BenchmarkStateJava25 extends BenchmarkState {
    public MemorySegment ffmKeySegment;
    public MemorySegment ffmValueSegment;
    private Arena benchmarkArena;

    @Setup
    public void setup() {
      super.setup();
      benchmarkArena = Arena.ofShared();
      ffmKeySegment = benchmarkArena.allocateFrom(ValueLayout.JAVA_BYTE, keyBytes);
      ffmValueSegment = benchmarkArena.allocate(keySize);
    }

    @TearDown
    public void tearDown() {
      super.tearDown();
      if (benchmarkArena != null) {
        benchmarkArena.close();
      }
    }
  }

  @Benchmark
  public byte[] passKeyAsFFM(BenchmarkStateJava25 benchmarkState) throws Throwable {
    if (ffmGetByteArrayHandle == null) {
      throw new RuntimeException("FFM not available");
    }

    try (Arena arena = Arena.ofConfined()) {
      final var keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, benchmarkState.keyBytes);
      final var valueSegment = arena.allocate(benchmarkState.keySize);

      int bytesRead = (int) ffmGetByteArrayHandle.invoke(
          keySegment,
          0,
          benchmarkState.keyBytes.length,
          valueSegment,
          0,
          benchmarkState.keySize
      );

      byte[] result = new byte[bytesRead];
      MemorySegment.copy(valueSegment, ValueLayout.JAVA_BYTE, 0, result, 0, bytesRead);

      return result;
    }
  }

  @Benchmark
  public byte[] passKeyAsFFMPreallocated(BenchmarkStateJava25 benchmarkState) throws Throwable {
    if (ffmGetByteArrayHandle == null) {
      throw new RuntimeException("FFM not available");
    }

    int bytesRead = (int) ffmGetByteArrayHandle.invoke(
        benchmarkState.ffmKeySegment,
        0,
        benchmarkState.keyBytes.length,
        benchmarkState.ffmValueSegment,
        0,
        benchmarkState.keySize
    );

    byte[] result = new byte[bytesRead];
    MemorySegment.copy(benchmarkState.ffmValueSegment, ValueLayout.JAVA_BYTE, 0, result, 0, bytesRead);

    return result;
  }
}
