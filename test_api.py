#!/usr/bin/env python3
"""Test script for Pension Calculation Engine - REST and gRPC."""

import requests
import json
import sys
import time
import os
import argparse
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "http://localhost:8080"
GRPC_HOST = "localhost"
GRPC_PORT = 9090
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

def pp(data):
    print(json.dumps(data, indent=2))

# ── Transport abstractions ──

def rest_send(request_dict):
    """Send via REST, return (data_dict, elapsed_ms)."""
    start = time.perf_counter()
    resp = requests.post(f"{BASE_URL}/calculation-requests", json=request_dict)
    elapsed_ms = (time.perf_counter() - start) * 1000
    return resp.json(), elapsed_ms

_grpc_modules = {}

def init_grpc():
    """Compile proto and initialize gRPC channel + stub."""
    global _grpc_modules
    if _grpc_modules:
        return _grpc_modules

    try:
        import grpc
        from grpc_tools import protoc
    except ImportError:
        print("ERROR: grpcio and grpcio-tools required for gRPC mode.")
        print("  pip install grpcio grpcio-tools")
        sys.exit(1)

    out_dir = os.path.join(SCRIPT_DIR, '_generated')
    os.makedirs(out_dir, exist_ok=True)

    proto_dir = os.path.join(SCRIPT_DIR, 'src', 'main', 'proto')

    init_file = os.path.join(out_dir, '__init__.py')
    if not os.path.exists(init_file):
        open(init_file, 'w').close()

    # Include well-known types from grpc_tools package
    import grpc_tools
    well_known = os.path.join(os.path.dirname(grpc_tools.__file__), '_proto')

    result = protoc.main([
        'grpc_tools.protoc',
        f'-I{proto_dir}',
        f'-I{well_known}',
        f'--python_out={out_dir}',
        f'--grpc_python_out={out_dir}',
        os.path.join(proto_dir, 'pension.proto'),
    ])

    if result != 0:
        print(f"ERROR: protoc compilation failed (code {result})")
        sys.exit(1)

    sys.path.insert(0, out_dir)
    import pension_pb2
    import pension_pb2_grpc

    channel = grpc.insecure_channel(f'{GRPC_HOST}:{GRPC_PORT}')
    stub = pension_pb2_grpc.PensionCalculationServiceStub(channel)

    _grpc_modules = {
        'pb2': pension_pb2,
        'pb2_grpc': pension_pb2_grpc,
        'channel': channel,
        'stub': stub,
    }
    print(f"gRPC stubs compiled, channel to {GRPC_HOST}:{GRPC_PORT}")
    return _grpc_modules

def grpc_send(request_dict):
    """Send via gRPC, return (data_dict, elapsed_ms)."""
    from google.protobuf import json_format
    mods = init_grpc()
    proto_req = json_format.ParseDict(request_dict, mods['pb2'].CalculationRequest())
    start = time.perf_counter()
    proto_resp = mods['stub'].Calculate(proto_req)
    elapsed_ms = (time.perf_counter() - start) * 1000
    data = json_format.MessageToDict(
        proto_resp,
        preserving_proto_field_name=True,
        always_print_fields_with_no_presence=True,
    )
    return data, elapsed_ms

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

def run_test_verbose(test_fn, send, transport_label):
    """Run a test with verbose output."""
    name = test_fn.__doc__ or test_fn.__name__
    try:
        ok, elapsed_ms, checks = test_fn(send)
        print(f"  [{transport_label}] {name}: {elapsed_ms:.2f}ms")
        for check_name, passed in checks:
            status = "PASS" if passed else "FAIL"
            print(f"    [{status}] {check_name}")
        return ok, elapsed_ms
    except Exception as e:
        print(f"  [{transport_label}] {name}: ERROR - {e}")
        return False, None


def run_test_silent(test_fn, send):
    """Run a test without printing. Returns (name, ok, elapsed_ms, err)."""
    name = test_fn.__doc__ or test_fn.__name__
    try:
        ok, elapsed_ms, _ = test_fn(send)
        return (name, ok, elapsed_ms, None)
    except Exception as e:
        return (name, False, None, str(e))


def run_suite(tests, send, iterations, max_parallel, transport_label):
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


