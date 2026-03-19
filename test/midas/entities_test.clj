(ns midas.entities-test
  (:require [clojure.test :refer [deftest is testing]]
            [midas.entities :as entities]
            [malli.core :as m]
            [midas.entities.schema :as schema]
            [midas.entities.schema.raw :as raw-schema])
  (:import [java.time LocalDate LocalTime]
           [java.math BigDecimal]))

(def sample-value-data
  {:ValueName "winter off peak"
   :DateStart "2023-05-01"
   :DateEnd "2023-05-01"
   :DayStart "Monday"
   :DayEnd "Monday"
   :TimeStart "07:00:00"
   :TimeEnd "07:59:59"
   :value 0.1006
   :Unit "$/kWh"})

(def sample-rate-info
  {:RateID "USCA-TSTS-TTOU-TEST"
   :SystemTime_UTC "2026-03-19T10:03:46.379Z"
   :RateName "CEC TEST24HTOU"
   :RateType "Time of use"
   :Sector "All sectors"
   :EndUse "All"
   :API_Url "None"
   :RatePlan_Url "https://energy.ca.gov"
   :AltRateName1 "TOU Base test rate"
   :AltRateName2 "TOU Base test rate"
   :SignupCloseDate "2024-12-31T00:00:00.000Z"
   :ValueInformation [sample-value-data]})

(def sample-rin-entry
  {:RateID "USCA-TSTS-TTOU-TEST"
   :SignalType "Rates"
   :Description "Rate Data for Distributor: Test, Energy Company: Test"
   :LastUpdated "2023-06-07T15:57:48.023"})

(def sample-holiday
  {:EnergyCode "PG"
   :EnergyDescription "Pacific Gas and Electric"
   :DateOfHoliday "2023-12-25T00:00:00"
   :HolidayDescription "Christmas 2023"})

(def sample-lookup
  {:UploadCode "PG"
   :Description "Pacific Gas and Electric"})

;; ---------------------------------------------------------------------------
;; Raw schema validation
;; ---------------------------------------------------------------------------

(deftest raw-schema-validation
  (testing "raw value data validates"
    (is (nil? (raw-schema/validate-raw-value-data sample-value-data))))
  (testing "raw rate info validates"
    (is (nil? (raw-schema/validate-raw-rate-info sample-rate-info))))
  (testing "raw RIN entry validates"
    (is (nil? (raw-schema/validate-raw-rin-entry sample-rin-entry))))
  (testing "raw holiday validates"
    (is (nil? (raw-schema/validate-raw-holiday sample-holiday))))
  (testing "raw lookup validates"
    (is (nil? (raw-schema/validate-raw-lookup sample-lookup)))))

;; ---------------------------------------------------------------------------
;; Coercion tests
;; ---------------------------------------------------------------------------

(deftest value-data-coercion
  (let [v (entities/->value-data sample-value-data)]
    (testing "basic fields"
      (is (= "winter off peak" (:midas.value/name v)))
      (is (= (LocalDate/parse "2023-05-01") (:midas.value/date-start v)))
      (is (= (LocalTime/parse "07:00:00") (:midas.value/time-start v)))
      (is (= :midas.day/monday (:midas.value/day-start v)))
      (is (= "$/kWh" (:midas.value/unit v))))
    (testing "price is BigDecimal"
      (is (instance? BigDecimal (:midas.value/price v))))
    (testing "raw metadata preserved"
      (is (= sample-value-data (-> v meta :midas/raw))))
    (testing "validates against coerced schema"
      (is (m/validate schema/ValueData v)))))

(deftest rate-info-coercion
  (let [r (entities/->rate-info sample-rate-info)]
    (testing "basic fields"
      (is (= "USCA-TSTS-TTOU-TEST" (:midas.rate/id r)))
      (is (= "CEC TEST24HTOU" (:midas.rate/name r)))
      (is (= :midas.rate-type/tou (:midas.rate/type r))))
    (testing "API_Url 'None' becomes nil"
      (is (nil? (:midas.rate/api-url r))))
    (testing "values are coerced"
      (is (= 1 (count (:midas.rate/values r))))
      (is (= "winter off peak" (-> r :midas.rate/values first :midas.value/name))))
    (testing "raw metadata preserved"
      (is (= sample-rate-info (-> r meta :midas/raw))))
    (testing "validates against coerced schema"
      (is (m/validate schema/RateInfo r)))))

