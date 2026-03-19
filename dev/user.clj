(ns user
  "REPL development utilities."
  (:require [midas.client :as client]
            [midas.entities :as entities]))

(defonce midas-client (atom nil))

(defn get-token!
  "Acquire a MIDAS bearer token from environment variables.
  Requires MIDAS_USERNAME and MIDAS_PASSWORD to be set."
  []
  (let [username (System/getenv "MIDAS_USERNAME")
        password (System/getenv "MIDAS_PASSWORD")]
    (when-not (and username password)
      (throw (ex-info "MIDAS_USERNAME and MIDAS_PASSWORD env vars required" {})))
    (client/get-token username password)))

(defn- midas-credentials
  "Read MIDAS credentials from environment variables."
  []
  (let [username (System/getenv "MIDAS_USERNAME")
        password (System/getenv "MIDAS_PASSWORD")]
    (when-not (and username password)
      (throw (ex-info "MIDAS_USERNAME and MIDAS_PASSWORD env vars required" {})))
    [username password]))

(defn start!
  "Initialize a MIDAS auto-refreshing client for REPL use.
  Token refreshes automatically — no need to call refresh!."
  ([] (start! {}))
  ([opts]
   (let [[username password] (midas-credentials)]
     (reset! midas-client (client/create-auto-client username password opts))
     :started)))

(comment
  ;; Start the client (requires MIDAS_USERNAME/MIDAS_PASSWORD env vars)
  (start!)

  ;; List available RINs (0=All, 1=Rates, 2=GHG, 3=Flex Alert)
  (def rin-resp (client/get-rin-list @midas-client 0))
  (client/success? rin-resp)
  (entities/rin-list rin-resp)

  ;; Fetch rate values for the test RIN
  (def rate-resp (client/get-rate-values @midas-client "USCA-TSTS-TTOU-TEST" "alldata"))
  (client/success? rate-resp)
  (entities/rate-info rate-resp)

  ;; Fetch holidays
  (def holiday-resp (client/get-holidays @midas-client))
  (entities/holidays holiday-resp)

  ;; Lookup tables
  (def dist-resp (client/get-lookup-table @midas-client "Distribution"))
  (entities/lookup-table dist-resp)

  ;; Historical list
  (def hist-list-resp (client/get-historical-list @midas-client "PG" "PG"))
  (entities/historical-list hist-list-resp)

  ;; Historical data
  (def hist-resp (client/get-historical-data @midas-client "USCA-TSTS-TTOU-TEST" "2023-01-01" "2023-12-31"))
  (entities/historical-data hist-resp)

  ;; Access raw data via metadata
  (-> rate-resp entities/rate-info meta :midas/raw)
  (-> rate-resp entities/rate-info :midas.rate/values first meta :midas/raw))
