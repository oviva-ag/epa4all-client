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

package de.gematik.vau.lib.crypto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

class KEMTest {

  @Test
  void encryptAeadThrowingIllegalArgumentException() {
    try (var cipherMock = mockStatic(Cipher.class)) {
      cipherMock
          .when(() -> Cipher.getInstance(anyString()))
          .thenThrow(new NoSuchAlgorithmException("Cannot find any provider supporting"));

      var key = new byte[32];
      var plainText = "plaintext".getBytes();
      assertThatThrownBy(() -> KEM.encryptAead(key, plainText))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Error while encrypting plaintext")
          .hasMessageContaining("Cannot find any provider supporting");
    }
  }

  @Test
  void decryptAeadThrowingIllegalArgumentException() {
    var key = new byte[32];
    var cipherText = new byte[0];
    assertThatThrownBy(() -> KEM.decryptAead(key, cipherText))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Error while decrypting AEAD ciphertext")
        .hasMessageContaining("IV is empty");
  }
}