(deftest rin-list-entry-coercion
  (let [e (entities/->rin-list-entry sample-rin-entry)]
    (testing "signal type keyword"
      (is (= :midas.signal-type/rates (:midas.rin/signal-type e))))
    (testing "last-updated parsed"
      (is (inst? (:midas.rin/last-updated e))))
    (testing "validates against coerced schema"
      (is (m/validate schema/RinListEntry e)))))

(deftest holiday-coercion
  (let [h (entities/->holiday sample-holiday)]
    (testing "date extracted from datetime"
      (is (= (LocalDate/parse "2023-12-25") (:midas.holiday/date h))))
    (testing "basic fields"
      (is (= "PG" (:midas.holiday/energy-code h)))
      (is (= "Christmas 2023" (:midas.holiday/description h))))
    (testing "validates against coerced schema"
      (is (m/validate schema/Holiday h)))))

(deftest lookup-entry-coercion
  (let [l (entities/->lookup-entry sample-lookup)]
    (testing "basic fields"
      (is (= "PG" (:midas.lookup/code l)))
      (is (= "Pacific Gas and Electric" (:midas.lookup/description l))))
    (testing "validates against coerced schema"
      (is (m/validate schema/LookupEntry l)))))

;; ---------------------------------------------------------------------------
;; Rate type coercion
;; ---------------------------------------------------------------------------

(deftest rate-type-coercion
  (testing "known rate types coerce to keywords"
    (is (= :midas.rate-type/tou
           (:midas.rate/type (entities/->rate-info (assoc sample-rate-info :RateType "Time of use")))))
    (is (= :midas.rate-type/cpp
           (:midas.rate/type (entities/->rate-info (assoc sample-rate-info :RateType "Critical Peak Pricing")))))
    (is (= :midas.rate-type/rtp
           (:midas.rate/type (entities/->rate-info (assoc sample-rate-info :RateType "Real Time Pricing")))))
    (is (= :midas.rate-type/ghg
           (:midas.rate/type (entities/->rate-info (assoc sample-rate-info :RateType "Greenhouse Gas emissions")))))
    (is (= :midas.rate-type/flex-alert
           (:midas.rate/type (entities/->rate-info (assoc sample-rate-info :RateType "Flex Alert"))))))
  (testing "unknown rate types pass through as strings"
    (is (= "Some Future Type"
           (:midas.rate/type (entities/->rate-info (assoc sample-rate-info :RateType "Some Future Type")))))))

;; ---------------------------------------------------------------------------
;; Edge cases
;; ---------------------------------------------------------------------------

(deftest null-handling
  (testing "nil DayStart/DayEnd (historical data)"
    (let [v (entities/->value-data (assoc sample-value-data :DayStart nil :DayEnd nil))]
      (is (nil? (:midas.value/day-start v)))
      (is (nil? (:midas.value/day-end v)))))
  (testing "nil RateType"
    (let [r (entities/->rate-info (assoc sample-rate-info :RateType nil))]
      (is (nil? (:midas.rate/type r)))))
  (testing "nil ValueInformation"
    (let [r (entities/->rate-info (assoc sample-rate-info :ValueInformation nil))]
      (is (nil? (:midas.rate/values r)))))
  (testing "API_Url nil vs 'None'"
    (let [r-none (entities/->rate-info (assoc sample-rate-info :API_Url "None"))
          r-nil (entities/->rate-info (assoc sample-rate-info :API_Url nil))
          r-real (entities/->rate-info (assoc sample-rate-info :API_Url "https://example.com"))]
      (is (nil? (:midas.rate/api-url r-none)))
      (is (nil? (:midas.rate/api-url r-nil)))
      (is (= "https://example.com" (:midas.rate/api-url r-real))))))

(deftest flex-alert-time-format
  (testing "HH:MM format (no seconds) parses correctly"
    (let [v (entities/->value-data (assoc sample-value-data
                                          :TimeStart "03:11"
                                          :TimeEnd "03:11"))]
      (is (= (LocalTime/parse "03:11") (:midas.value/time-start v)))
      (is (= (LocalTime/parse "03:11") (:midas.value/time-end v))))))

(deftest datetime-parsing-variants
  (testing "ISO 8601 with Z"
    (let [r (entities/->rate-info sample-rate-info)]
      (is (inst? (:midas.rate/system-time r)))))
  (testing "MIDAS no-timezone format"
    (let [e (entities/->rin-list-entry sample-rin-entry)]
      (is (inst? (:midas.rin/last-updated e)))))
  (testing "holiday datetime → LocalDate extraction"
    (let [h (entities/->holiday sample-holiday)]
      (is (instance? LocalDate (:midas.holiday/date h))))))
