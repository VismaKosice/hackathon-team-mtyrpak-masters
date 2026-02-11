#!/usr/bin/env python3
"""Test script for Pension Calculation Engine - REST API."""

import requests
import json
import sys
import time
import os
import argparse
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "http://localhost:8080"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

def pp(data):
    print(json.dumps(data, indent=2))

# ── Transport ──

def rest_send(request_dict):
    """Send via REST, return (data_dict, elapsed_ms)."""
    start = time.perf_counter()
    resp = requests.post(f"{BASE_URL}/calculation-requests", json=request_dict)
    elapsed_ms = (time.perf_counter() - start) * 1000
    return resp.json(), elapsed_ms

# ── Test functions ──

def test_example_from_readme(send=rest_send):
    """README example (create + policy + indexation)"""
    request = {
        "tenant_id": "tenant-001",
        "calculation_instructions": {
            "mutations": [
                {
                    "mutation_id": "a1111111-1111-1111-1111-111111111111",
                    "mutation_definition_name": "create_dossier",
                    "mutation_type": "DOSSIER_CREATION",
                    "actual_at": "2020-01-01",
                    "mutation_properties": {
                        "dossier_id": "d2222222-2222-2222-2222-222222222222",
                        "person_id": "p3333333-3333-3333-3333-333333333333",
                        "name": "Jane Doe",
                        "birth_date": "1960-06-15"
                    }
                },
                {
                    "mutation_id": "b4444444-4444-4444-4444-444444444444",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2020-01-01",
                    "dossier_id": "d2222222-2222-2222-2222-222222222222",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-A",
                        "employment_start_date": "2000-01-01",
                        "salary": 50000,
                        "part_time_factor": 1.0
                    }
                },
                {
                    "mutation_id": "c5555555-5555-5555-5555-555555555555",
                    "mutation_definition_name": "apply_indexation",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2021-01-01",
                    "dossier_id": "d2222222-2222-2222-2222-222222222222",
                    "mutation_properties": {
                        "percentage": 0.03
                    }
                }
            ]
        }
    }

    data, elapsed_ms = send(request)
    meta = data.get("calculation_metadata", {})
    result = data.get("calculation_result", {})
    end = result.get("end_situation", {})
    sit = end.get("situation", {})
    dossier = sit.get("dossier") or {}

    checks = [
        ("outcome SUCCESS", meta.get("calculation_outcome") == "SUCCESS"),
        ("messages empty", result.get("messages") == []),
        ("3 mutations returned", len(result.get("mutations", [])) == 3),
        ("end mutation_index=2", end.get("mutation_index") == 2),
        ("dossier_id correct", dossier.get("dossier_id") == "d2222222-2222-2222-2222-222222222222"),
        ("status ACTIVE", dossier.get("status") == "ACTIVE"),
        ("1 person", len(dossier.get("persons", [])) == 1),
        ("1 policy", len(dossier.get("policies", [])) == 1),
        ("policy_id format", dossier.get("policies", [{}])[0].get("policy_id") == "d2222222-2222-2222-2222-222222222222-1"),
        ("salary=51500 after 3%", abs(dossier.get("policies", [{}])[0].get("salary", 0) - 51500) < 0.01),
        ("initial dossier=null", result.get("initial_situation", {}).get("situation", {}).get("dossier") is None),
    ]

    ok = True
    for name, passed in checks:
        if not passed:
            ok = False
    return ok, elapsed_ms, checks


def test_create_dossier_only(send=rest_send):
    """Create dossier only"""
    request = {
        "tenant_id": "tenant-002",
        "calculation_instructions": {
            "mutations": [
                {
                    "mutation_id": "11111111-0000-0000-0000-000000000001",
                    "mutation_definition_name": "create_dossier",
                    "mutation_type": "DOSSIER_CREATION",
                    "actual_at": "2023-01-01",
                    "mutation_properties": {
                        "dossier_id": "dddddddd-0000-0000-0000-000000000001",
                        "person_id": "pppppppp-0000-0000-0000-000000000001",
                        "name": "John Smith",
                        "birth_date": "1970-03-20"
                    }
                }
            ]
        }
    }

    data, elapsed_ms = send(request)
    dossier = data["calculation_result"]["end_situation"]["situation"].get("dossier") or {}
    checks = [
        ("outcome SUCCESS", data["calculation_metadata"]["calculation_outcome"] == "SUCCESS"),
        ("dossier created", bool(dossier)),
        ("status ACTIVE", dossier.get("status") == "ACTIVE"),
        ("retirement_date null", dossier.get("retirement_date") is None),
        ("1 person", len(dossier.get("persons", [])) == 1),
        ("person name", (dossier.get("persons") or [{}])[0].get("name") == "John Smith"),
        ("empty policies", dossier.get("policies", []) == []),
        ("mutation_index=0", data["calculation_result"]["end_situation"]["mutation_index"] == 0),
    ]

    ok = True
    for name, passed in checks:
        if not passed:
            ok = False
    return ok, elapsed_ms, checks


