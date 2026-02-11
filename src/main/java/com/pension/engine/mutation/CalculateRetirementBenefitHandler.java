package com.pension.engine.mutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.response.CalculationMessage;
import com.pension.engine.model.state.Dossier;
import com.pension.engine.model.state.Person;
import com.pension.engine.model.state.Policy;
import com.pension.engine.model.state.Situation;
import com.pension.engine.scheme.SchemeRegistryClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CalculateRetirementBenefitHandler implements MutationHandler {

    @Override
    public MutationResult execute(Situation situation, Mutation mutation, SchemeRegistryClient schemeClient, ObjectMapper mapper) {
        JsonNode props = mutation.getMutationProperties();
        Dossier dossier = situation.getDossier();

        // Validation
        if (dossier == null) {
            return MutationResult.critical(new CalculationMessage(
                    "CRITICAL", "DOSSIER_NOT_FOUND", "No dossier exists in the situation"));
        }

        List<Policy> policies = dossier.getPolicies();
        if (policies.isEmpty()) {
            return MutationResult.critical(new CalculationMessage(
                    "CRITICAL", "NO_POLICIES", "Dossier has no policies"));
        }

        String retirementDateStr = props.path("retirement_date").asText();
        long retirementEpochDay = LocalDate.parse(retirementDateStr).toEpochDay();

        int policyCount = policies.size();
        double[] years = new double[policyCount];
        double[] effectiveSalaries = new double[policyCount];
        List<CalculationMessage> warnings = null;
        double totalYears = 0;

        // Single pass: calculate years of service, effective salaries, and warnings
        for (int i = 0; i < policyCount; i++) {
            Policy policy = policies.get(i);
            long empStartDay = LocalDate.parse(policy.getEmploymentStartDate()).toEpochDay();
            long daysDiff = retirementEpochDay - empStartDay;

            if (daysDiff < 0) {
                years[i] = 0;
                if (warnings == null) warnings = new ArrayList<>(2);
                warnings.add(new CalculationMessage(
                        "WARNING", "RETIREMENT_BEFORE_EMPLOYMENT",
                        "Retirement date is before employment start date for policy " + policy.getPolicyId()));
            } else {
                years[i] = daysDiff / 365.25;
            }

            effectiveSalaries[i] = policy.getSalary() * policy.getPartTimeFactor();
            totalYears += years[i];
        }

        // Eligibility check: age >= 65 OR total years >= 40
        Person participant = dossier.getPersons().get(0);
        long birthEpochDay = LocalDate.parse(participant.getBirthDate()).toEpochDay();
        long ageDays = retirementEpochDay - birthEpochDay;
        double age = ageDays / 365.25;

        if (age < 65 && totalYears < 40) {
            // Combine warnings with critical
            List<CalculationMessage> allMessages = new ArrayList<>(warnings != null ? warnings.size() + 1 : 1);
            if (warnings != null) allMessages.addAll(warnings);
            allMessages.add(new CalculationMessage(
                    "CRITICAL", "NOT_ELIGIBLE",
                    "Participant is under 65 years old and has less than 40 years of service"));
            return MutationResult.critical(allMessages);
        }

        // Fetch accrual rates from scheme registry if available
        Map<String, Double> accrualRates = null;
        if (schemeClient != null) {
            accrualRates = schemeClient.getAccrualRates(policies);
        }

        // Calculate weighted average salary
        double weightedSum = 0;
        for (int i = 0; i < policyCount; i++) {
            weightedSum += effectiveSalaries[i] * years[i];
        }
        double weightedAvg = totalYears > 0 ? weightedSum / totalYears : 0;

        // Capture old values for backward patch
        String oldStatus = dossier.getStatus();
        String oldRetirementDate = dossier.getRetirementDate();
        Double[] oldPensions = new Double[policyCount];
        for (int i = 0; i < policyCount; i++) {
            oldPensions[i] = policies.get(i).getAttainablePension();
        }

        // Calculate annual pension using accrual rate (per-scheme if available, else default 0.02)
        double annualPension;
        if (accrualRates != null) {
            annualPension = 0;
            for (int i = 0; i < policyCount; i++) {
                double accrualRate = accrualRates.getOrDefault(policies.get(i).getSchemeId(), 0.02);
                annualPension += weightedAvg * years[i] * accrualRate;
            }

            // Distribute proportionally: each policy gets its share
            for (int i = 0; i < policyCount; i++) {
                if (totalYears > 0) {
                    double accrualRate = accrualRates.getOrDefault(policies.get(i).getSchemeId(), 0.02);
                    double policyPension = weightedAvg * years[i] * accrualRate;
                    policies.get(i).setAttainablePension(policyPension);
                } else {
                    policies.get(i).setAttainablePension(0.0);
                }
            }
        } else {
            annualPension = weightedAvg * totalYears * 0.02;

            // Distribute proportionally
            for (int i = 0; i < policyCount; i++) {
                if (totalYears > 0) {
                    double policyPension = annualPension * (years[i] / totalYears);
                    policies.get(i).setAttainablePension(policyPension);
                } else {
                    policies.get(i).setAttainablePension(0.0);
                }
            }
        }

        // Update dossier status
        dossier.setStatus("RETIRED");
        dossier.setRetirementDate(retirementDateStr);

        // Build patches
        ArrayNode fwd = mapper.createArrayNode();
        ArrayNode bwd = mapper.createArrayNode();

        // Status change
        ObjectNode fwdStatus = fwd.addObject();
        fwdStatus.put("op", "replace");
        fwdStatus.put("path", "/dossier/status");
        fwdStatus.put("value", "RETIRED");

        ObjectNode bwdStatus = bwd.addObject();
        bwdStatus.put("op", "replace");
        bwdStatus.put("path", "/dossier/status");
        bwdStatus.put("value", oldStatus);

        // Retirement date change
        ObjectNode fwdRetDate = fwd.addObject();
        fwdRetDate.put("op", "replace");
        fwdRetDate.put("path", "/dossier/retirement_date");
        fwdRetDate.put("value", retirementDateStr);

        ObjectNode bwdRetDate = bwd.addObject();
        bwdRetDate.put("op", "replace");
        bwdRetDate.put("path", "/dossier/retirement_date");
        if (oldRetirementDate != null) {
            bwdRetDate.put("value", oldRetirementDate);
        } else {
            bwdRetDate.putNull("value");
        }

        // Attainable pension per policy
        for (int i = 0; i < policyCount; i++) {
            String path = "/dossier/policies/" + i + "/attainable_pension";

            ObjectNode fwdPension = fwd.addObject();
            fwdPension.put("op", "replace");
            fwdPension.put("path", path);
            fwdPension.put("value", policies.get(i).getAttainablePension());

            ObjectNode bwdPension = bwd.addObject();
            bwdPension.put("op", "replace");
            bwdPension.put("path", path);
            if (oldPensions[i] != null) {
                bwdPension.put("value", oldPensions[i]);
            } else {
                bwdPension.putNull("value");
            }
        }

        MutationResult result = (warnings != null && !warnings.isEmpty()) ? MutationResult.warnings(warnings) : MutationResult.success();
        return result.withPatches(fwd, bwd);
    }
}
