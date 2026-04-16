(ns midas.entities.schema
  "Malli schemas for coerced MIDAS entities (the public contract)."
  (:import [java.time LocalDate LocalDateTime LocalTime]
           [java.math BigDecimal]))

(def DayType
  [:enum
   :midas.day/monday :midas.day/tuesday :midas.day/wednesday
   :midas.day/thursday :midas.day/friday :midas.day/saturday
   :midas.day/sunday :midas.day/holiday])

(def SignalType
  [:enum
   :midas.signal-type/rates
   :midas.signal-type/ghg
   :midas.signal-type/flex-alert])

(def RateType
  [:enum
   :midas.rate-type/tou
   :midas.rate-type/cpp
   :midas.rate-type/rtp
   :midas.rate-type/ghg
   :midas.rate-type/flex-alert])

(def UnitType
  [:enum
   :midas.unit/dollar-per-kwh
   :midas.unit/dollar-per-kw
   :midas.unit/export-dollar-per-kwh
   :midas.unit/backup-dollar-per-kwh
   :midas.unit/kg-co2-per-kwh
   :midas.unit/dollar-per-kvarh
   :midas.unit/event
   :midas.unit/level])

(def ValueData
  [:map
   [:midas.value/name :string]
   [:midas.value/date-start [:maybe [:fn #(instance? LocalDate %)]]]
   [:midas.value/date-end [:maybe [:fn #(instance? LocalDate %)]]]
   [:midas.value/day-start {:optional true} [:maybe DayType]]
   [:midas.value/day-end {:optional true} [:maybe DayType]]
   [:midas.value/time-start [:maybe [:fn #(instance? LocalTime %)]]]
   [:midas.value/time-end [:maybe [:fn #(instance? LocalTime %)]]]
   [:midas.value/price [:maybe [:fn #(instance? BigDecimal %)]]]
   [:midas.value/unit [:or UnitType :string]]
   [:tick/beginning {:optional true} [:fn #(instance? LocalDateTime %)]]
   [:tick/end {:optional true} [:fn #(instance? LocalDateTime %)]]])

(def RateInfo
  [:map
   [:midas.rate/id :string]
   [:midas.rate/system-time {:optional true} [:maybe inst?]]
   [:midas.rate/name :string]
   [:midas.rate/type {:optional true} [:maybe [:or RateType :string]]]
   [:midas.rate/sector {:optional true} [:maybe :string]]
   [:midas.rate/end-use {:optional true} [:maybe :string]]
   [:midas.rate/api-url {:optional true} [:maybe :string]]
   [:midas.rate/rate-plan-url {:optional true} [:maybe :string]]
   [:midas.rate/alt-name-1 {:optional true} [:maybe :string]]
   [:midas.rate/alt-name-2 {:optional true} [:maybe :string]]
   [:midas.rate/signup-close {:optional true} [:maybe inst?]]
   [:midas.rate/values {:optional true} [:maybe [:vector ValueData]]]])

(def RinListEntry
  [:map
   [:midas.rin/id :string]
   [:midas.rin/signal-type [:maybe SignalType]]
   [:midas.rin/description :string]
   [:midas.rin/last-updated {:optional true} [:maybe inst?]]])

(def ParsedRin
  [:map
   [:midas.rin/country [:string {:min 2 :max 2}]]
   [:midas.rin/state [:string {:min 2 :max 2}]]
   [:midas.rin/distribution [:string {:min 2 :max 2}]]
   [:midas.rin/energy [:string {:min 2 :max 2}]]
   [:midas.rin/rate [:string {:min 4 :max 4}]]
   [:midas.rin/location [:string {:min 1 :max 10}]]
   [:midas.rin/distribution-name {:optional true} :string]
   [:midas.rin/energy-name {:optional true} :string]])

(def Holiday
  [:map
   [:midas.holiday/energy-code :string]
   [:midas.holiday/energy-name :string]
   [:midas.holiday/date [:maybe [:fn #(instance? LocalDate %)]]]
   [:midas.holiday/description :string]])

(def LookupEntry
  [:map
   [:midas.lookup/code :string]
   [:midas.lookup/description :string]])
