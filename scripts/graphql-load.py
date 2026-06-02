#!/usr/bin/env python3
"""
graphql-load.py  —  GraphQL arithmetic load generator (add / subtract / multiply / divide)
Usage: python3 graphql-load.py -r 10000 -c 50
"""

import argparse
import http.client
import json
import random
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlparse

# ── ANSI colors ───────────────────────────────────────────────────────────────
G = '\033[0;32m'; R = '\033[0;31m'; C = '\033[0;36m'; W = '\033[1;37m'; N = '\033[0m'

# ── Thread-local HTTP connection (keep-alive, one conn per thread) ─────────────
_local = threading.local()

def _conn(host: str, port: int) -> http.client.HTTPConnection:
    if not hasattr(_local, 'conn'):
        _local.conn = http.client.HTTPConnection(host, port)
    return _local.conn

# ── Operations ────────────────────────────────────────────────────────────────
OPERATIONS = ["add", "subtract", "multiply", "divide"]

# ── Random operands ───────────────────────────────────────────────────────────
#   divide: b integer part >= 1  →  b never zero
#   others: full 0–999 range for both operands
def operands(op: str) -> tuple[str, str]:
    a = f"{random.randint(0,  999)}.{random.randint(0, 999):03d}"
    b_int = random.randint(1, 1000) if op == "divide" else random.randint(0, 999)
    b = f"{b_int}.{random.randint(0, 999):03d}"
    return a, b

# ── Single HTTP request (retries once on stale keep-alive connection) ─────────
def fire(host: str, port: int, path: str, timeout: int) -> tuple[int, str]:
    op     = random.choice(OPERATIONS)
    a, b   = operands(op)
    body   = json.dumps({"query": f'query MyQuery {{ {op}(a: "{a}", b: "{b}") }}'})
    hdrs   = {"Content-Type": "application/json", "Connection": "keep-alive"}

    for _ in range(2):
        try:
            conn = _conn(host, port)
            conn.request("POST", path, body=body, headers=hdrs)
            resp = conn.getresponse()
            resp.read()          # must drain before reusing connection
            return resp.status, op
        except Exception:
            _local.conn = None   # force reconnect on next attempt
    return 0, op

# ── Progress bar (runs in its own thread) ────────────────────────────────────
def monitor(counter: list[int], total: int, t0: float, stop: threading.Event) -> None:
    while not stop.wait(0.5):
        done    = counter[0]
        elapsed = max(time.monotonic() - t0, 1e-9)
        pct     = done * 100 // total
        rps     = int(done / elapsed)
        bar     = '#' * (pct // 4) + '-' * (25 - pct // 4)
        print(f"\r  {C}[{G}{bar}{C}]{N}  {done:5d}/{total}  {pct:3d}%  ~{rps:4d} req/s   ",
              end='', flush=True)

# ── CLI ───────────────────────────────────────────────────────────────────────
def parse() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="graphql-load.py",
        description="GraphQL arithmetic load generator (add / subtract / multiply / divide)"
    )
    p.add_argument("-e", "--endpoint",    default="http://localhost:8080/graphql", metavar="URL")
    p.add_argument("-r", "--requests",    default=10_000, type=int, metavar="N")
    p.add_argument("-c", "--concurrency", default=50,     type=int, metavar="N")
    p.add_argument("-t", "--timeout",     default=10,     type=int, metavar="S")
    return p.parse_args()

# ── Main ──────────────────────────────────────────────────────────────────────
def main() -> None:
    args = parse()
    url  = urlparse(args.endpoint)
    host = url.hostname
    port = url.port or (443 if url.scheme == "https" else 80)
    path = url.path or "/"

    print(f"{C}=================================================={N}")
    print(f"{C}  GraphQL Arithmetic Load Generator  [Python]{N}")
    print(f"{C}=================================================={N}")
    print(f"  {'Endpoint:':<14} {W}{args.endpoint}{N}")
    print(f"  {'Requests:':<14} {W}{args.requests}{N}")
    print(f"  {'Concurrency:':<14} {W}{args.concurrency}{N}")
    print(f"  {'Operations:':<14} {W}{', '.join(OPERATIONS)}{N}")
    print()

    results  : list[int]        = []
    op_counts: dict[str, int]   = {op: 0 for op in OPERATIONS}
    counter  : list[int]        = [0]
    lock     = threading.Lock()
    t0       = time.monotonic()
    stop_evt = threading.Event()

    mon = threading.Thread(target=monitor, args=(counter, args.requests, t0, stop_evt), daemon=True)
    mon.start()

    def worker(_: int) -> int:
        status, op = fire(host, port, path, args.timeout)
        with lock:
            results.append(status)
            op_counts[op] += 1
            counter[0] = len(results)
        return status

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        pool.map(worker, range(args.requests))

    stop_evt.set()
    mon.join()

    elapsed = max(time.monotonic() - t0, 1e-9)
    success = results.count(200)
    fail    = len(results) - success
    rps     = int(args.requests / elapsed)

    print(f"\r  {C}[{G}{'#' * 25}{N}]  {args.requests:5d}/{args.requests}  100%  done!                      ")
    print()
    print(f"{C}=================================================={N}")
    print(f"  {G}{'OK 200:':<12} {success}{N}")
    print(f"  {R}{'Non-200:':<12} {fail}{N}")
    print(f"  {'Duration:':<12} {elapsed:.1f}s  (~{rps} req/s)")
    print(f"  {'Operations:'}")
    for op, count in op_counts.items():
        pct = count * 100 // args.requests
        print(f"    {op:<12} {count:>6}  ({pct:>2}%)")
    print(f"{C}=================================================={N}")

if __name__ == "__main__":
    main()
