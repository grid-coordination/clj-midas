(ns midas.integration-test
  "Integration tests against the live MIDAS API.
  Requires MIDAS_USERNAME and MIDAS_PASSWORD environment variables.
  Run with: clojure -M:test -m kaocha.runner --focus :integration"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [midas.client :as client]
            [midas.entities :as entities]
            [malli.core :as m]
            [midas.entities.schema :as schema]))

;; ---------------------------------------------------------------------------
;; Fixture: auto-refreshing client
;; ---------------------------------------------------------------------------

(def ^:dynamic *client* nil)

(defn live-client-fixture [f]
  (let [username (System/getenv "MIDAS_USERNAME")
        password (System/getenv "MIDAS_PASSWORD")]
    (when (and username password)
      (binding [*client* (client/create-auto-client username password)]
        (f)))))

(use-fixtures :once live-client-fixture)

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest ^:integration token-acquisition
  (when *client*
    (testing "client has valid token"
      (let [ti (client/token-info *client*)]
        (is (some? (:token ti)))
        (is (not (client/token-expired? ti)))))))

(deftest ^:integration rin-list-query
  (when *client*
    (testing "signal type 1 (Rates) returns RINs"
      (let [resp (client/get-rin-list *client* 1)
            rins (entities/rin-list resp)]
        (is (client/success? resp))
        (is (pos? (count rins)))
        (is (m/validate schema/RinListEntry (first rins)))
        (is (every? #(= :midas.signal-type/rates (:midas.rin/signal-type %))
                    (take 10 rins)))))
    (testing "signal type 2 (GHG) returns RINs"
      (let [resp (client/get-rin-list *client* 2)
            rins (entities/rin-list resp)]
        (is (client/success? resp))
        (is (pos? (count rins)))))
    (testing "signal type 3 (Flex Alert) returns RINs"
      (let [resp (client/get-rin-list *client* 3)
            rins (entities/rin-list resp)]
        (is (client/success? resp))
        (is (pos? (count rins)))))))

(deftest ^:integration rate-values-query
  (when *client*
    (testing "test TOU rate returns valid data"
      (let [resp (client/get-rate-values *client* "USCA-TSTS-TTOU-TEST" "alldata")
            rate (entities/rate-info resp)]
        (is (client/success? resp))
        (is (= "USCA-TSTS-TTOU-TEST" (:midas.rate/id rate)))
        (is (= :midas.rate-type/tou (:midas.rate/type rate)))
        (is (m/validate schema/RateInfo rate))
        (is (pos? (count (:midas.rate/values rate))))
        (is (m/validate schema/ValueData (first (:midas.rate/values rate))))
        (is (= :midas.unit/dollar-per-kwh
               (-> rate :midas.rate/values first :midas.value/unit)))))))

(deftest ^:integration flex-alert-query
  (when *client*
    (testing "Flex Alert realtime returns valid data"
      (let [resp (client/get-rate-values *client* "USCA-FLEX-FXRT-0000" "realtime")]
        (when (client/success? resp)
          (let [rate (entities/rate-info resp)]
            (is (entities/flex-alert? rate))
            (is (= :midas.unit/event
                   (-> rate :midas.rate/values first :midas.value/unit)))))))))

(deftest ^:integration holiday-query
  (when *client*
    (testing "holidays returns valid data"
      (let [resp (client/get-holidays *client*)
            hols (entities/holidays resp)]
        (is (client/success? resp))
        (is (pos? (count hols)))
        (is (m/validate schema/Holiday (first hols)))))))

(deftest ^:integration lookup-table-queries
  (when *client*
    (doseq [table ["Distribution" "Energy" "Unit" "Ratetype"]]
      (testing (str "lookup table: " table)
        (let [resp (client/get-lookup-table *client* table)
              entries (entities/lookup-table resp)]
          (is (client/success? resp))
          (is (pos? (count entries)))
          (is (m/validate schema/LookupEntry (first entries))))))))

(deftest ^:integration historical-list-query
  (when *client*
    (testing "test provider historical list is deduplicated"
      (let [resp (client/get-historical-list *client* "TS" "TS")
            raw-count (count (:body resp))
            deduped (entities/historical-list resp)]
        (is (client/success? resp))
        (is (pos? (count deduped)))
        (is (<= (count deduped) raw-count) "deduplication should not add entries")))))

(deftest ^:integration historical-data-query
  (when *client*
    (testing "test RIN historical data returns valid rate info"
      (let [resp (client/get-historical-data *client*
                                             "USCA-TSTS-TTOU-TEST"
                                             "2023-01-01" "2023-03-31")
            rate (entities/historical-data resp)]
        (is (client/success? resp))
        (is (= "USCA-TSTS-TTOU-TEST" (:midas.rate/id rate)))
        (is (m/validate schema/RateInfo rate))))))
