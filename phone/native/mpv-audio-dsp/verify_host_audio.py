"""Measure the MPV FFmpeg DSP algorithm on a supplied desktop FFmpeg.

This is digital sample verification, not Android native/device acceptance.
An older host without alimiter latency compensation only verifies amplitude.
"""
from array import array
import argparse
import json
import math
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    version = subprocess.check_output([args.ffmpeg, "-version"], text=True).splitlines()[0]
    help_text = subprocess.check_output([args.ffmpeg, "-hide_banner", "-h", "filter=alimiter"], text=True)
    latency = ":latency=1" if "latency" in help_text else ""
    rate = 48000
    source = array("f", (0.001 * math.sin(2 * math.pi * 1000 * frame / rate)
                         for frame in range(rate * 2) for _ in range(2)))
    results = []
    for multiplier in [1, 5, 9, 16]:
        graph = (f"aformat=sample_fmts=flt,volume={multiplier}:precision=float,"
                 f"alimiter=limit=0.96:attack=5:release=150:level=0{latency}")
        output = subprocess.run([args.ffmpeg, "-v", "error", "-f", "f32le", "-ar", str(rate), "-ac", "2",
                                 "-i", "pipe:0", "-af", graph, "-f", "f32le", "pipe:1"],
                                input=source.tobytes(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        samples = array("f")
        samples.frombytes(output.stdout)
        middle = samples[rate:rate * 3]
        rms = math.sqrt(sum(value * value for value in middle) / len(middle))
        expected = multiplier * 0.001 / math.sqrt(2)
        assert abs(rms / expected - 1) < 0.001, (multiplier, rms, expected)
        assert max(abs(value) for value in samples) <= 0.960001
        if latency:
            assert len(samples) == len(source), (len(samples), len(source))
        results.append({"multiplier": multiplier, "rms": rms, "expected_rms": expected,
                        "peak": max(abs(value) for value in samples), "frames": len(samples) // 2})
    loud = array("f", (0.5 * math.sin(2 * math.pi * 437 * frame / rate)
                       for frame in range(rate * 2) for _ in range(2)))
    graph = f"aformat=sample_fmts=flt,volume=16:precision=float,alimiter=limit=0.96:attack=5:release=150:level=0{latency}"
    output = subprocess.run([args.ffmpeg, "-v", "error", "-f", "f32le", "-ar", str(rate), "-ac", "2",
                             "-i", "pipe:0", "-af", graph, "-f", "f32le", "pipe:1"],
                            input=loud.tobytes(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    samples = array("f")
    samples.frombytes(output.stdout)
    peak = max(abs(value) for value in samples)
    assert peak <= 0.960001, peak
    normalization = []
    for amplitude in [0.0, 0.0002, 0.02]:
        tone = array("f", (amplitude * math.sin(2 * math.pi * 1000 * frame / rate)
                           for frame in range(rate * 2) for _ in range(2)))
        graph = ("aformat=sample_fmts=flt,"
                 "dynaudnorm=f=50:g=5:p=0.96:m=4:r=0.125:n=1:c=0:b=0:s=0:t=0.00316227766,"
                 f"volume=1:precision=float,alimiter=limit=0.96:attack=5:release=150:level=0{latency}")
        output = subprocess.run([args.ffmpeg, "-v", "error", "-f", "f32le", "-ar", str(rate), "-ac", "2",
                                 "-i", "pipe:0", "-af", graph, "-f", "f32le", "pipe:1"],
                                input=tone.tobytes(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        samples = array("f")
        samples.frombytes(output.stdout)
        normalized_peak = max(abs(value) for value in samples)
        if amplitude <= 0.0002:
            assert normalized_peak <= amplitude * 1.0001 + 1e-10, (amplitude, normalized_peak)
        else:
            assert amplitude * 1.5 < normalized_peak <= amplitude * 4.0001
        assert len(samples) == len(tone), (len(samples), len(tone))
        normalization.append({"source_peak": amplitude, "output_peak": normalized_peak,
                              "frames": len(samples) // 2})
    impulses = array("f", [0.0]) * (rate * 2 * 2)
    positions = [1001, rate * 2 - 1]
    for frame in positions:
        impulses[frame * 2] = impulses[frame * 2 + 1] = 0.01
    output = subprocess.run([args.ffmpeg, "-v", "error", "-f", "f32le", "-ar", str(rate), "-ac", "2",
                             "-i", "pipe:0", "-af", f"volume=5:precision=float,alimiter=limit=0.96:attack=5:release=150:level=0{latency}",
                             "-f", "f32le", "pipe:1"], input=impulses.tobytes(),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    samples = array("f")
    samples.frombytes(output.stdout)
    actual_positions = [index // 2 for index in range(0, len(samples), 2) if abs(samples[index]) > 1e-6]
    if latency:
        assert actual_positions == positions, actual_positions
        assert len(samples) == len(impulses)
    report = {"scope": "Host FFmpeg decoded float PCM; does not execute Android ELF or validate a device output",
              "host_version": version, "latency_option_available": bool(latency),
              "low_signal": results, "loud_16x_output_peak": peak,
              "normalization_gate_and_cap": normalization,
              "impulse_expected_frames": positions, "impulse_output_frames": actual_positions}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
