/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.bonsai;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.AbstractJsonRpcHttpBySpecTest;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TraceJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpBySpecTest {

  @Override
  protected void doSetup() throws Exception {
    setupBonsaiBlockchain();
    startService();
  }

  @Override
  protected BlockchainSetupUtil getBlockchainSetupUtil(final DataStorageFormat storageFormat) {
    return createBlockchainSetupUtil(
        "trace/chain-data/genesis.json", "trace/chain-data/blocks.bin", storageFormat);
  }

  @ParameterizedTest
  @CsvSource({"53086, false", "53089, true"})
  void rootOpcodeReportsEffectsOnlyWithSufficientGas(final long gas, final boolean succeeds)
      throws Exception {
    // Istanbul creation costs 53,080 intrinsic gas. Two PUSHes cost six more;
    // ADD needs another three, so only the second case can execute it.
    final JsonNode result = traceVm("6001600201", gas);
    final JsonNode ops = result.get("vmTrace").get("ops");
    final JsonNode add = ops.get(ops.size() - 1);
    assertThat(add.get("pc").asInt()).isEqualTo(4);
    if (succeeds) {
      assertThat(result.get("trace").get(0).has("error")).isFalse();
      assertThat(add.get("ex").get("push").get(0).asText()).isEqualTo("0x3");
      assertThat(add.get("ex").get("used").asLong()).isZero();
    } else {
      assertThat(result.get("trace").get(0).get("error").asText()).isEqualTo("Out of gas");
      assertThat(add.get("ex").isNull()).isTrue();
    }
  }

  private JsonNode traceVm(final String code, final long gas) throws Exception {
    final String request =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"trace_call\",\"params\":[{"
            + "\"from\":\"0x627306090abab3a6e1400e9345bc60c78a8bef57\",\"gas\":\"0x"
            + Long.toHexString(gas)
            + "\",\"data\":\"0x"
            + code
            + "\"},[\"vmTrace\",\"trace\"],\"latest\"]}";
    try (Response response =
        client
            .newCall(
                new Request.Builder().url(baseUrl).post(RequestBody.create(request, JSON)).build())
            .execute()) {
      assertThat(response.code()).isEqualTo(200);
      final JsonNode body = new ObjectMapper().readTree(response.body().string());
      assertThat(body.has("error")).isFalse();
      assertThat(body.has("result")).isTrue();
      return body.get("result");
    }
  }

  public static Object[][] specs() {
    return AbstractJsonRpcHttpBySpecTest.findSpecFiles(
        new String[] {
          "trace/specs/trace-block",
          "trace/specs/trace-get",
          "trace/specs/trace-transaction",
          "trace/specs/replay-trace-transaction/flat",
          "trace/specs/replay-trace-transaction/vm-trace",
          "trace/specs/replay-trace-transaction/statediff",
          "trace/specs/replay-trace-transaction/all",
          "trace/specs/replay-trace-transaction/halt-cases",
          "trace/specs/trace-filter",
          "trace/specs/trace-call",
          "trace/specs/trace-callMany",
          "trace/specs/trace-raw-transaction"
        });
  }
}
