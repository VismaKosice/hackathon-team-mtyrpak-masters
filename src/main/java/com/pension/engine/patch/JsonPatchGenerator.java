package com.pension.engine.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.zjsonpatch.JsonDiff;
import com.flipkart.zjsonpatch.DiffFlags;

import java.util.EnumSet;

public class JsonPatchGenerator {

    private static final EnumSet<DiffFlags> FLAGS = EnumSet.of(
            DiffFlags.OMIT_MOVE_OPERATION,
            DiffFlags.OMIT_COPY_OPERATION
    );

    public static JsonNode generateForwardPatch(JsonNode before, JsonNode after) {
        return JsonDiff.asJson(before, after, FLAGS);
    }

    public static JsonNode generateBackwardPatch(JsonNode before, JsonNode after) {
        return JsonDiff.asJson(after, before, FLAGS);
    }

    public static JsonNode[] generateBothPatches(JsonNode before, JsonNode after) {
        return new JsonNode[] {
            JsonDiff.asJson(before, after, FLAGS),
            JsonDiff.asJson(after, before, FLAGS)
        };
    }
}
