# SCHEDULER POLICY — Goal 007

## Fixed production defaults

```text
signal sources/profile: 16
signal TTL max: 24h
demotion grace: 2s
transition retry: 1s exponential, max 30s
ACTIVE cadence: 100ms
NEARBY cadence: 250ms
WARM cadence: 1s
BACKGROUND cadence: 10s
SLEEPING: no due work
```

## Configured guards

```text
MaxScheduledPhantomProfiles=10000
PhantomSchedulerPulseMillis=100
PhantomSchedulerProfilesPerPulse=128
```

## Overload

Queue occupancy selects NORMAL/ELEVATED/HIGH/CRITICAL. Only WARM and BACKGROUND
cadence is multiplied 1/2/4/8. State is never automatically demoted.

## Fairness

Ready profiles are coalesced. Due ordering uses logical due time, rotating
fairness sequence and profile ID. A processed profile is rescheduled into a
future cohort and cannot continuously reinsert ahead of still-due peers.
