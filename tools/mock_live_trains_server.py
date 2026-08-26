#!/usr/bin/env python3
"""Local-only WinRah live-train mock server.

This server generates synthetic train positions in memory and exposes read-only
endpoints compatible with the WinRah API. It never writes to Supabase or Render.

Examples:
  python3 mock_live_trains_server.py
  python3 mock_live_trains_server.py --host 0.0.0.0 --port 18080 --interval 5

For Android Emulator use: http://10.0.2.2:18080/
For a physical phone use the computer's LAN IP, for example:
  http://192.168.1.20:18080/
"""

from __future__ import annotations

import argparse
import json
import math
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse


# Synthetic fixture coordinates for local testing only. They are not official
# railway geometry and must never be presented as production data.
ROUTES: list[dict[str, Any]] = [
    {
        "trip_id": "mock-trip-alger-east",
        "train_id": "mock-train-alger-01",
        "line_id": "mock-line-alger-east",
        "name": "Mock Alger Est",
        "speed_kmh": 54.0,
        "stops": [
            {"id": "mock-station-alger", "name": "Mock Alger", "lat": 36.7529, "lon": 3.0420},
            {"id": "mock-station-bordj", "name": "Mock Bordj El Kiffan", "lat": 36.7350, "lon": 3.1900},
            {"id": "mock-station-rouiba", "name": "Mock Rouiba", "lat": 36.7380, "lon": 3.2800},
        ],
    },
    {
        "trip_id": "mock-trip-tizi-thennia",
        "train_id": "mock-train-tizi-01",
        "line_id": "mock-line-tizi-thennia",
        "name": "Mock Tizi Ouzou–Thenia",
        "speed_kmh": 48.0,
        "stops": [
            {"id": "mock-station-thenia", "name": "Mock Thenia", "lat": 36.7250, "lon": 3.5560},
            {"id": "mock-station-bois", "name": "Mock Bois des Cars", "lat": 36.6900, "lon": 3.7200},
            {"id": "mock-station-tizi", "name": "Mock Tizi Ouzou", "lat": 36.7118, "lon": 4.0450},
        ],
    },
    {
        "trip_id": "mock-trip-oran-west",
        "train_id": "mock-train-oran-01",
        "line_id": "mock-line-oran-west",
        "name": "Mock Oran Ouest",
        "speed_kmh": 62.0,
        "stops": [
            {"id": "mock-station-oran", "name": "Mock Oran", "lat": 35.6971, "lon": -0.6308},
            {"id": "mock-station-es-senia", "name": "Mock Es Senia", "lat": 35.6500, "lon": -0.6200},
            {"id": "mock-station-ain", "name": "Mock Aïn El Turk", "lat": 35.7400, "lon": -0.7700},
        ],
    },
]


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def interpolate(a: dict[str, Any], b: dict[str, Any], fraction: float) -> tuple[float, float]:
    fraction = max(0.0, min(1.0, fraction))
    return (
        a["lat"] + (b["lat"] - a["lat"]) * fraction,
        a["lon"] + (b["lon"] - a["lon"]) * fraction,
    )


@dataclass
class SimTrain:
    config: dict[str, Any]
    segment: int = 0
    fraction: float = 0.10
    last_update: float = field(default_factory=time.time)
    scenario: str = "normal"
    status: str = "RUNNING"

    def update(self, now: float) -> None:
        elapsed = max(0.0, now - self.last_update)
        self.last_update = now
        stops = self.config["stops"]
        if len(stops) < 2:
            return
        if self.scenario in {"stopped", "emergency"}:
            return
        distance = haversine_m(
            stops[self.segment]["lat"],
            stops[self.segment]["lon"],
            stops[self.segment + 1]["lat"],
            stops[self.segment + 1]["lon"],
        )
        speed_kmh = self.config["speed_kmh"] * (0.35 if self.scenario == "delay" else 1.0)
        segment_seconds = max(1.0, distance / (speed_kmh / 3.6))
        self.fraction += elapsed / segment_seconds
        while self.fraction >= 1.0:
            self.fraction -= 1.0
            self.segment += 1
            if self.segment >= len(stops) - 1:
                self.segment = 0
                self.fraction = 0.05

    def payload(self) -> dict[str, Any]:
        stops = self.config["stops"]
        a, b = stops[self.segment], stops[self.segment + 1]
        lat, lon = interpolate(a, b, self.fraction)
        distance_to_next = haversine_m(lat, lon, b["lat"], b["lon"])
        effective_speed = 0.0 if self.scenario in {"stopped", "emergency"} else self.config["speed_kmh"] * (0.35 if self.scenario == "delay" else 1.0)
        eta_seconds = None if effective_speed == 0 else int(distance_to_next / (effective_speed / 3.6))
        heading = (math.degrees(math.atan2(b["lon"] - a["lon"], b["lat"] - a["lat"])) + 360) % 360
        now = int(time.time() * 1000)
        status = {"normal": "RUNNING", "delay": "DELAYED", "stopped": "STOPPED", "emergency": "EMERGENCY"}[self.scenario]
        return {
            "train_id": self.config["train_id"],
            "trip_id": self.config["trip_id"],
            "line_id": self.config["line_id"],
            "name": self.config["name"],
            "truth": "OBSERVED",
            "status": status,
            "scenario": self.scenario,
            "delay_minutes": 18 if self.scenario == "delay" else 0,
            "alert": "Emergency stop simulated" if self.scenario == "emergency" else None,
            "latitude": round(lat, 6),
            "longitude": round(lon, 6),
            "speed_kmh": round(effective_speed, 1),
            "speed_mps": round(effective_speed / 3.6, 2),
            "heading": round(heading, 1),
            "next_station": {"id": b["id"], "name": b["name"], "latitude": b["lat"], "longitude": b["lon"]},
            "distance_to_next_station_m": round(distance_to_next, 1),
            "eta_seconds": eta_seconds,
            "last_observed_at": now,
            "source_count": 1,
            "mock": True,
        }