def print_stats(label, all_timings, total_passed, total_failed, wall_elapsed, iterations, num_tests):
    """Print statistics for a single transport."""
    print(f"\n{'=' * 70}")
    print(f"  {label}: {total_passed} passed, {total_failed} failed "
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


def print_comparison(rest_timings, grpc_timings, iterations):
    """Print side-by-side comparison table."""
    print(f"\n{'=' * 90}")
    print("  REST vs gRPC COMPARISON")
    print(f"{'=' * 90}")

    if iterations == 1:
        print(f"  {'Test':<32} {'REST':>10} {'gRPC':>10} {'Speedup':>10}")
        print(f"  {'-'*32} {'-'*10} {'-'*10} {'-'*10}")
        rest_total = 0
        grpc_total = 0
        for name in rest_timings:
            rt = rest_timings[name][0] if rest_timings.get(name) else None
            gt = grpc_timings[name][0] if grpc_timings.get(name) else None
            r_str = f"{rt:.2f}ms" if rt else "ERROR"
            g_str = f"{gt:.2f}ms" if gt else "ERROR"
            if rt and gt:
                speedup = rt / gt
                s_str = f"{speedup:.2f}x"
                rest_total += rt
                grpc_total += gt
            else:
                s_str = "N/A"
            print(f"  {name:<32} {r_str:>10} {g_str:>10} {s_str:>10}")
        print(f"  {'-'*32} {'-'*10} {'-'*10} {'-'*10}")
        if rest_total and grpc_total:
            speedup = rest_total / grpc_total
            print(f"  {'Average':<32} {rest_total/len(rest_timings):>8.2f}ms {grpc_total/len(grpc_timings):>8.2f}ms {speedup:>8.2f}x")
    else:
        print(f"  {'Test':<28} {'REST Avg':>10} {'gRPC Avg':>10} {'REST P50':>10} {'gRPC P50':>10} {'Speedup':>10}")
        print(f"  {'-'*28} {'-'*10} {'-'*10} {'-'*10} {'-'*10} {'-'*10}")
        all_rest = []
        all_grpc = []
        for name in rest_timings:
            rt = rest_timings.get(name, [])
            gt = grpc_timings.get(name, [])
            if rt and gt:
                r_avg = statistics.mean(rt)
                g_avg = statistics.mean(gt)
                r_p50 = statistics.median(rt)
                g_p50 = statistics.median(gt)
                speedup = r_avg / g_avg if g_avg > 0 else float('inf')
                all_rest.extend(rt)
                all_grpc.extend(gt)
                print(f"  {name:<28} {r_avg:>8.2f}ms {g_avg:>8.2f}ms {r_p50:>8.2f}ms {g_p50:>8.2f}ms {speedup:>8.2f}x")
            else:
                print(f"  {name:<28} {'ERROR':>10} {'ERROR':>10}")
        print(f"  {'-'*28} {'-'*10} {'-'*10} {'-'*10} {'-'*10} {'-'*10}")
        if all_rest and all_grpc:
            r_avg = statistics.mean(all_rest)
            g_avg = statistics.mean(all_grpc)
            r_p50 = statistics.median(all_rest)
            g_p50 = statistics.median(all_grpc)
            speedup = r_avg / g_avg if g_avg > 0 else float('inf')
            print(f"  {'Overall':<28} {r_avg:>8.2f}ms {g_avg:>8.2f}ms {r_p50:>8.2f}ms {g_p50:>8.2f}ms {speedup:>8.2f}x")


# ── Main ──

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test Pension Calculation Engine API")
    parser.add_argument("-n", "--iterations", type=int, default=1, help="Number of iterations (default: 1)")
    parser.add_argument("-p", "--parallel", type=int, default=20, help="Max parallel requests (default: 20)")
    parser.add_argument("--mode", choices=["rest", "grpc", "both"], default="both",
                        help="Transport mode (default: both)")
    args = parser.parse_args()

    iterations = args.iterations
    max_parallel = args.parallel
    mode = args.mode
    tests = ALL_TESTS
    num_tests = len(tests)

    any_failed = False

    # Single-iteration verbose mode
    if iterations == 1 and mode != "both":
        send = rest_send if mode == "rest" else grpc_send
        if mode == "grpc":
            init_grpc()
        label = "REST" if mode == "rest" else "gRPC"
        print(f"\nRunning {num_tests} tests via {label}...\n")
        total_passed = 0
        total_failed = 0
        for t in tests:
            ok, elapsed_ms = run_test_verbose(t, send, label)
            if ok:
                total_passed += 1
            else:
                total_failed += 1
        print(f"\n{total_passed} passed, {total_failed} failed")
        any_failed = total_failed > 0

    elif mode == "rest":
        print(f"\nREST mode: {iterations}x{num_tests} requests, {max_parallel} parallel\n")
        timings, passed, failed, wall = run_suite(tests, rest_send, iterations, max_parallel, "REST")
        print_stats("REST", timings, passed, failed, wall, iterations, num_tests)
        any_failed = failed > 0

    elif mode == "grpc":
        init_grpc()
        print(f"\ngRPC mode: {iterations}x{num_tests} requests, {max_parallel} parallel\n")
        timings, passed, failed, wall = run_suite(tests, grpc_send, iterations, max_parallel, "gRPC")
        print_stats("gRPC", timings, passed, failed, wall, iterations, num_tests)
        any_failed = failed > 0

    else:  # both
        if mode == "both":
            init_grpc()

        print(f"\nBOTH mode: {iterations}x{num_tests} requests each, {max_parallel} parallel\n")

        # Warmup
        if iterations > 1:
            print("Warming up (5 requests each)...")
            for _ in range(5):
                for t in tests[:1]:
                    try:
                        t(rest_send)
                    except:
                        pass
                    try:
                        t(grpc_send)
                    except:
                        pass
            print("Warmup done.\n")

        rest_timings, rest_passed, rest_failed, rest_wall = run_suite(
            tests, rest_send, iterations, max_parallel, "REST")
        grpc_timings, grpc_passed, grpc_failed, grpc_wall = run_suite(
            tests, grpc_send, iterations, max_parallel, "gRPC")

        print_stats("REST", rest_timings, rest_passed, rest_failed, rest_wall, iterations, num_tests)
        print_stats("gRPC", grpc_timings, grpc_passed, grpc_failed, grpc_wall, iterations, num_tests)
        print_comparison(rest_timings, grpc_timings, iterations)

        any_failed = (rest_failed + grpc_failed) > 0

    sys.exit(1 if any_failed else 0)
