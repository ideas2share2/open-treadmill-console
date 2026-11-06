# Estimated active calories

The app estimates energy from weight, reported speed, incline and valid moving time. It does not measure oxygen use or require a heart-rate sensor. Values are a rough comparison aid, not a measured metabolic result.

The base walking oxygen-cost terms are `0.1 × speed + 1.8 × speed × grade`. The running terms are `0.2 × speed + 0.9 × speed × grade`. Speed is metres/minute; grade is a fraction. Multiplication by body kg/200 converts these active terms into estimated kcal/min. The standard resting3.5ml/kg/min term is excluded, so these are **active**, not total, calories.

Primary reference: [manufacturer service instructions with ACSM treadmill equations](https://support.lifefitness.com/hc/es-419/article_attachments/27331725370647). Walking conditions also appear in the [Compendium of Physical Activities](https://pacompendium.com/walking/).

## Explicit approximations

- The walk/jog transition is uncertain. The implementation blends the equations over3.7–5mph to avoid a sudden rate jump; this interpolation is an implementation choice, not a separately validated gait measurement.
- Negative incline uses a level-ground approximation. The uphill equation should not be treated as a validated downhill model.
- Very slow walking, unusual gait, supported/handrail walking, fitness differences and inaccurate machine settings can change real expenditure.
- Age, sex and height are stored locally for the profile, but do not change this particular formula. They are not used to imply precision the model does not have.
- A supplied age below18 disables the adult estimate. Blank age uses the standard adult assumption. Missing weight leaves the estimate unavailable.

## Persistence

The eligible weight and model version are captured when a new workout begins. Later weight/profile edits do not rewrite past calorie estimates. Only fresh, contiguous Running intervals accumulate energy; paused, stale and inactive time does not. Summary values use the existing transactional save cadence.

Older workouts without a contemporaneous weight/model remain unavailable rather than being recomputed using today's weight. A zero estimate with a valid model is different from unavailable. Tests cover reference values, bounds, transition continuity, pauses, gaps, inactivity and recovery.
