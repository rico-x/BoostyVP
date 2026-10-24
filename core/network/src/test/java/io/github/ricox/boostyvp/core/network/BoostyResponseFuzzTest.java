// SPDX-License-Identifier: MIT
package io.github.ricox.boostyvp.core.network;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Jazzer fuzz tests for Boosty API answers and content blocks. Every unit test
 * run replays the saved inputs under BoostyResponseFuzzTestInputs; JAZZER_FUZZ=1
 * fuzzes one method (see docs/DEVELOPMENT.md). The checks are in
 * BoostyResponseChecks.
 */
class BoostyResponseFuzzTest {
    private static final String[] TYPES = {"text", "header", "link", "image", "video", "ok_video", "audio_file", "file", "smile"};
    private static final String[] PLAYER_TYPES = {"hls", "dash", "low", "high", "full_hd", "ultra_hd", "live_playback_hls", ""};

    @FuzzTest(maxDuration = "2m")
    void response(FuzzedDataProvider data) {
        int kind = data.consumeInt(0, 3);
        BoostyResponseChecks.INSTANCE.response(kind, data.consumeRemainingAsString());
    }

    @FuzzTest(maxDuration = "2m")
    void contentBlock(FuzzedDataProvider data) {
        BoostyResponseChecks.INSTANCE.contentBlock(
            data.pickValue(TYPES), data.consumeString(512), data.consumeString(128), data.consumeString(16),
            data.pickValue(PLAYER_TYPES), data.consumeString(128), data.consumeString(16));
    }
}
