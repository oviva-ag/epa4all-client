/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.vau.lib.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// borrowed from apache-commons
public class DigestUtils {

  private DigestUtils() {}

  /**
   * Calculates the SHA-256 digest and returns the value as a {@code byte[]}.
   *
   * @param data Data to digest
   * @return SHA-256 digest
   * @since 1.4
   */
  public static byte[] sha256(final byte[] data) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
