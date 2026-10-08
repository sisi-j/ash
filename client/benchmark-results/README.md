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

### Faster view scan (#105), 8 October 2026

The same laptop, back to back in one sitting, with faster clouds on throughout and `faster-view-scan.enabled` switched between runs:

| Run | Faster view scan | Average | Uncertainty | 1% low | Terrain setup's share |
| --- | --- | --- | --- | --- | --- |
| `1.8.9-scan-off-20261008-015824` | off | 513.4 FPS | ±1.2% | 224.0 FPS | 22.4% |
| `1.8.9-scan-on-20261008-020203` | on | 579.2 FPS | ±1.0% | 252.9 FPS | 16.7% |
| `1.8.9-scan-off-20261008-020541` | off | 510.2 FPS | ±0.5% | 227.9 FPS | 22.6% |
| `1.8.9-scan-on-20261008-020919` | on | 579.5 FPS | ±0.9% | 254.8 FPS | 16.7% |

- **Faster view scan is 12.8% to 13.6% faster here**, against at most 3.1% for twice the combined uncertainty of any pair.
- **The scan's own section went** from 7.1% and 7.7% of the frame to under the profile's 1% floor.
- **The gain is more than the section's share.** The scan saved about 0.23 ms a frame where its section was about 0.15 ms. Most likely the rest is the garbage the scan made every frame, which the collector no longer has to clear; that is not measured.
- **This scene is its best case.** The camera turns on the spot, so it never leaves its block and the answer is always reused. A player walking moves into a new block a few times a second, and each move costs one scan, as before.

### Faster chunk search (#104), not shipped, 8 October 2026

The same laptop, back to back in one sitting, with faster clouds and faster view scan on throughout. The candidate kept each section's six neighbours instead of looking them up with five integer divisions, tested each frustum plane from its one deciding corner, and kept the search's list of directions instead of copying it per section. It found exactly the game's sections, in the game's order.

| Run | Faster chunk search | Average | Uncertainty | 1% low | Terrain setup's share |
| --- | --- | --- | --- | --- | --- |
| `1.8.9-search-off-20261008-023649` | off | 588.1 FPS | ±0.8% | 254.8 FPS | 17.2% |
| `1.8.9-search-on-20261008-024028` | on | 596.6 FPS | ±0.8% | 260.8 FPS | 17.3% |
| `1.8.9-search-off-20261008-024406` | off | 595.6 FPS | ±1.4% | 264.2 FPS | 16.5% |
| `1.8.9-search-on-20261008-024744` | on | 589.4 FPS | ±1.0% | 257.8 FPS | 17.3% |

- **No gain.** The two settings overlap, and terrain setup's share did not move, so the candidate was not shipped. The spec ships an optimisation only on a measured gain.
- **What it rules out.** The neighbour lookups, the frustum tests and the copied directions are not where the search's time goes.
- **Where it points instead.** A flight recording of the search (`-Pbench.jfr`) put almost every sample at the step that makes each newly reached section's record: a new object, a new set of directions copied from its parent's, and a new link in the queue. That is the next thing to try, and to measure first.

## FPS marks (#70), 8 October 2026

Each tile's FPS mark comes from these runs, and is written down in `shared/.../perf/FpsMeasurements.java`.

**How.** On each target, in one sitting, every feature on its defaults. Then each feature switched off in turn, with each of those runs between two with everything on. The change is the off run against the average of the two either side. Within ±3% the mark is level.

**What the scene can't show.** It's a spectator turning on the spot: no fight, no keys pressed, no server. A feature's cost there is the cost of having it on, its hooks and its drawing, not of using it.

**Only the summaries are committed** (`.json`, not `-frames.csv`). There are 41 runs, and the marks need only their averages.

### 1.8.9

| Feature | Off | On | Change | Uncertainty |
| --- | --- | --- | --- | --- |
| fps-readout | 600.8 FPS | 596.1 FPS | -0.8% | ±1.2% |
| toggle-sprint | 571.7 FPS | 573.7 FPS | +0.4% | ±1.2% |
| crosshair | 574.3 FPS | 566.8 FPS | -1.3% | ±1.2% |
| hit-indicator | 576.7 FPS | 576.9 FPS | +0.0% | ±1.1% |
| freelook | 576.2 FPS | 577.9 FPS | +0.3% | ±1.2% |
| snaplook | 572.6 FPS | 579.6 FPS | +1.2% | ±1.3% |
| ping-readout | 577.1 FPS | 582.3 FPS | +0.9% | ±1.3% |
| hit-colour | 572.7 FPS | 576.5 FPS | +0.7% | ±1.3% |
| faster-clouds | 518.9 FPS | 573.0 FPS | **+10.4%** | ±1.1% |
| faster-view-scan | 485.6 FPS | 573.5 FPS | **+18.1%** | ±0.8% |

