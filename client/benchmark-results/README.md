# Frame-time results

Results from the frame-time measurement. How to run it and how to read the results are in `client/README.md`, under *Measuring frame time*.

Every run writes two files here:

- `<target>-<label>-<time>.json`: the summary;
- `-frames.csv`: every frame's time.

A run that decides something is committed: a baseline, or the before and after for an optimisation or for Lithium. Its pull request names the files it relies on.

They are only comparable with runs from the same machine. The `machine` block in each file says which machine that was.

## Recorded

### The 1.8.9 baseline (#42), 7 October 2026

The maintainer's laptop: a Ryzen 7 8845HS with an RTX 4050 Laptop GPU (driver 596.56), Windows 11, plugged in, Power mode set to Best performance.

| Run | Average | Uncertainty | 1% low |
| --- | --- | --- | --- |
| `1.8.9-baseline-20261007-213316` | 440.9 FPS | ±0.5% | 207.0 FPS |
| `1.8.9-baseline-20261007-213709` | 439.4 FPS | ±1.2% | 202.2 FPS |

- **The two runs agree.** They are 0.3% apart, well inside twice their combined uncertainty (2.6%).
- **This machine can show a change of about 3% or more.** A change smaller than that has not been shown to move the frame rate.
- **One run was left out.** A first run, straight after switching the power mode, came out at ±2.8%, over the 2% limit. It marked itself not comparable, so it isn't kept.

The profile, from the game's own profiler, as a share of the frame:

| Section | Share |
| --- | --- |
| Rendering the level | 66% |
| Terrain setup: finding and rebuilding chunks | 20% |
| Terrain: drawing chunks | 17% |
| Clouds | 17% |
| Entities | 4% |
| Translucent | 3% |
| Waiting on the display | 23% |
| The F3 chart itself | 7% |

The F3 chart is only on while profiling. Terrain setup, terrain and clouds together are over half the frame, which is where #45 starts.
