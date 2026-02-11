package com.pension.engine.mutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.response.CalculationMessage;
import com.pension.engine.model.state.Dossier;
import com.pension.engine.model.state.Policy;
import com.pension.engine.model.state.Projection;
import com.pension.engine.model.state.Situation;
import com.pension.engine.scheme.SchemeRegistryClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProjectFutureBenefitsHandler implements MutationHandler {

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

        String startDateStr = props.path("projection_start_date").asText();
        String endDateStr = props.path("projection_end_date").asText();
        int intervalMonths = props.path("projection_interval_months").asInt();

        if (endDateStr.compareTo(startDateStr) <= 0) {
            return MutationResult.critical(new CalculationMessage(
                    "CRITICAL", "INVALID_DATE_RANGE", "Projection end date must be after start date"));
        }

        List<CalculationMessage> warnings = null;

        // Check projection before employment warning
        for (int i = 0; i < policies.size(); i++) {
            Policy policy = policies.get(i);
            if (startDateStr.compareTo(policy.getEmploymentStartDate()) < 0) {
                if (warnings == null) warnings = new ArrayList<>(1);
                warnings.add(new CalculationMessage(
                        "WARNING", "PROJECTION_BEFORE_EMPLOYMENT",
                        "Projection start date is before employment start date for policy " + policy.getPolicyId()));
            }
        }

        // Fetch accrual rates
        Map<String, Double> accrualRates = null;
        if (schemeClient != null) {
            accrualRates = schemeClient.getAccrualRates(policies);
        }

        int policyCount = policies.size();
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        // Pre-parse employment start dates as epoch days and compute effective salaries
        long[] empStartDays = new long[policyCount];
        double[] effectiveSalaries = new double[policyCount];
        double[] accrualRateArr = new double[policyCount];
        for (int i = 0; i < policyCount; i++) {
            Policy policy = policies.get(i);
            empStartDays[i] = LocalDate.parse(policy.getEmploymentStartDate()).toEpochDay();
            effectiveSalaries[i] = policy.getSalary() * policy.getPartTimeFactor();
            if (accrualRates != null) {
                accrualRateArr[i] = accrualRates.getOrDefault(policy.getSchemeId(), 0.02);
            } else {
                accrualRateArr[i] = 0.02;
            }
        }

        // Count projection dates first to pre-allocate
        int dateCount = 0;
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusMonths(intervalMonths)) {
            dateCount++;
        }

        // Initialize projections for each policy
        List<List<Projection>> allProjections = new ArrayList<>(policyCount);
        for (int i = 0; i < policyCount; i++) {
            allProjections.add(new ArrayList<>(dateCount));
        }

        // Reuse arrays across projection dates
        double[] years = new double[policyCount];

        // For each projection date, calculate pension using the same formula as retirement
        for (LocalDate projDate = startDate; !projDate.isAfter(endDate); projDate = projDate.plusMonths(intervalMonths)) {
            long projDayEpoch = projDate.toEpochDay();
            double totalYears = 0;
            double weightedSum = 0;

            for (int i = 0; i < policyCount; i++) {
                long daysDiff = projDayEpoch - empStartDays[i];
                if (daysDiff >= 0) {
                    years[i] = daysDiff / 365.25;
                } else {
                    years[i] = 0;
                }
                totalYears += years[i];
                weightedSum += effectiveSalaries[i] * years[i];
            }

            double weightedAvg = totalYears > 0 ? weightedSum / totalYears : 0;
            String dateStr = projDate.toString();

            for (int i = 0; i < policyCount; i++) {
                double policyPension;
                if (totalYears > 0) {
                    policyPension = weightedAvg * years[i] * accrualRateArr[i];
                } else {
                    policyPension = 0;
                }
                allProjections.get(i).add(new Projection(dateStr, policyPension));
            }
        }

        // Set projections on policies
        for (int i = 0; i < policyCount; i++) {
            policies.get(i).setProjections(allProjections.get(i));
        }

        if (warnings != null && !warnings.isEmpty()) {
            return MutationResult.warnings(warnings);
        }
        return MutationResult.success();
    }
}
