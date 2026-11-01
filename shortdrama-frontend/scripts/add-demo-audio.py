"""Create original, quiet demo music and mux it without re-encoding video."""
from array import array
import json
import math
from pathlib import Path
import subprocess
import shutil
import wave

PROJECT = Path(__file__).resolve().parents[2]
MEDIA = PROJECT / "shortdrama-backend" / "media-source" / "demo"
OUTPUT = MEDIA / "edit"
PROTECTED = PROJECT / "shortdrama-backend" / "media" / "demo"
OUTPUT.mkdir(exist_ok=True)
PROTECTED.mkdir(parents=True, exist_ok=True)
SAMPLE_RATE = 44100

for index in range(1, 5):
    source = MEDIA / f"drama-{index}.mp4"
    metadata = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(source)
    ]))
    duration = float(metadata["format"]["duration"])
    samples = array("h")
    # An original four-chord ambient motif. No downloaded or licensed recording.
    chords = [(130.81, 164.81, 196), (110, 130.81, 164.81),
              (87.31, 110, 130.81), (98, 123.47, 146.83)]
    transpose = 2 ** ((index - 1) * 2 / 12)
    for sample in range(round(duration * SAMPLE_RATE)):
        t = sample / SAMPLE_RATE
        chord_number = min(3, int(t / (duration / 4)))
        chord = chords[chord_number]
        beat = t % 0.75
        note = chord[int(t / 0.75) % 3] * 4 * transpose
        pluck = (1 - math.exp(-beat * 90)) * math.exp(-beat * 5)
        envelope = min(1, t / 0.5, (duration - t) / 0.8)
        local_time = t % (duration / 4)
        pad_envelope = min(1, local_time / 0.15, (duration / 4 - local_time) / 0.15)
        for channel in range(2):
            pad = sum(math.sin(2 * math.pi * frequency * transpose * t + channel * 0.12)
                      for frequency in chord) / 3
            bell = math.sin(2 * math.pi * note * t) + 0.18 * math.sin(4 * math.pi * note * t)
            value = envelope * (0.09 * pad * pad_envelope + 0.11 * bell * pluck)
            samples.append(round(max(-1, min(1, value)) * 32767))
    wav = OUTPUT / f"drama-{index}-music.wav"
    with wave.open(str(wav), "wb") as audio:
        audio.setnchannels(2)
        audio.setsampwidth(2)
        audio.setframerate(SAMPLE_RATE)
        audio.writeframes(samples.tobytes())
    target = OUTPUT / f"drama-{index}-sound.mp4"
    subprocess.run([
        "ffmpeg", "-y", "-v", "error", "-i", str(source), "-i", str(wav),
        "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac",
        "-b:a", "128k", "-af", f"loudnorm=I=-20:TP=-3:LRA=6,afade=t=in:d=0.03,afade=t=out:st={duration-0.03}:d=0.03",
        "-t", str(duration), "-movflags", "+faststart", str(target)
    ], check=True)
    wav.unlink()
    shutil.copyfile(target, PROTECTED / f"drama-{index}.mp4")
    print(f"Created {target.name}: {duration:.2f}s")
