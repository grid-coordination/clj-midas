(ns midas.entities.schema.raw
  "Malli schemas for raw MIDAS API responses (boundary validation)."
  (:require [malli.core :as m]))

(def ValueData
  [:map
   [:ValueName :string]
   [:DateStart :string]
   [:DateEnd :string]
   [:DayStart {:optional true} [:maybe :string]]
   [:DayEnd {:optional true} [:maybe :string]]
   [:TimeStart :string]
   [:TimeEnd :string]
   [:value :double]
   [:Unit :string]])

(def RateInfo
  [:map
   [:RateID :string]
   [:SystemTime_UTC {:optional true} :string]
   [:RateName :string]
   [:RateType {:optional true} [:maybe :string]]
   [:Sector {:optional true} [:maybe :string]]
   [:EndUse {:optional true} [:maybe :string]]
   [:API_Url {:optional true} [:maybe :string]]
   [:RatePlan_Url {:optional true} [:maybe :string]]
   [:AltRateName1 {:optional true} [:maybe :string]]
   [:AltRateName2 {:optional true} [:maybe :string]]
   [:SignupCloseDate {:optional true} [:maybe :string]]
   [:ValueInformation {:optional true} [:maybe [:vector ValueData]]]])

(def RinListEntry
  [:map
   [:RateID :string]
   [:SignalType [:enum "Rates" "GHG" "Flex Alert"]]
   [:Description :string]
   [:LastUpdated {:optional true} :string]])

(def HolidayEntry
  [:map
   [:EnergyCode :string]
   [:EnergyDescription :string]
   [:DateOfHoliday :string]
   [:HolidayDescription :string]])

(def LookupEntry
  [:map
   [:UploadCode :string]
   [:Description :string]])

;; ---------------------------------------------------------------------------
;; Validation helpers
;; ---------------------------------------------------------------------------

(defn validate-raw-rate-info [raw]   (m/explain RateInfo raw))
(defn validate-raw-value-data [raw]  (m/explain ValueData raw))
(defn validate-raw-rin-entry [raw]   (m/explain RinListEntry raw))
(defn validate-raw-holiday [raw]     (m/explain HolidayEntry raw))
(defn validate-raw-lookup [raw]      (m/explain LookupEntry raw))