def test_error_no_dossier(send=rest_send):
    """Error: add_policy without dossier"""
    request = {
        "tenant_id": "tenant-003",
        "calculation_instructions": {
            "mutations": [
                {
                    "mutation_id": "22222222-0000-0000-0000-000000000001",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2023-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000099",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-X",
                        "employment_start_date": "2020-01-01",
                        "salary": 40000,
                        "part_time_factor": 1.0
                    }
                }
            ]
        }
    }

    data, elapsed_ms = send(request)
    meta = data["calculation_metadata"]
    result = data["calculation_result"]
    checks = [
        ("outcome FAILURE", meta["calculation_outcome"] == "FAILURE"),
        ("has CRITICAL message", any(m["level"] == "CRITICAL" for m in result["messages"])),
        ("DOSSIER_NOT_FOUND code", any(m["code"] == "DOSSIER_NOT_FOUND" for m in result["messages"])),
        ("end dossier=null", result["end_situation"]["situation"].get("dossier") is None),
    ]

    ok = True
    for name, passed in checks:
        if not passed:
            ok = False
    return ok, elapsed_ms, checks


def test_full_flow_with_retirement(send=rest_send):
    """Full flow with retirement"""
    request = {
        "tenant_id": "tenant-004",
        "calculation_instructions": {
            "mutations": [
                {
                    "mutation_id": "aaaaaaaa-0000-0000-0000-000000000001",
                    "mutation_definition_name": "create_dossier",
                    "mutation_type": "DOSSIER_CREATION",
                    "actual_at": "2000-01-01",
                    "mutation_properties": {
                        "dossier_id": "dddddddd-0000-0000-0000-000000000002",
                        "person_id": "pppppppp-0000-0000-0000-000000000002",
                        "name": "Alice Example",
                        "birth_date": "1960-01-01"
                    }
                },
                {
                    "mutation_id": "bbbbbbbb-0000-0000-0000-000000000001",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2000-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000002",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-A",
                        "employment_start_date": "2000-01-01",
                        "salary": 50000,
                        "part_time_factor": 1.0
                    }
                },
                {
                    "mutation_id": "bbbbbbbb-0000-0000-0000-000000000002",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2010-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000002",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-B",
                        "employment_start_date": "2010-01-01",
                        "salary": 60000,
                        "part_time_factor": 0.8
                    }
                },
                {
                    "mutation_id": "cccccccc-0000-0000-0000-000000000001",
                    "mutation_definition_name": "calculate_retirement_benefit",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2025-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000002",
                    "mutation_properties": {
                        "retirement_date": "2025-01-01"
                    }
                }
            ]
        }
    }

    data, elapsed_ms = send(request)
    meta = data["calculation_metadata"]
    result = data["calculation_result"]
    dossier = result["end_situation"]["situation"].get("dossier") or {}
    policies = dossier.get("policies", [])

    checks = [
        ("outcome SUCCESS", meta["calculation_outcome"] == "SUCCESS"),
        ("status RETIRED", dossier.get("status") == "RETIRED"),
        ("retirement_date set", dossier.get("retirement_date") == "2025-01-01"),
        ("2 policies", len(policies) == 2),
        ("policy1 pension ~24625", abs((policies[0] if policies else {}).get("attainable_pension", 0) - 24625) < 5),
        ("policy2 pension ~14775", abs((policies[1] if len(policies) > 1 else {}).get("attainable_pension", 0) - 14775) < 5),
        ("4 mutations returned", len(result["mutations"]) == 4),
    ]

    ok = True
    for name, passed in checks:
        if not passed:
            ok = False
    return ok, elapsed_ms, checks


