/*
 * Copyright 2023 gematik GmbH
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

package com.oviva.telematik.epaapi.internal;

import static org.apache.cxf.message.Message.*;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class MtomConfigOutInterceptor extends AbstractPhaseInterceptor<Message> {

  public MtomConfigOutInterceptor() {
    this(Phase.PRE_STREAM);
  }

  public MtomConfigOutInterceptor(String phase) {
    super(phase);
  }

  @Override
  public void handleMessage(Message message) throws Fault {
    message.put(MTOM_ENABLED, true);
  }
}
