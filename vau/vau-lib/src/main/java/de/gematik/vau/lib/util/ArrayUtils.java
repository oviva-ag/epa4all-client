/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.vau.lib.util;

import java.util.List;
import java.util.stream.Stream;

// mostly borrowed from apache-commons
public class ArrayUtils {

  private ArrayUtils() {}

  public static byte[] unionByteArrays(Object... args) {
    final List<byte[]> byteArrayList =
        Stream.of(args)
            .map(
                arg -> {
                  if (arg instanceof byte[] array) {
                    return array;
                  } else if (arg instanceof Byte b) {
                    return new byte[] {b};
                  } else {
                    throw new RuntimeException("Invalid type " + arg.getClass().getSimpleName());
                  }
                })
            .toList();

    int totalLength = byteArrayList.stream().mapToInt(array -> array.length).sum();
    byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] array : byteArrayList) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }

  public static byte[] addAll(byte[] array1, byte... array2) {
    if (array1 == null) {
      return clone(array2);
    } else if (array2 == null) {
      return clone(array1);
    } else {
      byte[] joinedArray = new byte[array1.length + array2.length];
      System.arraycopy(array1, 0, joinedArray, 0, array1.length);
      System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
      return joinedArray;
    }
  }

  public static byte[] clone(final byte[] array) {
    return array != null ? array.clone() : null;
  }

  public static byte[] subarray(
      final byte[] array, int startIndexInclusive, int endIndexExclusive) {
    if (array == null) {
      return null;
    }
    startIndexInclusive = Math.max(0, startIndexInclusive);
    endIndexExclusive = Math.min(endIndexExclusive, array.length);
    final int newSize = endIndexExclusive - startIndexInclusive;
    if (newSize <= 0) {
      return new byte[0];
    }

    var dst = new byte[newSize];
    System.arraycopy(array, startIndexInclusive, dst, 0, newSize);
    return dst;
  }
}