class Simulator:
    def __init__(self, interval: float) -> None:
        self.interval = max(0.5, interval)
        self.scenario = "normal"
        self.trains = [SimTrain(config=route, fraction=0.08 + i * 0.22, scenario=self.scenario) for i, route in enumerate(ROUTES)]
        self.lock = threading.RLock()
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._loop, name="mock-train-updater", daemon=True)

    def start(self) -> None:
        self.thread.start()

    def _loop(self) -> None:
        while not self.stop_event.wait(self.interval):
            now = time.time()
            with self.lock:
                for train in self.trains:
                    train.update(now)

    def set_scenario(self, scenario: str) -> None:
        if scenario not in {"normal", "delay", "stopped", "emergency"}:
            raise ValueError("scenario must be normal, delay, stopped, or emergency")
        with self.lock:
            self.scenario = scenario
            for train in self.trains:
                train.scenario = scenario

    def snapshot(self) -> list[dict[str, Any]]:
        with self.lock:
            return [train.payload() for train in self.trains]

    def stations(self) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for route in ROUTES:
            for station in route["stops"]:
                if not any(item["id"] == station["id"] for item in result):
                    result.append({**station, "mock": True})
        return result


SIM: Simulator


class Handler(BaseHTTPRequestHandler):
    server_version = "WinRahMock/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[mock-api] {self.address_string()} - {fmt % args}")

    def _send(self, payload: Any, status: int = 200) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        trains = SIM.snapshot()
        if path == "/scenario":
            return self._send({"scenario": SIM.scenario, "allowed": ["normal", "delay", "stopped", "emergency"], "mock": True})
        if path == "/scenario/set":
            value = parse_qs(parsed.query).get("value", [""])[0].lower()
            try:
                SIM.set_scenario(value)
            except ValueError as exc:
                return self._send({"detail": str(exc), "mock": True}, 400)
            return self._send({"scenario": SIM.scenario, "changed": True, "mock": True})
        if path == "/health":
            return self._send({"status": "ok", "mock": True, "trains": len(trains)})
        if path == "/version":
            return self._send({"service": "winrah-mock-live", "storage": "memory-mock", "mock": True})
        if path == "/trains":
            return self._send(trains)
        if path == "/nearby-trains":
            query = parse_qs(parsed.query)
            try:
                lat = float(query["lat"][0])
                lon = float(query["lon"][0])
                radius = float(query.get("radius", [10000])[0])
            except (KeyError, ValueError):
                return self._send({"detail": "lat and lon are required"}, 400)
            result = [t | {"distance_m": round(haversine_m(lat, lon, t["latitude"], t["longitude"]), 1)}
                      for t in trains if haversine_m(lat, lon, t["latitude"], t["longitude"]) <= radius]
            return self._send(result)
        if path == "/stations":
            return self._send(SIM.stations())
        if path == "/map/stations.geojson":
            features = [{"type": "Feature", "properties": {"id": s["id"], "name": s["name"], "mock": True},
                         "geometry": {"type": "Point", "coordinates": [s["lon"], s["lat"]]}}
                        for s in SIM.stations()]
            return self._send({"type": "FeatureCollection", "features": features})
        if path == "/map/network-lines":
            return self._send({"type": "FeatureCollection", "features": []})
        if path == "/map/railway-segments.geojson":
            features = []
            for route in ROUTES:
                for a, b in zip(route["stops"], route["stops"][1:]):
                    features.append({"type": "Feature", "properties": {"line_id": route["line_id"], "mock": True},
                                     "geometry": {"type": "LineString", "coordinates": [[a["lon"], a["lat"]], [b["lon"], b["lat"]]]}})
            return self._send({"type": "FeatureCollection", "features": features})
        return self._send({"detail": "not_found", "mock": True}, 404)


def main() -> None:
    parser = argparse.ArgumentParser(description="Local WinRah live-train mock API")
    parser.add_argument("--host", default="127.0.0.1", help="Use 0.0.0.0 for a physical phone on the LAN")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--interval", type=float, default=5.0, help="Position update interval in seconds")
    parser.add_argument("--scenario", choices=["normal", "delay", "stopped", "emergency"], default="normal")
    args = parser.parse_args()

    global SIM
    SIM = Simulator(args.interval)
    SIM.set_scenario(args.scenario)
    SIM.start()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"WinRah Mock API: http://{args.host}:{args.port}")
    print(f"Synthetic trains: {len(ROUTES)} | update interval: {args.interval:g}s | scenario: {args.scenario}")
    print("Read-only in-memory mode: no Supabase/Render writes")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping mock server")
    finally:
        SIM.stop_event.set()
        server.server_close()


if __name__ == "__main__":
    main()
