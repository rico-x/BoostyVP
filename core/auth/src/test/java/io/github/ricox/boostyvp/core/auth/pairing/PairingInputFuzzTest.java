// SPDX-License-Identifier: MIT
package io.github.ricox.boostyvp.core.auth.pairing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Jazzer fuzz tests for the pairing server's untrusted input. Every unit test
 * run replays the saved inputs under PairingInputFuzzTestInputs; JAZZER_FUZZ=1
 * fuzzes one method (see docs/DEVELOPMENT.md). The checks are in
 * PairingInputChecks.
 */
class PairingInputFuzzTest {
    @FuzzTest(maxDuration = "2m")
    void httpRequest(FuzzedDataProvider data) {
        PairingInputChecks.INSTANCE.httpRequest(data.consumeRemainingAsBytes());
    }

    @FuzzTest(maxDuration = "2m")
    void formField(FuzzedDataProvider data) {
        String name = data.consumeString(32);
        PairingInputChecks.INSTANCE.formField(data.consumeRemainingAsString(), name);
    }

    @FuzzTest(maxDuration = "2m")
    void session(FuzzedDataProvider data) {
        String clientId = data.consumeString(64);
        PairingInputChecks.INSTANCE.session(data.consumeRemainingAsString(), clientId);
    }
}
