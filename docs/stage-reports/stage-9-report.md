# Stage 9 · Attendance

## Objective and baseline

Add subject attendance without duplicating course records. Reviewed subject IDs, deletion rules, the shared JSON repository, settings defaults and the Swing navigation.

## Features and files

`AttendanceEntry`, `AttendanceTarget` and their repositories record individual outcomes and per-subject targets. `AttendanceService` assembles subject and overall totals and records corrections. `AttendanceMath` uses integer arithmetic for ceiling and floor calculations. `AttendancePanel` displays history, targets, totals, projection and warnings. Updated the profile, controller, sidebar and subject deletion rule.

## Design and dependencies

The service depends on typed repository interfaces. Percentage display uses floating point while class thresholds use exact arithmetic. A zero target permits unlimited absences. Reaching 100% is impossible once a class has been missed. No dependency was added.

## Verification

97 tests passed, including 17 attendance cases. The initial integration run found a duplicate JSON ID property in the target record. A dedicated repository DTO corrected the format. A native Swing probe verified the dark-theme dashboard, forecast, history and class controls at a narrow window size.

## Limitations and next stage

Class dates and outcomes are stored locally. The projection assumes consecutive attended or missed classes as labeled. Stage 10 uses the same profile and subject IDs for study materials.