def test_not_eligible_retirement(send=rest_send):
    """Error: NOT_ELIGIBLE retirement"""
    request = {
        "tenant_id": "tenant-005",
        "calculation_instructions": {
            "mutations": [
                {
                    "mutation_id": "eeeeeeee-0000-0000-0000-000000000001",
                    "mutation_definition_name": "create_dossier",
                    "mutation_type": "DOSSIER_CREATION",
                    "actual_at": "2020-01-01",
                    "mutation_properties": {
                        "dossier_id": "dddddddd-0000-0000-0000-000000000005",
                        "person_id": "pppppppp-0000-0000-0000-000000000005",
                        "name": "Young Worker",
                        "birth_date": "1990-01-01"
                    }
                },
                {
                    "mutation_id": "eeeeeeee-0000-0000-0000-000000000002",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2020-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000005",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-A",
                        "employment_start_date": "2020-01-01",
                        "salary": 40000,
                        "part_time_factor": 1.0
                    }
                },
                {
                    "mutation_id": "eeeeeeee-0000-0000-0000-000000000003",
                    "mutation_definition_name": "calculate_retirement_benefit",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2025-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000005",
                    "mutation_properties": {
                        "retirement_date": "2025-01-01"
                    }
                }
            ]
        }
    }

    data, elapsed_ms = send(request)
    meta = data["calculation_metadata"]
    result = data["calculation_result"]
    end_dossier = result["end_situation"]["situation"].get("dossier") or {}
    checks = [
        ("outcome FAILURE", meta["calculation_outcome"] == "FAILURE"),
        ("has CRITICAL message", any(m["level"] == "CRITICAL" for m in result["messages"])),
        ("NOT_ELIGIBLE code", any(m["code"] == "NOT_ELIGIBLE" for m in result["messages"])),
        ("dossier still ACTIVE", end_dossier.get("status") == "ACTIVE"),
    ]

    ok = True
    for name, passed in checks:
        if not passed:
            ok = False
    return ok, elapsed_ms, checks


def test_indexation_with_filters(send=rest_send):
    """Indexation with filters"""
    request = {
        "tenant_id": "tenant-006",
        "calculation_instructions": {
            "mutations": [
                {
                    "mutation_id": "ffffffff-0000-0000-0000-000000000001",
                    "mutation_definition_name": "create_dossier",
                    "mutation_type": "DOSSIER_CREATION",
                    "actual_at": "2000-01-01",
                    "mutation_properties": {
                        "dossier_id": "dddddddd-0000-0000-0000-000000000006",
                        "person_id": "pppppppp-0000-0000-0000-000000000006",
                        "name": "Filter Test",
                        "birth_date": "1960-01-01"
                    }
                },
                {
                    "mutation_id": "ffffffff-0000-0000-0000-000000000002",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2000-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000006",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-A",
                        "employment_start_date": "2000-01-01",
                        "salary": 40000,
                        "part_time_factor": 1.0
                    }
                },
                {
                    "mutation_id": "ffffffff-0000-0000-0000-000000000003",
                    "mutation_definition_name": "add_policy",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2010-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000006",
                    "mutation_properties": {
                        "scheme_id": "SCHEME-B",
                        "employment_start_date": "2010-01-01",
                        "salary": 50000,
                        "part_time_factor": 1.0
                    }
                },
                {
                    "mutation_id": "ffffffff-0000-0000-0000-000000000004",
                    "mutation_definition_name": "apply_indexation",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2021-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000006",
                    "mutation_properties": {
                        "percentage": 0.10,
                        "scheme_id": "SCHEME-A"
                    }
                },
                {
                    "mutation_id": "ffffffff-0000-0000-0000-000000000005",
                    "mutation_definition_name": "apply_indexation",
                    "mutation_type": "DOSSIER",
                    "actual_at": "2022-01-01",
                    "dossier_id": "dddddddd-0000-0000-0000-000000000006",
                    "mutation_properties": {
                        "percentage": 0.05,
                        "effective_before": "2005-01-01"
                    }
                }
            ]
        }
    }

    data, elapsed_ms = send(request)
    policies = data["calculation_result"]["end_situation"]["situation"].get("dossier", {}).get("policies", [])

    checks = [
        ("policy1 salary ~46200", abs((policies[0] if policies else {}).get("salary", 0) - 46200) < 0.01),
        ("policy2 salary unchanged 50000", abs((policies[1] if len(policies) > 1 else {}).get("salary", 0) - 50000) < 0.01),
    ]

    ok = True
    for name, passed in checks:
        if not passed:
            ok = False
    return ok, elapsed_ms, checks


# ── Test registry ──

ALL_TESTS = [
    test_example_from_readme,
    test_create_dossier_only,
    test_error_no_dossier,
    test_full_flow_with_retirement,
    test_not_eligible_retirement,
    test_indexation_with_filters,
]


# ── Runners ──

def run_test_verbose(test_fn, send):
    """Run a test with verbose output."""
    name = test_fn.__doc__ or test_fn.__name__
    try:
        ok, elapsed_ms, checks = test_fn(send)
        print(f"  {name}: {elapsed_ms:.2f}ms")
        for check_name, passed in checks:
            status = "PASS" if passed else "FAIL"
            print(f"    [{status}] {check_name}")
        return ok, elapsed_ms
    except Exception as e:
        print(f"  {name}: ERROR - {e}")
        return False, None


