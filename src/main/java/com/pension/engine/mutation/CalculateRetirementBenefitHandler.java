package com.pension.engine.mutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.response.CalculationMessage;
import com.pension.engine.model.state.Dossier;
import com.pension.engine.model.state.Person;
import com.pension.engine.model.state.Policy;
import com.pension.engine.model.state.Situation;
import com.pension.engine.scheme.SchemeRegistryClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CalculateRetirementBenefitHandler implements MutationHandler {

    @Override
    public MutationResult execute(Situation situation, Mutation mutation, SchemeRegistryClient schemeClient) {
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
        LocalDate retirementDate = LocalDate.parse(retirementDateStr);

        int policyCount = policies.size();
        double[] years = new double[policyCount];
        double[] effectiveSalaries = new double[policyCount];
        List<CalculationMessage> warnings = null;
        double totalYears = 0;

        // Single pass: calculate years of service, effective salaries, and warnings
        for (int i = 0; i < policyCount; i++) {
            Policy policy = policies.get(i);
            LocalDate empStart = LocalDate.parse(policy.getEmploymentStartDate());

            if (retirementDate.isBefore(empStart)) {
                years[i] = 0;
                if (warnings == null) warnings = new ArrayList<>(2);
                warnings.add(new CalculationMessage(
                        "WARNING", "RETIREMENT_BEFORE_EMPLOYMENT",
                        "Retirement date is before employment start date for policy " + policy.getPolicyId()));
            } else {
                long days = ChronoUnit.DAYS.between(empStart, retirementDate);
                years[i] = days / 365.25;
            }

            effectiveSalaries[i] = policy.getSalary() * policy.getPartTimeFactor();
            totalYears += years[i];
        }

        // Eligibility check: age >= 65 OR total years >= 40
        Person participant = dossier.getPersons().get(0);
        LocalDate birthDate = LocalDate.parse(participant.getBirthDate());
        long ageDays = ChronoUnit.DAYS.between(birthDate, retirementDate);
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

        // Calculate annual pension using accrual rate (per-scheme if available, else default 0.02)
        double annualPension;
        if (accrualRates != null) {
            // When using per-scheme accrual rates, we need to calculate per-policy pension directly
            // Annual pension = Σ(weighted_avg * policy_years * accrual_rate_for_policy_scheme)
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

        if (warnings != null && !warnings.isEmpty()) {
            return MutationResult.warnings(warnings);
        }
        return MutationResult.success();
    }
}
