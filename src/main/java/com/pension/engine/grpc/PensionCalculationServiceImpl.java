package com.pension.engine.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.google.protobuf.*;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.pension.engine.CalculationEngine;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PensionCalculationServiceImpl
        extends PensionCalculationServiceGrpc.PensionCalculationServiceImplBase {

    private final CalculationEngine engine;
    private final ObjectMapper mapper;

    public PensionCalculationServiceImpl(CalculationEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    @Override
    public void calculate(
            com.pension.engine.grpc.CalculationRequest protoRequest,
            StreamObserver<com.pension.engine.grpc.CalculationResponse> responseObserver) {
        try {
            var javaRequest = convertRequestFromProto(protoRequest);
            var javaResponse = engine.process(javaRequest);
            var protoResponse = convertResponseToProto(javaResponse);
            responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    // ── Proto → Java conversion (request) ──

    private com.pension.engine.model.request.CalculationRequest convertRequestFromProto(
            com.pension.engine.grpc.CalculationRequest proto) {
        var java = new com.pension.engine.model.request.CalculationRequest();
        java.setTenantId(proto.getTenantId());

        var instructions = new com.pension.engine.model.request.CalculationInstructions();
        List<com.pension.engine.model.request.Mutation> mutations = new ArrayList<>(
                proto.getCalculationInstructions().getMutationsCount());
        for (var pm : proto.getCalculationInstructions().getMutationsList()) {
            mutations.add(convertMutationFromProto(pm));
        }
        instructions.setMutations(mutations);
        java.setCalculationInstructions(instructions);
        return java;
    }

    private com.pension.engine.model.request.Mutation convertMutationFromProto(
            com.pension.engine.grpc.Mutation proto) {
        var java = new com.pension.engine.model.request.Mutation();
        java.setMutationId(proto.getMutationId());
        java.setMutationDefinitionName(proto.getMutationDefinitionName());
        java.setMutationType(proto.getMutationType());
        java.setActualAt(proto.getActualAt());
        if (proto.hasDossierId()) {
            java.setDossierId(proto.getDossierId());
        }
        if (proto.hasMutationProperties()) {
            java.setMutationProperties(structToJsonNode(proto.getMutationProperties()));
        }
        return java;
    }

    // ── Java → Proto conversion (response) ──

    private com.pension.engine.grpc.CalculationResponse convertResponseToProto(
            com.pension.engine.model.response.CalculationResponse java) {
        var builder = com.pension.engine.grpc.CalculationResponse.newBuilder();
        builder.setCalculationMetadata(convertMetadataToProto(java.getCalculationMetadata()));
        builder.setCalculationResult(convertResultToProto(java.getCalculationResult()));
        return builder.build();
    }

    private com.pension.engine.grpc.CalculationMetadata convertMetadataToProto(
            com.pension.engine.model.response.CalculationMetadata java) {
        return com.pension.engine.grpc.CalculationMetadata.newBuilder()
                .setCalculationId(java.getCalculationId())
                .setTenantId(java.getTenantId())
                .setCalculationStartedAt(java.getCalculationStartedAt())
                .setCalculationCompletedAt(java.getCalculationCompletedAt())
                .setCalculationDurationMs(java.getCalculationDurationMs())
                .setCalculationOutcome(java.getCalculationOutcome())
                .build();
    }

    private com.pension.engine.grpc.CalculationResult convertResultToProto(
            com.pension.engine.model.response.CalculationResult java) {
        var builder = com.pension.engine.grpc.CalculationResult.newBuilder();

        for (var msg : java.getMessages()) {
            builder.addMessages(convertMessageToProto(msg));
        }
        for (var pm : java.getMutations()) {
            builder.addMutations(convertProcessedMutationToProto(pm));
        }
        builder.setEndSituation(convertSnapshotToProto(java.getEndSituation()));
        builder.setInitialSituation(convertInitialSituationToProto(java.getInitialSituation()));
        return builder.build();
    }

    private com.pension.engine.grpc.CalculationMessage convertMessageToProto(
            com.pension.engine.model.response.CalculationMessage java) {
        return com.pension.engine.grpc.CalculationMessage.newBuilder()
                .setId(java.getId())
                .setLevel(java.getLevel())
                .setCode(java.getCode())
                .setMessage(java.getMessage())
                .build();
    }

    private com.pension.engine.grpc.ProcessedMutation convertProcessedMutationToProto(
            com.pension.engine.model.response.ProcessedMutation java) {
        var builder = com.pension.engine.grpc.ProcessedMutation.newBuilder();
        builder.setMutation(convertMutationToProto(java.getMutation()));

        if (java.getCalculationMessageIndexes() != null) {
            for (int idx : java.getCalculationMessageIndexes()) {
                builder.addCalculationMessageIndexes(idx);
            }
        }

        if (java.getForwardPatch() != null) {
            builder.setForwardPatchToSituationAfterThisMutation(jsonNodeToValue(java.getForwardPatch()));
        }
        if (java.getBackwardPatch() != null) {
            builder.setBackwardPatchToPreviousSituation(jsonNodeToValue(java.getBackwardPatch()));
        }
        return builder.build();
    }

    private com.pension.engine.grpc.Mutation convertMutationToProto(
            com.pension.engine.model.request.Mutation java) {
        var builder = com.pension.engine.grpc.Mutation.newBuilder()
                .setMutationId(java.getMutationId())
                .setMutationDefinitionName(java.getMutationDefinitionName())
                .setMutationType(java.getMutationType())
                .setActualAt(java.getActualAt());
        if (java.getDossierId() != null) {
            builder.setDossierId(java.getDossierId());
        }
        if (java.getMutationProperties() != null) {
            builder.setMutationProperties(jsonNodeToStruct(java.getMutationProperties()));
        }
        return builder.build();
    }

    private com.pension.engine.grpc.SituationSnapshot convertSnapshotToProto(
            com.pension.engine.model.response.SituationSnapshot java) {
        var builder = com.pension.engine.grpc.SituationSnapshot.newBuilder()
                .setMutationId(java.getMutationId())
                .setMutationIndex(java.getMutationIndex())
                .setActualAt(java.getActualAt());
        builder.setSituation(convertSituationToProto(java.getSituation()));
        return builder.build();
    }

    private com.pension.engine.grpc.InitialSituation convertInitialSituationToProto(
            com.pension.engine.model.response.InitialSituation java) {
        var builder = com.pension.engine.grpc.InitialSituation.newBuilder()
                .setActualAt(java.getActualAt());
        builder.setSituation(convertSituationToProto(java.getSituation()));
        return builder.build();
    }

    private com.pension.engine.grpc.Situation convertSituationToProto(
            com.pension.engine.model.state.Situation java) {
        var builder = com.pension.engine.grpc.Situation.newBuilder();
        if (java.getDossier() != null) {
            builder.setDossier(convertDossierToProto(java.getDossier()));
        }
        return builder.build();
    }

    private com.pension.engine.grpc.Dossier convertDossierToProto(
            com.pension.engine.model.state.Dossier java) {
        var builder = com.pension.engine.grpc.Dossier.newBuilder()
                .setDossierId(java.getDossierId())
                .setStatus(java.getStatus());
        if (java.getRetirementDate() != null) {
            builder.setRetirementDate(java.getRetirementDate());
        }
        for (var p : java.getPersons()) {
            builder.addPersons(convertPersonToProto(p));
        }
        for (var p : java.getPolicies()) {
            builder.addPolicies(convertPolicyToProto(p));
        }
        return builder.build();
    }

    private com.pension.engine.grpc.Person convertPersonToProto(
            com.pension.engine.model.state.Person java) {
        return com.pension.engine.grpc.Person.newBuilder()
                .setPersonId(java.getPersonId())
                .setRole(java.getRole())
                .setName(java.getName())
                .setBirthDate(java.getBirthDate())
                .build();
    }

    private com.pension.engine.grpc.Policy convertPolicyToProto(
            com.pension.engine.model.state.Policy java) {
        var builder = com.pension.engine.grpc.Policy.newBuilder()
                .setPolicyId(java.getPolicyId())
                .setSchemeId(java.getSchemeId())
                .setEmploymentStartDate(java.getEmploymentStartDate())
                .setSalary(java.getSalary())
                .setPartTimeFactor(java.getPartTimeFactor());
        if (java.getAttainablePension() != null) {
            builder.setAttainablePension(java.getAttainablePension());
        }
        if (java.getProjections() != null) {
            for (var proj : java.getProjections()) {
                builder.addProjections(convertProjectionToProto(proj));
            }
        }
        return builder.build();
    }

    private com.pension.engine.grpc.Projection convertProjectionToProto(
            com.pension.engine.model.state.Projection java) {
        return com.pension.engine.grpc.Projection.newBuilder()
                .setDate(java.getDate())
                .setProjectedPension(java.getProjectedPension())
                .build();
    }

    // ── Struct/Value ↔ JsonNode conversion ──

    private JsonNode structToJsonNode(Struct struct) {
        ObjectNode node = mapper.createObjectNode();
        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            node.set(entry.getKey(), valueToJsonNode(entry.getValue()));
        }
        return node;
    }

    private JsonNode valueToJsonNode(Value value) {
        switch (value.getKindCase()) {
            case NULL_VALUE:
                return NullNode.getInstance();
            case NUMBER_VALUE: {
                double d = value.getNumberValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < Long.MAX_VALUE) {
                    long l = (long) d;
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return IntNode.valueOf((int) l);
                    }
                    return LongNode.valueOf(l);
                }
                return DoubleNode.valueOf(d);
            }
            case STRING_VALUE:
                return TextNode.valueOf(value.getStringValue());
            case BOOL_VALUE:
                return BooleanNode.valueOf(value.getBoolValue());
            case STRUCT_VALUE:
                return structToJsonNode(value.getStructValue());
            case LIST_VALUE: {
                ArrayNode arr = mapper.createArrayNode();
                for (Value v : value.getListValue().getValuesList()) {
                    arr.add(valueToJsonNode(v));
                }
                return arr;
            }
            default:
                return NullNode.getInstance();
        }
    }

    private Struct jsonNodeToStruct(JsonNode node) {
        Struct.Builder builder = Struct.newBuilder();
        if (node.isObject()) {
            node.fields().forEachRemaining(e ->
                    builder.putFields(e.getKey(), jsonNodeToValue(e.getValue())));
        }
        return builder.build();
    }

    private Value jsonNodeToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
        if (node.isTextual()) {
            return Value.newBuilder().setStringValue(node.textValue()).build();
        }
        if (node.isNumber()) {
            return Value.newBuilder().setNumberValue(node.doubleValue()).build();
        }
        if (node.isBoolean()) {
            return Value.newBuilder().setBoolValue(node.booleanValue()).build();
        }
        if (node.isObject()) {
            Struct.Builder struct = Struct.newBuilder();
            node.fields().forEachRemaining(e ->
                    struct.putFields(e.getKey(), jsonNodeToValue(e.getValue())));
            return Value.newBuilder().setStructValue(struct).build();
        }
        if (node.isArray()) {
            ListValue.Builder list = ListValue.newBuilder();
            for (JsonNode child : node) {
                list.addValues(jsonNodeToValue(child));
            }
            return Value.newBuilder().setListValue(list).build();
        }
        return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    }
}