def run_test_silent(test_fn, send):
    """Run a test without printing. Returns (name, ok, elapsed_ms, err)."""
    name = test_fn.__doc__ or test_fn.__name__
    try:
        ok, elapsed_ms, _ = test_fn(send)
        return (name, ok, elapsed_ms, None)
    except Exception as e:
        return (name, False, None, str(e))


def run_suite(tests, send, iterations, max_parallel):
    """Run test suite, return {test_name: [elapsed_ms, ...]} and (passed, failed) counts."""
    all_timings = {(t.__doc__ or t.__name__): [] for t in tests}
    total_passed = 0
    total_failed = 0

    jobs = [(i, t) for i in range(iterations) for t in tests]
    wall_start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=max_parallel) as executor:
        futures = {executor.submit(run_test_silent, t, send): (i, t) for i, t in jobs}
        for future in as_completed(futures):
            name, ok, elapsed_ms, err = future.result()
            if err:
                total_failed += 1
            else:
                all_timings[name].append(elapsed_ms)
                if ok:
                    total_passed += 1
                else:
                    total_failed += 1

    wall_elapsed = (time.perf_counter() - wall_start) * 1000
    return all_timings, total_passed, total_failed, wall_elapsed


def print_stats(all_timings, total_passed, total_failed, wall_elapsed, iterations, num_tests):
    """Print statistics."""
    print(f"\n{'=' * 70}")
    print(f"  REST: {total_passed} passed, {total_failed} failed "
          f"({iterations}x{num_tests} = {iterations * num_tests} requests)")
    print(f"{'=' * 70}")

    if iterations == 1:
        total_ms = 0
        for name, times in all_timings.items():
            if times:
                total_ms += times[0]
                print(f"  {name:<45} {times[0]:>8.2f}ms")
            else:
                print(f"  {name:<45} {'ERROR':>8}")
        print(f"  {'-'*45} {'-'*8}")
        print(f"  {'Average':<45} {total_ms/max(num_tests,1):>8.2f}ms")
        print(f"  {'Wall time':<45} {wall_elapsed:>8.2f}ms")
    else:
        print(f"  {'Test':<32} {'Avg':>8} {'Min':>8} {'Max':>8} {'P50':>8} {'P95':>8}")
        print(f"  {'-'*32} {'-'*8} {'-'*8} {'-'*8} {'-'*8} {'-'*8}")
        all_times = []
        for name, times in all_timings.items():
            if not times:
                print(f"  {name:<32} {'ERROR':>8}")
                continue
            all_times.extend(times)
            avg = statistics.mean(times)
            mn = min(times)
            mx = max(times)
            p50 = statistics.median(times)
            p95 = sorted(times)[int(len(times) * 0.95)]
            print(f"  {name:<32} {avg:>6.2f}ms {mn:>6.2f}ms {mx:>6.2f}ms {p50:>6.2f}ms {p95:>6.2f}ms")
        print(f"  {'-'*32} {'-'*8} {'-'*8} {'-'*8} {'-'*8} {'-'*8}")
        if all_times:
            print(f"  {'Overall':<32} {statistics.mean(all_times):>6.2f}ms {min(all_times):>6.2f}ms "
                  f"{max(all_times):>6.2f}ms {statistics.median(all_times):>6.2f}ms "
                  f"{sorted(all_times)[int(len(all_times)*0.95)]:>6.2f}ms")
        print(f"\n  Wall time: {wall_elapsed:.2f}ms")
        if wall_elapsed > 0 and all_times:
            print(f"  Throughput: {len(all_times) / (wall_elapsed / 1000):.0f} req/s")


# ── Main ──

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test Pension Calculation Engine API")
    parser.add_argument("-n", "--iterations", type=int, default=1, help="Number of iterations (default: 1)")
    parser.add_argument("-p", "--parallel", type=int, default=20, help="Max parallel requests (default: 20)")
    args = parser.parse_args()

    iterations = args.iterations
    max_parallel = args.parallel
    tests = ALL_TESTS
    num_tests = len(tests)

    if iterations == 1:
        print(f"\nRunning {num_tests} tests via REST...\n")
        total_passed = 0
        total_failed = 0
        for t in tests:
            ok, elapsed_ms = run_test_verbose(t, rest_send)
            if ok:
                total_passed += 1
            else:
                total_failed += 1
        print(f"\n{total_passed} passed, {total_failed} failed")
        sys.exit(1 if total_failed > 0 else 0)
    else:
        print(f"\nREST: {iterations}x{num_tests} requests, {max_parallel} parallel\n")
        timings, passed, failed, wall = run_suite(tests, rest_send, iterations, max_parallel)
        print_stats(timings, passed, failed, wall, iterations, num_tests)
        sys.exit(1 if failed > 0 else 0)
