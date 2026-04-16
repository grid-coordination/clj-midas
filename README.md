# clj-midas

[![Clojars Project](https://img.shields.io/clojars/v/energy.grid-coordination/clj-midas.svg)](https://clojars.org/energy.grid-coordination/clj-midas)

A Clojure client library for the California Energy Commission's [MIDAS API](https://midasapi.energy.ca.gov/), providing access to electricity rate data, GHG emissions signals, Flex Alerts, utility holidays, and reference lookup tables. Built on a [non-official OpenAPI spec](https://github.com/grid-coordination/midas-api-specs#disclaimer) derived from the CEC's public documentation.

## Features

- **Spec-driven HTTP client** built on [Martian](https://github.com/oliyh/martian) with the bundled OpenAPI spec as the single source of truth
- **Two-layer data model**: raw API responses (PascalCase, strings) and coerced Clojure entities (namespaced keywords, BigDecimals, java.time types)
- **Auto-refreshing authentication**: 10-minute bearer tokens are transparently refreshed before expiry
- **Metadata preservation**: every coerced entity carries the original API data as `:midas/raw` metadata
- **Malli schemas** for both raw and coerced data layers
- **RIN parsing**: decompose a [Rate Identification Number](https://github.com/grid-coordination/midas-api-specs/blob/main/doc/rin-structure.md) into its component fields, with optional annotation from lookup tables
- **Signal type helpers**: `flex-alert?`, `flex-alert-active?`, `ghg?` for quick classification

## Installation

Add to your `deps.edn`:

```clojure
{:deps {energy.grid-coordination/clj-midas {:mvn/version "0.3.0"}}}
```

## Quick Start

```clojure
(require '[midas.client :as client]
         '[midas.entities :as entities])

;; Create an auto-refreshing client (token renews transparently)
(def c (client/create-auto-client "username" "password"))

;; Or manually: acquire token, then create client
(def token-info (client/get-token "username" "password"))
(def c (client/create-client token-info))

;; Fetch rate values for a RIN
(def resp (client/get-rate-values c "USCA-TSTS-TTOU-TEST" "alldata"))
(client/success? resp)
;=> true

;; Coerce to idiomatic Clojure entities
(def rate (entities/rate-info resp))
```

## API Endpoints

The MIDAS API exposes five endpoints. clj-midas wraps all of them:

| Function | Endpoint | Description |
|----------|----------|-------------|
| `get-rin-list` | `GET /ValueData?SignalType=` | List available RINs by signal type |
| `get-rate-values` | `GET /ValueData?ID=&QueryType=` | Fetch rate/price data for a RIN |
| `get-lookup-table` | `GET /ValueData?LookupTable=` | Fetch reference data (codes & descriptions) |
| `get-holidays` | `GET /Holiday` | Fetch all utility holidays |
| `get-historical-list` | `GET /HistoricalList` | List RINs with archived data |
| `get-historical-data` | `GET /HistoricalData` | Fetch archived rate data by date range |
| `register` | `POST /Registration` | Register a new API account |

## Data Model

The library provides two views of the API data:

### Raw Layer

Direct from the JSON — PascalCase keys, string values. Useful for debugging or when you need the exact API representation.

```clojure
(:body resp)
;=> {:RateID "USCA-TSTS-TTOU-TEST"
;    :RateName "CEC TEST24HTOU"
;    :RateType "Time of use"
;    :ValueInformation [{:ValueName "winter off peak"
;                         :DateStart "2023-05-01"
;                         :TimeStart "07:00:00"
;                         :value 0.1006
;                         :Unit "$/kWh"} ...]}
```

### Coerced Layer

Idiomatic Clojure — namespaced keywords, native types.

```clojure
(entities/rate-info resp)
;=> #:midas.rate{:id "USCA-TSTS-TTOU-TEST"
;                :name "CEC TEST24HTOU"
;                :type :midas.rate-type/tou
;                :values [#:midas.value{:name "winter off peak"
;                                       :date-start #local-date "2023-05-01"
;                                       :time-start #local-time "07:00"
;                                       :price 0.1006M
;                                       :unit :midas.unit/dollar-per-kwh
;                                       :day-start :midas.day/monday} ...]}
```

### Entities

#### RateInfo

| Key | Type | Description |
|-----|------|-------------|
| `:midas.rate/id` | `String` | Rate Identification Number (RIN) |
| `:midas.rate/name` | `String` | Rate name |
| `:midas.rate/type` | `Keyword` or `String` | Rate type (`:midas.rate-type/tou`, `/cpp`, `/rtp`, `/ghg`, `/flex-alert`, or passthrough string) |
| `:midas.rate/system-time` | `Instant` | Server timestamp |
| `:midas.rate/sector` | `String` or `nil` | Customer sector |
| `:midas.rate/end-use` | `String` or `nil` | End use category |
| `:midas.rate/api-url` | `String` or `nil` | API URL (literal `"None"` becomes nil) |
| `:midas.rate/rate-plan-url` | `String` or `nil` | Rate schedule URL |
| `:midas.rate/signup-close` | `Instant` or `nil` | Signup deadline |
| `:midas.rate/values` | `vector` | Vector of ValueData maps |

#### ValueData

| Key | Type | Description |
|-----|------|-------------|
| `:midas.value/name` | `String` | Interval description (e.g. `"winter off peak"`) |
| `:midas.value/date-start` | `LocalDate` | Interval start date |
| `:midas.value/date-end` | `LocalDate` | Interval end date |
| `:midas.value/day-start` | `Keyword` or `nil` | Day type (`:midas.day/monday` ... `:midas.day/holiday`) |
| `:midas.value/day-end` | `Keyword` or `nil` | Day type |
| `:midas.value/time-start` | `LocalTime` | Interval start time |
| `:midas.value/time-end` | `LocalTime` | Interval end time |
| `:midas.value/price` | `BigDecimal` | Price or emissions value |
| `:midas.value/unit` | `Keyword` or `String` | Unit (`:midas.unit/dollar-per-kwh`, `/kg-co2-per-kwh`, `/event`, etc.) |

#### RinListEntry

| Key | Type | Description |
|-----|------|-------------|
| `:midas.rin/id` | `String` | Rate Identification Number |
| `:midas.rin/signal-type` | `Keyword` or `nil` | Signal type (`:midas.signal-type/rates`, `/ghg`, `/flex-alert`) |
| `:midas.rin/description` | `String` | Human-readable description |
| `:midas.rin/last-updated` | `Instant` or `nil` | Last data update timestamp |

#### Holiday

| Key | Type | Description |
|-----|------|-------------|
| `:midas.holiday/energy-code` | `String` | Two-character provider code |
| `:midas.holiday/energy-name` | `String` | Provider name |
| `:midas.holiday/date` | `LocalDate` | Holiday date |
| `:midas.holiday/description` | `String` | Holiday name |

#### LookupEntry

| Key | Type | Description |
|-----|------|-------------|
| `:midas.lookup/code` | `String` | Upload code |
| `:midas.lookup/description` | `String` | Human-readable description |

#### ParsedRin

| Key | Type | Description |
|-----|------|-------------|
| `:midas.rin/country` | `String` | Country code (e.g. `"US"`) |
| `:midas.rin/state` | `String` | State code (e.g. `"CA"`) |
| `:midas.rin/distribution` | `String` | Distribution utility code (e.g. `"PG"`) |
| `:midas.rin/energy` | `String` | Energy provider code (e.g. `"PG"`) |
| `:midas.rin/rate` | `String` | Rate schedule identifier (e.g. `"TOU4"`) |
| `:midas.rin/location` | `String` | Location identifier (e.g. `"0000"`) |
| `:midas.rin/distribution-name` | `String` or absent | Human-readable distribution utility name (added by `annotate-rin`) |
| `:midas.rin/energy-name` | `String` or absent | Human-readable energy provider name (added by `annotate-rin`) |

### Type Coercion Summary

| Raw (API) | Coerced (Clojure) | Example |
|-----------|-------------------|---------|
| ISO date string | `java.time.LocalDate` | `"2023-05-01"` → `#local-date "2023-05-01"` |
| Time string | `java.time.LocalTime` | `"07:00:00"` → `#local-time "07:00"` |
| ISO datetime with Z | `java.time.Instant` | `"2023-03-21T16:34:42.906Z"` → `#inst "..."` |
| Datetime without TZ | `java.time.Instant` (as UTC) | `"2023-06-07T15:57:48.023"` → `#inst "..."` |
| Number | `BigDecimal` | `0.1006` → `0.1006M` |
| Rate type string | Namespaced keyword | `"Time of use"` → `:midas.rate-type/tou` |
| Unit string | Namespaced keyword | `"$/kWh"` → `:midas.unit/dollar-per-kwh` |
| Day string | Namespaced keyword | `"Monday"` → `:midas.day/monday` |

## Authentication

MIDAS uses a two-step auth flow:

1. **HTTP Basic auth** → `GET /Token` returns a bearer token (valid for 10 minutes)
2. **Bearer token** → all other endpoints

```clojure
;; Manual token management
(def token-info (client/get-token "user" "pass"))
;=> {:token "eyJ..." :acquired-at #inst "..." :expires-at #inst "..."}

(client/token-expired? token-info)
;=> false

;; Auto-refreshing client (recommended)
(def c (client/create-auto-client "user" "pass"))
;; Token refreshes transparently — no manual management needed
```

## RIN Parsing

Parse a [Rate Identification Number](https://github.com/grid-coordination/midas-api-specs/blob/main/doc/rin-structure.md) into its component fields:

```clojure
(entities/parse-rin "USCA-PGPG-TOU4-0000")
;=> {:midas.rin/country "US"
;    :midas.rin/state "CA"
;    :midas.rin/distribution "PG"
;    :midas.rin/energy "PG"
;    :midas.rin/rate "TOU4"
;    :midas.rin/location "0000"}

;; Returns nil for invalid RINs
(entities/parse-rin "not-a-rin")
;=> nil
```

Add human-readable labels from MIDAS lookup tables:

```clojure
;; Fetch lookup tables once
(def dist-table (entities/lookup-table (client/get-lookup-table c "Distribution")))
(def energy-table (entities/lookup-table (client/get-lookup-table c "Energy")))
(def lookups {"Distribution" dist-table, "Energy" energy-table})

(entities/annotate-rin (entities/parse-rin "USCA-SDEA-TTOU-0000") lookups)
;=> {:midas.rin/country "US"
;    :midas.rin/state "CA"
;    :midas.rin/distribution "SD"
;    :midas.rin/distribution-name "San Diego Gas and Electric"
;    :midas.rin/energy "EA"
;    :midas.rin/energy-name "Clean Energy Alliance"
;    :midas.rin/rate "TTOU"
;    :midas.rin/location "0000"}
```

## Signal Type Helpers

```clojure
;; Detect signal type from rate data
(entities/ghg? rate)           ;=> true/false (checks rate-type + unit)
(entities/flex-alert? rate)    ;=> true/false
(entities/flex-alert-active? rate) ;=> true if any value > 0
```

## Metadata

Every coerced entity preserves the original API data as metadata:

```clojure
(def rate (entities/rate-info resp))

;; Access the original API response
(-> rate meta :midas/raw)
;=> {:RateID "USCA-TSTS-TTOU-TEST" :RateName "CEC TEST24HTOU" ...}

;; Works at every level
(-> rate :midas.rate/values first meta :midas/raw)
;=> {:ValueName "winter off peak" :DateStart "2023-05-01" ...}
```

## Schemas

Malli schemas are published in dedicated namespaces.

### `midas.entities.schema` — Coerced entities (the public contract)

```clojure
(require '[midas.entities.schema :as schema]
         '[malli.core :as m])

(m/validate schema/RateInfo rate)    ;=> true
(m/validate schema/ValueData value)  ;=> true

;; Available: RateInfo, ValueData, RinListEntry, ParsedRin, Holiday,
;;            LookupEntry, RateType, SignalType, DayType, UnitType
```

### `midas.entities.schema.raw` — Raw API shapes

```clojure
(require '[midas.entities.schema.raw :as raw])

(raw/validate-raw-rate-info (:body resp))    ;=> nil (valid)
(raw/validate-raw-value-data value)          ;=> nil or Malli explanation

;; Available: RateInfo, ValueData, RinListEntry, HolidayEntry, LookupEntry
```

## API Reference

### `midas.client`

| Function | Description |
|----------|-------------|
| `get-token` | Authenticate with HTTP Basic, returns token-info map |
| `token-expired?` | Check if a token-info is expired (with 30s buffer) |
| `create-client` | Create client with a token string or token-info map |
| `create-auto-client` | Create client with auto-refreshing token |
| `token-info` | Get current token-info from a client |
| `success?` | True if HTTP response is 2xx |
| `body` | Extract parsed body from response |
| `get-rin-list` | List RINs by signal type (0=All, 1=Rates, 2=GHG, 3=Flex Alert) |
| `get-rate-values` | Fetch rate data for a RIN (`"alldata"` or `"realtime"`) |
| `get-lookup-table` | Fetch a reference table (Distribution, Energy, Unit, etc.) |
| `get-holidays` | Fetch all utility holidays |
| `get-historical-list` | List RINs with archived data for a provider pair |
| `get-historical-data` | Fetch archived rate data for a RIN and date range |
| `register` | Register a new MIDAS account (auto base64 encodes fields) |
| `routes` | List available Martian route names |

### `midas.entities`

| Function | Description |
|----------|-------------|
| `->rate-info` | Coerce a raw RateInfo map |
| `->value-data` | Coerce a raw ValueData map |
| `->rin-list-entry` | Coerce a raw RIN list entry |
| `->holiday` | Coerce a raw HolidayEntry |
| `->lookup-entry` | Coerce a raw LookupEntry |
| `rate-info` | Extract + coerce rate info from HTTP response |
| `rin-list` | Extract + coerce RIN list from HTTP response |
| `holidays` | Extract + coerce holidays from HTTP response |
| `historical-list` | Extract + coerce + deduplicate historical RIN list |
| `historical-data` | Extract + coerce historical rate data |
| `lookup-table` | Extract + coerce lookup table entries |
| `ghg?` | True if rate-info is a GHG signal |
| `parse-rin` | Parse a RIN string into its component fields |
| `annotate-rin` | Add human-readable labels from lookup tables to a parsed RIN |
| `flex-alert?` | True if rate-info is a Flex Alert |
| `flex-alert-active?` | True if a Flex Alert is currently active |

## REPL Session Example

```clojure
(require '[midas.client :as client]
         '[midas.entities :as entities]
         '[midas.entities.schema :as schema]
         '[malli.core :as m])

;; Create auto-refreshing client
(def c (client/create-auto-client
         (System/getenv "MIDAS_USERNAME")
         (System/getenv "MIDAS_PASSWORD")))

;; List all rate RINs
(def rin-resp (client/get-rin-list c 1))
(def rins (entities/rin-list rin-resp))
(count rins)
;=> 67266

;; Fetch the test TOU rate
(def rate-resp (client/get-rate-values c "USCA-TSTS-TTOU-TEST" "alldata"))
(def rate (entities/rate-info rate-resp))

(:midas.rate/type rate)
;=> :midas.rate-type/tou

(count (:midas.rate/values rate))
;=> 768

;; Inspect a value interval
(first (:midas.rate/values rate))
;=> #:midas.value{:name "winter off peak"
;                  :date-start #local-date "2023-05-01"
;                  :time-start #local-time "07:00"
;                  :time-end #local-time "07:59:59"
;                  :price 0.1006M
;                  :unit :midas.unit/dollar-per-kwh
;                  :day-start :midas.day/monday
;                  :day-end :midas.day/monday}

;; Validate against schema
(m/validate schema/RateInfo rate)
;=> true

;; Check Flex Alert status
(def flex-resp (client/get-rate-values c "USCA-FLEX-FXRT-0000" "realtime"))
(def flex (entities/rate-info flex-resp))
(entities/flex-alert? flex)
;=> true
(entities/flex-alert-active? flex)
;=> false  (no active alert)

;; Lookup tables
(def dists (entities/lookup-table (client/get-lookup-table c "Distribution")))
(first dists)
;=> #:midas.lookup{:code "BN" :description "Banning"}

;; Holidays
(first (entities/holidays (client/get-holidays c)))
;=> #:midas.holiday{:energy-code "SD"
;                    :energy-name "San Diego Gas and Electric"
;                    :date #local-date "2023-02-20"
;                    :description "President's Day"}

;; Access raw API data via metadata
(-> rate meta :midas/raw :RateType)
;=> "Time of use"
```

## Development

### Start nREPL

```bash
clojure -M:nrepl
# nREPL port written to .nrepl-port
```

Requires `MIDAS_USERNAME` and `MIDAS_PASSWORD` environment variables.

### Dev helpers

The `dev/user.clj` namespace provides REPL convenience functions:

```clojure
(start!)   ; create auto-refreshing client from env vars
```

### Run tests

```bash
# Unit tests only (fast, no network)
clojure -M:test -m kaocha.runner --focus :unit

# Integration tests (hits live API, needs credentials)
clojure -M:test -m kaocha.runner --focus :integration

# All tests
clojure -M:test -m kaocha.runner
```

## License

[MIT License](LICENSE) -- Copyright (c) 2026 Clark Communications Corporation