Faster clouds and faster view scan raise the frame rate. Every other feature is level.

The FPS readout was measured again in a second sitting. In the first, its "before" run was the sitting's very first, which came out at 522.7 FPS against 560 to 587 for every other run with everything on. A cold first run is now a throwaway, `marks-warm`.

### 1.21.11

| Feature | Off | On | Change | Uncertainty |
| --- | --- | --- | --- | --- |
| fps-readout | 712.8 FPS | 704.6 FPS | -1.2% | ±1.3% |
| toggle-sprint | 690.8 FPS | 704.8 FPS | +2.0% | ±1.2% |
| crosshair | 702.6 FPS | 701.8 FPS | -0.1% | ±1.1% |
| hit-indicator | 699.0 FPS | 700.9 FPS | +0.3% | ±1.0% |
| freelook | 688.7 FPS | 700.1 FPS | +1.7% | ±1.1% |
| snaplook | 708.2 FPS | 702.5 FPS | -0.8% | ±1.3% |
| ping-readout | 700.3 FPS | 698.0 FPS | -0.3% | ±1.1% |
| hit-colour | 713.3 FPS | 702.7 FPS | -1.5% | ±0.8% |

Every feature is level.

**Four were measured again in a second sitting.** In the first, three runs came out near 870 FPS where every other was near 700:
- the FPS readout off;
- toggle sprint off (which also marked itself not comparable);
- one run with everything on, which snaplook and the ping readout leaned on.

None depended on a feature, and nothing recorded explains them. In the second sitting, every run drew 246 to 255 sections, logged at its end, and none came out fast. So the fast runs left out remain unexplained; if they come back, the section count is the first thing to compare.

## Lithium on 1.21.11 (#44), 8 October 2026

The same laptop, in one sitting: Lithium `0.21.4+mc1.21.11` off, on, off, on. First in singleplayer, then on a vanilla dedicated server on this machine (127.0.0.1, `-Pbench.server`), each after a throwaway warm-up run. ash's features were on their defaults throughout. Only the summaries are committed.

| Run | Lithium | Average | Uncertainty | 1% low |
| --- | --- | --- | --- | --- |
| singleplayer, first pair | off | 626.1 FPS | ±0.5% | 255.8 FPS |
| | on | 627.1 FPS | ±1.1% | 248.1 FPS |
| singleplayer, second pair | off | 656.5 FPS | ±0.9% | 274.6 FPS |
| | on | 671.5 FPS | ±0.6% | 275.5 FPS |
| server, first pair | off | 637.1 FPS | ±0.6% | 259.6 FPS |
| | on | 627.3 FPS | ±0.8% | 244.6 FPS |
| server, second pair | off | 622.2 FPS | ±0.5% | 252.8 FPS |
| | on | 644.4 FPS | ±0.7% | 266.3 FPS |

The files, in the table's order:
- `1.21.11-lithium-sp-off-20261008-141642`, `-sp-on-20261008-142023`, `-sp-off-20261008-142450`, `-sp-on-20261008-142935`;
- `1.21.11-lithium-server-off-20261008-143654`, `-server-on-20261008-144031`, `-server-off-20261008-144402`, `-server-on-20261008-144749`.

- **No measured gain.** Pair by pair, Lithium moved the frame rate by +0.2% and +2.3% in singleplayer, and by -1.5% and +3.6% on the server. Averaged, it's +1.2% and +1.0%, inside the ±3% the spec counts as no change, with pairs that disagree with each other by more than either moves.
- **What this doesn't measure.** It measures frame time only. Lithium's own claims are about the built-in server's tick time, which a laptop with cores to spare doesn't feel as frames. Tick time on a weaker machine is a different question from the one #44 asked.
- **What follows.** Lithium isn't bundled, and ADR-0013 is amended with these numbers.
