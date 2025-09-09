# User Data - Persistence, Capacity and High Performance (V1)

This document summarizes the capacities really implemented for the persistence of talks and events registered by users.

## Current scope

- ** Asynchronous persistence. ** User's events, speakers and agendas are saved in JSON files through a single writer with atomic writings and reintent. If the tail is filled, the Scriptures are discarded and the error is recorded.
- ** Load of the last year. ** At the beginning of the application the most recent year available and the corresponding agenda is loaded in memory.
- ** Readings from memory. ** The consultations are answered from a _snapshot_ in memory. The first reading opens a 1–2S soda window that coalesce multiple requests in a single access to disc.
- ** Historical on demand. ** There is support at the service level to load or release previous years (`Loadhistorical` /` unloadhistorical`) subject to capacity evaluation, although there is still no user interface.
- ** Admission for capacity. ** Before accessing private routes, memory and disk space are verified; If the system is saturated, it is answered with "due to high demand, we cannot manage your data at this time. Try it later."
- ** Capacity panel. ** In `/Private/admin/capacity` the current mode, memory and disk, and readings and writings metrics are shown.

## Relevant configuration

`` Properties
Read.window = PT2S # refresh window for readings
Read.Max-Stale = PT10S # maximum obsolete data time
persist.
``

The data is stored in the `data/` folder and the deeds are atomic (`file.tmp → replace`) with up to three retents.

## Out of V1 reach

- Graphic interface to recover historical per year.
- Edition of memory or disc budgets from the capacity panel.
- External alerts when the capacity is saturated.