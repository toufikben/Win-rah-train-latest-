# Roadmap

Working plan for Win rah train. Items are ordered; nothing here overrides the QA
gates in `qa-winrah/qa/05-qa-report.md`.

## Now — one armed alert, always visible

The app keeps a single armed alert (station + direction). Today the hero button
is the only place that says whether it is armed, so changing station or
direction hides a running alert.

This milestone adds:

- a persistent banner on Home showing the armed station and direction,
- cancel from that banner even when the view shows a different pair,
- an explicit "does not follow your current selection" warning,
- a "show" action that jumps the view back to the armed pair,
- replacing the alert while another pair is armed, with an undo snackbar.

The alert service, its notification IDs and its persistence keys are unchanged.

## Later — several alerts at once (milestone D)

**Scope:** more than one armed station/direction pair, each with its own alert.
Nothing in this milestone is built yet; it is recorded here so the single-pair
model does not pretend to be a queue.

**What has to change**

- Storage: a list of armed pairs instead of `alert_station`/`alert_heading`
  (`alert_lead_min` can stay shared, or become per pair).
- Service: one tracking loop per pair, or one loop that walks all pairs. Today
  `ApproachAlertService.start()` cancels the running job, so a second arm
  silently replaces the first.
- Notifications: a notification ID per pair (today 2001/2011 are global), and a
  cap on active pairs so the tray cannot be flooded.
- Copy: every alert message and ring must name its station; "your train is
  close" is ambiguous once two trains are tracked.
- Duplicates: the same trip can be the next train for two nearby stations, so
  rings must be de-duplicated by trip ID.
- Grouping: a summary notification with one line per pair, and a way to cancel
  one pair from the tray.
- Boot: the resume prompt (notification 2012) must summarise several pairs and
  resume all of them in one tap.
- UI: list of armed pairs instead of a single banner, with a soft limit
  (about 3) and a clear "limit reached" state.
- Cost: battery and API load multiply with the number of pairs; the tracking
  loop count and the polling interval need a budget before the UI allows more.

**Why it is not part of the current milestone:** it changes persistence,
service lifecycle, notification identity and copy all at once. Shipping it
together with the banner would make it impossible to tell which change caused
a ring to be missed or duplicated.

**Start criteria**

1. The single-pair banner has shipped and been verified on a device.
2. The tracking loop and the alert service have automated tests covering
   replacement, cancel and resume.
3. A decision on the per-pair ring budget (how many pairs, how often we poll).
4. A plan for migrating existing single-pair prefs without losing an armed
   alert.

## Deferred

- Passenger/train simulation against the QA build (five to eight riders,
  arrival detection, favorites checks) — needs staging sessions and a
  writes-enabled build.
