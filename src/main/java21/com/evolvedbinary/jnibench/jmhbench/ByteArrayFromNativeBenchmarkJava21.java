/**
 * Copyright © 2016, Evolved Binary Ltd
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
