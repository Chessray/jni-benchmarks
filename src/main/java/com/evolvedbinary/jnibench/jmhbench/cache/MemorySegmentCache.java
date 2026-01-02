package com.evolvedbinary.jnibench.jmhbench.cache;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.stream.IntStream;

public class MemorySegmentCache extends LinkedListAllocationCache<MemorySegment> {
  private final Arena arena;

  public MemorySegmentCache() {
    arena = Arena.ofShared();
  }


  @Override
  MemorySegment allocate(final int valueSize) {
    return arena.allocate(valueSize);
  }

  @Override
  void free(final MemorySegment buffer) {
    // Nothing to do here, as we override taerdown() directly.
  }

  @Override
  public void tearDown() {
    super.tearDown();
    arena.close();
  }

  @Override
  protected int byteChecksum(final MemorySegment item) {
    return IntStream.range(0, (int) item.byteSize()).map(offset -> item.get(JAVA_BYTE, offset)).sum();
  }

  @Override
  protected int longChecksum(final MemorySegment item) {
    return byteChecksum(item);
  }

  @Override
  protected byte[] copyOut(final MemorySegment item) {
    // Get a cached byte array of the correct size
    byte[] array = byteArrayOfSize((int) item.byteSize());

    // Perform bulk copy from native memory to Java heap array
    MemorySegment.copy(item, ValueLayout.JAVA_BYTE, 0, array, 0, (int) item.byteSize());

    return array;
  }

  @Override
  protected long copyIn(final MemorySegment item, final byte fillByte) {
    // Highly optimized bulk fill (native memset equivalent)
    item.fill(fillByte);

    return fillByte;
  }
}
