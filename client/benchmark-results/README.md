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
- **This machine can show a change of about 3% or more, within one sitting.** A change smaller than that has not been shown to move the frame rate. Between sittings it drifts much further (see the profile runs below), so this baseline is a picture of where the frame goes, not the "before" for a later change.
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

### Where 1.8.9's terrain setup goes (#45), 7 October 2026

`1.8.9-profile-20261007-221536`, the same scene and laptop, with the profile five levels deep and the culling's block scan marked out:

| Section | Share |
| --- | --- |
| Terrain setup's culling: the chunk-visibility search | 13.9% |
| Terrain setup's culling: the scan of the camera's chunk section (`open_faces`) | 6.5% |
| Clouds | 14.8% |

So the baseline's 20% of terrain setup is two costs, and clouds are the largest single one. That is why #45 is faster clouds.

This run averaged 372.9 FPS, against the baseline's 440 an hour earlier, on the same build: the drift between sittings that the client README warns of.

### Faster clouds (#45), 8 October 2026

The same laptop, back to back in one sitting, with `faster-clouds.enabled` switched between runs:

| Run | Faster clouds | Average | Uncertainty | 1% low | Clouds' share |
| --- | --- | --- | --- | --- | --- |
| `1.8.9-clouds-off-20261008-010535` | off | 459.0 FPS | ±0.5% | 220.9 FPS | 17.4% |
| `1.8.9-clouds-on-20261008-010914` | on | 513.4 FPS | ±1.0% | 235.4 FPS | 9.9% |
| `1.8.9-clouds-off-20261008-011253` | off | 458.8 FPS | ±1.0% | 228.0 FPS | 17.5% |
| `1.8.9-clouds-on-20261008-011630` | on | 514.0 FPS | ±0.6% | 237.9 FPS | 9.8% |

- **Faster clouds is 11.9% to 12.0% faster here**, against at most 2.8% for twice the combined uncertainty of any pair.
- **Drift does not explain it.** The runs alternate, and each setting agrees with itself to 0.1%.
- **The clouds' own time halved,** from about 0.38 ms to 0.19 ms a frame.
- **Building the clouds was the cost, not drawing them.** A first version also put each pass into one draw instead of 64. It measured about 15% faster the evening before, but Mesa's software renderer on CI then settled a few dozen ties between cloud faces at equal depth the other way. So the shipped version draws tile by tile as the game does, and keeps most of the gain.
- **It only helps with fancy clouds,** the game's default. With clouds on Fast or Off, the game never reaches the code it replaces.
