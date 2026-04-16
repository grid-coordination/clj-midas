(ns midas.entities
  "MIDAS entity coercion: raw API responses → idiomatic Clojure maps.

  Two-layer data model:
    Raw layer   — direct JSON→EDN, PascalCase keys, string values
    Coerced layer — namespaced keywords, native types (java.time, BigDecimal)

  All coerced entities carry the original raw data as :midas/raw metadata."
  (:require [clojure.string :as str])
  (:import [java.time LocalDate LocalDateTime LocalTime Instant]
           [java.time.format DateTimeParseException]
           [java.math BigDecimal]))

;; ---------------------------------------------------------------------------
;; Time parsing helpers
;; ---------------------------------------------------------------------------

(defn- parse-date
  "Parse an ISO date string (YYYY-MM-DD) to a LocalDate, or nil."
  [s]
  (when (and s (string? s) (not (str/blank? s)))
    (LocalDate/parse s)))

(defn- parse-datetime
  "Parse an ISO datetime string to an Instant.
  Handles both proper ISO 8601 (with Z/offset) and MIDAS's no-timezone
  format (e.g. \"2023-12-25T00:00:00\") by treating it as UTC."
  [s]
  (when (and s (string? s) (not (str/blank? s)))
    (try
      (Instant/parse s)
      (catch DateTimeParseException _
        ;; MIDAS returns datetimes without timezone — treat as UTC
        (-> (LocalDateTime/parse s)
            (.atZone (java.time.ZoneId/of "UTC"))
            (.toInstant))))))

(defn- parse-time
  "Parse a time string (HH:MM:SS or HH:MM) to a LocalTime, or nil."
  [s]
  (when (and s (string? s) (not (str/blank? s)))
    (LocalTime/parse s)))

(defn- parse-bigdec
  "Coerce a number to BigDecimal."
  [n]
  (when n (BigDecimal/valueOf (double n))))

;; ---------------------------------------------------------------------------
;; Signal type coercion
;; ---------------------------------------------------------------------------

(def signal-type-kw
  {"Rates"      :midas.signal-type/rates
   "GHG"        :midas.signal-type/ghg
   "Flex Alert" :midas.signal-type/flex-alert})

;; ---------------------------------------------------------------------------
;; Rate type coercion
;; ---------------------------------------------------------------------------

(def rate-type-kw
  {"Time of use"              :midas.rate-type/tou
   "Critical Peak Pricing"    :midas.rate-type/cpp
   "Real Time Pricing"        :midas.rate-type/rtp
   "Greenhouse Gas emissions" :midas.rate-type/ghg
   "Flex Alert"               :midas.rate-type/flex-alert})

;; ---------------------------------------------------------------------------
;; Unit type coercion
;; ---------------------------------------------------------------------------

(def unit-type-kw
  {"$/kWh"        :midas.unit/dollar-per-kwh
   "$/kW"         :midas.unit/dollar-per-kw
   "export $/kWh" :midas.unit/export-dollar-per-kwh
   "backup $/kWh" :midas.unit/backup-dollar-per-kwh
   "kg/kWh CO2"   :midas.unit/kg-co2-per-kwh
   "$/kvarh"      :midas.unit/dollar-per-kvarh
   "Event"        :midas.unit/event
   "Level"        :midas.unit/level})

;; ---------------------------------------------------------------------------
;; Day type coercion
;; ---------------------------------------------------------------------------

(def day-type-kw
  {"Monday"    :midas.day/monday
   "Tuesday"   :midas.day/tuesday
   "Wednesday" :midas.day/wednesday
   "Thursday"  :midas.day/thursday
   "Friday"    :midas.day/friday
   "Saturday"  :midas.day/saturday
   "Sunday"    :midas.day/sunday
   "Holiday"   :midas.day/holiday})

;; ---------------------------------------------------------------------------
;; Entity coercion functions
;; ---------------------------------------------------------------------------

(defn ->value-data
  "Coerce a raw ValueData interval to an idiomatic Clojure map.

  Raw shape:
    {:ValueName \"winter off peak\" :DateStart \"2023-05-01\" ...}

  Coerced shape:
    {:midas.value/name \"winter off peak\"
     :midas.value/date-start #local-date ...
     :midas.value/price 0.1006M
     ...}"
  [raw]
  (-> {:midas.value/name       (:ValueName raw)
       :midas.value/date-start (parse-date (:DateStart raw))
       :midas.value/date-end   (parse-date (:DateEnd raw))
       :midas.value/day-start  (get day-type-kw (:DayStart raw))
       :midas.value/day-end    (get day-type-kw (:DayEnd raw))
       :midas.value/time-start (parse-time (:TimeStart raw))
       :midas.value/time-end   (parse-time (:TimeEnd raw))
       :midas.value/price      (parse-bigdec (:value raw))
       :midas.value/unit       (get unit-type-kw (:Unit raw) (:Unit raw))}
      (with-meta {:midas/raw raw})))

(defn ->rate-info
  "Coerce a raw RateInfo response to an idiomatic Clojure map.

  Raw shape:
    {:RateID \"USCA-TSTS-TTOU-TEST\" :RateName \"CEC TEST24HTOU\"
     :ValueInformation [...] ...}

  Coerced shape:
    {:midas.rate/id \"USCA-TSTS-TTOU-TEST\"
     :midas.rate/name \"CEC TEST24HTOU\"
     :midas.rate/values [<ValueData> ...]
     ...}"
  [raw]
  (-> {:midas.rate/id              (:RateID raw)
       :midas.rate/system-time     (parse-datetime (:SystemTime_UTC raw))
       :midas.rate/name            (:RateName raw)
       :midas.rate/type            (get rate-type-kw (:RateType raw) (:RateType raw))
       :midas.rate/sector          (:Sector raw)
       :midas.rate/end-use         (:EndUse raw)
       :midas.rate/api-url         (let [u (:API_Url raw)]
                                     (when (and u (not= u "None")) u))
       :midas.rate/rate-plan-url   (:RatePlan_Url raw)
       :midas.rate/alt-name-1      (:AltRateName1 raw)
       :midas.rate/alt-name-2      (:AltRateName2 raw)
       :midas.rate/signup-close    (parse-datetime (:SignupCloseDate raw))
       :midas.rate/values          (when-let [vi (:ValueInformation raw)]
                                     (mapv ->value-data vi))}
      (with-meta {:midas/raw raw})))

(defn ->rin-list-entry
  "Coerce a raw RIN list entry.

  Raw shape:
    {:RateID \"USCA-TSTS-TTOU-TEST\" :SignalType \"Rates\"
     :Description \"...\" :LastUpdated \"2023-06-07T15:57:48.023\"}

  Coerced shape:
    {:midas.rin/id \"USCA-TSTS-TTOU-TEST\"
     :midas.rin/signal-type :midas.signal-type/rates
     :midas.rin/description \"...\"
     :midas.rin/last-updated #inst ...}"
  [raw]
  (-> {:midas.rin/id           (:RateID raw)
       :midas.rin/signal-type  (get signal-type-kw (:SignalType raw))
       :midas.rin/description  (:Description raw)
       :midas.rin/last-updated (parse-datetime (:LastUpdated raw))}
      (with-meta {:midas/raw raw})))

(defn ->holiday
  "Coerce a raw HolidayEntry.

  Raw shape:
    {:EnergyCode \"PG\" :EnergyDescription \"Pacific Gas and Electric\"
     :DateOfHoliday \"2023-12-25T00:00:00\" :HolidayDescription \"Christmas 2023\"}

  Coerced shape:
    {:midas.holiday/energy-code \"PG\"
     :midas.holiday/energy-name \"Pacific Gas and Electric\"
     :midas.holiday/date #local-date 2023-12-25
     :midas.holiday/description \"Christmas 2023\"}"
  [raw]
  (let [date-str (:DateOfHoliday raw)
        ;; Extract just the date portion from the datetime string
        local-date (when date-str
                     (parse-date (subs date-str 0 (min 10 (count date-str)))))]
    (-> {:midas.holiday/energy-code (:EnergyCode raw)
         :midas.holiday/energy-name (:EnergyDescription raw)
         :midas.holiday/date        local-date
         :midas.holiday/description (:HolidayDescription raw)}
        (with-meta {:midas/raw raw}))))

(defn ->lookup-entry
  "Coerce a raw LookupEntry.

  Raw shape:
    {:UploadCode \"PG\" :Description \"Pacific Gas and Electric\"}

  Coerced shape:
    {:midas.lookup/code \"PG\"
     :midas.lookup/description \"Pacific Gas and Electric\"}"
  [raw]
  (-> {:midas.lookup/code        (:UploadCode raw)
       :midas.lookup/description (:Description raw)}
      (with-meta {:midas/raw raw})))

;; ---------------------------------------------------------------------------
;; Coerced API helpers — fetch + coerce in one step
;; ---------------------------------------------------------------------------

(defn rin-list
  "Extract and coerce RIN list entries from a get-rin-list response."
  [response]
  (mapv ->rin-list-entry (:body response)))

(defn rate-info
  "Extract and coerce rate info from a get-rate-values response."
  [response]
  (->rate-info (:body response)))

(defn holidays
  "Extract and coerce holidays from a get-holidays response."
  [response]
  (mapv ->holiday (:body response)))

(defn- distinct-by
  "Returns a lazy sequence of elements with duplicates removed by (f item)."
  [f coll]
  (let [step (fn step [xs seen]
               (lazy-seq
                (loop [[x :as xs] xs seen seen]
                  (when (seq xs)
                    (let [k (f x)]
                      (if (contains? seen k)
                        (recur (rest xs) seen)
                        (cons x (step (rest xs) (conj seen k)))))))))]
    (step coll #{})))

(defn historical-list
  "Extract and coerce historical RIN list from a get-historical-list response.
  Deduplicates by RIN ID (the live API returns duplicates)."
  [response]
  (->> (:body response)
       (map ->rin-list-entry)
       (distinct-by :midas.rin/id)
       vec))

(defn historical-data
  "Extract and coerce historical rate data from a get-historical-data response."
  [response]
  (->rate-info (:body response)))

(defn lookup-table
  "Extract and coerce lookup table entries from a get-lookup-table response."
  [response]
  (mapv ->lookup-entry (:body response)))

;; ---------------------------------------------------------------------------
;; RIN parsing
;; ---------------------------------------------------------------------------

(def ^:private rin-pattern
  "Regex matching the RIN format: CCSS-DDEE-RRRR-LLLL(LLLLLL).
  Four segments encoding six fields: country, state, distribution, energy,
  rate, and location."
  #"^([A-Z]{2})([A-Z]{2})-([A-Z0-9]{2})([A-Z0-9]{2})-([A-Z0-9]{4})-([A-Z0-9]{1,10})$")

(defn parse-rin
  "Parse a RIN string into its component fields.

  Example:
    (parse-rin \"USCA-PGPG-TOU4-0000\")
    ;=> {:midas.rin/country \"US\"
    ;    :midas.rin/state \"CA\"
    ;    :midas.rin/distribution \"PG\"
    ;    :midas.rin/energy \"PG\"
    ;    :midas.rin/rate \"TOU4\"
    ;    :midas.rin/location \"0000\"}

  Returns nil if the string does not match the RIN format."
  [rin]
  (when-let [[_ country state distribution energy rate location]
             (re-matches rin-pattern rin)]
    {:midas.rin/country      country
     :midas.rin/state        state
     :midas.rin/distribution distribution
     :midas.rin/energy       energy
     :midas.rin/rate         rate
     :midas.rin/location     location}))

(defn annotate-rin
  "Add human-readable labels to a parsed RIN map.

  lookup-tables is a map of lookup table name to a sequence of coerced
  LookupEntry maps (as returned by `(entities/lookup-table response)`).
  Recognized keys: \"Distribution\" and \"Energy\".

  Example:
    (annotate-rin (parse-rin \"USCA-PGPG-TOU4-0000\")
                  {\"Distribution\" dist-entries
                   \"Energy\"       energy-entries})
    ;=> {:midas.rin/country \"US\"
    ;    :midas.rin/state \"CA\"
    ;    :midas.rin/distribution \"PG\"
    ;    :midas.rin/distribution-name \"Pacific Gas and Electric\"
    ;    :midas.rin/energy \"PG\"
    ;    :midas.rin/energy-name \"Pacific Gas and Electric\"
    ;    :midas.rin/rate \"TOU4\"
    ;    :midas.rin/location \"0000\"}"
  [parsed-rin lookup-tables]
  (let [index (fn [entries]
                (into {} (map (juxt :midas.lookup/code :midas.lookup/description))
                      entries))
        dist-idx   (some-> (get lookup-tables "Distribution") index)
        energy-idx (some-> (get lookup-tables "Energy") index)]
    (cond-> parsed-rin
      (and dist-idx (get dist-idx (:midas.rin/distribution parsed-rin)))
      (assoc :midas.rin/distribution-name
             (get dist-idx (:midas.rin/distribution parsed-rin)))

      (and energy-idx (get energy-idx (:midas.rin/energy parsed-rin)))
      (assoc :midas.rin/energy-name
             (get energy-idx (:midas.rin/energy parsed-rin))))))

;; ---------------------------------------------------------------------------
;; Signal type helpers
;; ---------------------------------------------------------------------------

(defn ghg?
  "True if rate-info represents a GHG (greenhouse gas emissions) signal."
  [rate]
  (or (= :midas.rate-type/ghg (:midas.rate/type rate))
      (some-> rate :midas.rate/values first :midas.value/unit
              (= :midas.unit/kg-co2-per-kwh))))

(defn flex-alert?
  "True if rate-info represents a Flex Alert signal."
  [rate]
  (or (= :midas.rate-type/flex-alert (:midas.rate/type rate))
      (some-> rate :midas.rate/values first :midas.value/unit
              (= :midas.unit/event))))

(defn flex-alert-active?
  "True if the Flex Alert rate-info indicates an active alert.
  Active when any value interval has a non-zero value."
  [rate]
  (and (flex-alert? rate)
       (some #(and (:midas.value/price %)
                   (pos? (.doubleValue (:midas.value/price %))))
             (:midas.rate/values rate))))
