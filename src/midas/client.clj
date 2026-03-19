(ns midas.client
  "MIDAS API client.

  Spec-driven HTTP client built on Martian. The OpenAPI spec bundled in
  resources/midas-spec/openapi.yaml is the single source of truth for
  endpoint definitions and parameter validation.

  Authentication flow:
    1. Call (get-token username password) to obtain a 10-minute bearer token
    2. Call (create-client token) to create an authenticated Martian client
    3. Use the client with API functions (rin-list, rate-values, etc.)"
  (:require [martian.core :as martian]
            [martian.hato :as martian-hato]
            [hato.client :as hc]
            [clojure.tools.logging :as log])
  (:import [java.util Base64]
           [java.time Instant]))

;; ---------------------------------------------------------------------------
;; Configuration
;; ---------------------------------------------------------------------------

(def api-url "https://midasapi.energy.ca.gov/api")
(def default-spec-path "midas-spec/openapi.yaml")

;; ---------------------------------------------------------------------------
;; Interceptors
;; ---------------------------------------------------------------------------

(defn- create-authentication-header
  "Martian interceptor that adds a Bearer token to requests."
  [token]
  {:name ::add-bearer-token
   :enter (fn [ctx]
            (assoc-in ctx
                      [:request :headers "Authorization"]
                      (str "Bearer " token)))})

(defn- turn-off-exception-throwing
  "Martian interceptor that prevents Hato from throwing on non-2xx responses."
  []
  {:name ::turn-off-exception-throwing
   :enter (fn [ctx]
            (assoc-in ctx [:request :throw-exceptions?] false))})

(defn- build-shared-http-client
  "Build a shared Java HttpClient with connection timeout."
  [{:keys [connect-timeout-ms] :or {connect-timeout-ms 5000}}]
  (hc/build-http-client {:connect-timeout connect-timeout-ms
                         :redirect-policy :normal
                         :version :http-1.1}))

(defn- inject-http-client
  "Martian interceptor that injects a shared HttpClient into every request."
  [http-client]
  {:name ::inject-http-client
   :enter (fn [ctx]
            (assoc-in ctx [:request :http-client] http-client))})

;; ---------------------------------------------------------------------------
;; Token acquisition (HTTP Basic auth → bearer token)
;; ---------------------------------------------------------------------------

(defn get-token
  "Authenticate with MIDAS using HTTP Basic auth and return a bearer token.

  The token is returned in the `Token` response header and is valid for
  10 minutes. Returns the token string on success, nil on failure.

  Options:
    :url - API base URL (default: production)"
  ([username password]
   (get-token username password {}))
  ([username password {:keys [url] :or {url api-url}}]
   (let [credentials (str username ":" password)
         encoded     (.encodeToString (Base64/getEncoder) (.getBytes credentials "UTF-8"))
         resp        (hato.client/request
                      {:method :get
                       :url (str url "/Token")
                       :headers {"Authorization" (str "Basic " encoded)}
                       :throw-exceptions? false})]
     (if (<= 200 (:status resp) 299)
       (let [token (get-in resp [:headers "token"])]
         (log/info "MIDAS token acquired, valid for 10 minutes")
         (with-meta {:token token
                     :acquired-at (Instant/now)
                     :expires-at (.plusSeconds (Instant/now) 600)}
           {:midas/raw resp}))
       (do (log/warn "MIDAS token request failed" {:status (:status resp)})
           nil)))))

(defn token-expired?
  "True if a token-info map is expired or will expire within buffer-seconds (default 30)."
  ([token-info]
   (token-expired? token-info 30))
  ([token-info buffer-seconds]
   (when token-info
     (.isBefore (.minusSeconds (:expires-at token-info) buffer-seconds)
                (Instant/now)))))

;; ---------------------------------------------------------------------------
;; Client creation
;; ---------------------------------------------------------------------------

(defn create-client
  "Create an authenticated MIDAS API client from the bundled OpenAPI spec.

  token can be either a raw token string or a token-info map from get-token.

  Options:
    :url       - API base URL (default: production)
    :spec-path - path to OpenAPI YAML on classpath (default: bundled spec)"
  ([token]
   (create-client token {}))
  ([token {:keys [url spec-path]
           :or   {url       api-url
                  spec-path default-spec-path}}]
   (log/info "Creating MIDAS client" {:url url})
   (let [token-str  (if (map? token) (:token token) token)
         http-client (build-shared-http-client {})]
     (-> (martian-hato/bootstrap-openapi
          spec-path
          {:server-url url
           :interceptors (concat
                          [(create-authentication-header token-str)
                           (turn-off-exception-throwing)
                           (inject-http-client http-client)]
                          martian-hato/default-interceptors)})
         (assoc :api-root url)
         (with-meta {:midas/token-info (when (map? token) token)})))))

;; ---------------------------------------------------------------------------
;; Raw API operations — return {:status :body :headers}
;; ---------------------------------------------------------------------------

(defn success?
  "True if the HTTP response has a 2xx status."
  [response]
  (let [status (:status response)]
    (and (integer? status) (<= 200 status 299))))

(defn body
  "Extract the parsed body from an HTTP response."
  [response]
  (:body response))

;; ValueData endpoint — multiplexed operations

(defn get-rin-list
  "Fetch list of available RINs by signal type.
  signal-type: 0=All, 1=Rates, 2=GHG, 3=Flex Alert."
  [client signal-type]
  (martian/response-for client :get-value-data {:SignalType signal-type}))

(defn get-rate-values
  "Fetch rate/price values for a specific RIN.
  query-type: \"alldata\" or \"realtime\"."
  [client rin query-type]
  (martian/response-for client :get-value-data {:ID rin :QueryType query-type}))

(defn get-lookup-table
  "Fetch a MIDAS lookup/reference table.
  table-name: Country, Daytype, Distribution, Enduse, Energy, Location,
              Ratetype, Sector, State, or Unit."
  [client table-name]
  (martian/response-for client :get-value-data {:LookupTable table-name}))

;; Holiday endpoint

(defn get-holidays
  "Fetch all utility holidays."
  [client]
  (martian/response-for client :get-holidays {}))

;; Historical endpoints

(defn get-historical-list
  "Fetch list of RINs with historical data for a provider pair."
  [client distribution-code energy-code]
  (martian/response-for client :get-historical-list
                        {:DistributionCode distribution-code
                         :EnergyCode energy-code}))

(defn get-historical-data
  "Fetch archived rate data for a RIN within a date range.
  Dates are ISO 8601 strings (e.g. \"2023-01-01\")."
  [client rin startdate enddate]
  (martian/response-for client :get-historical-data
                        {:id rin :startdate startdate :enddate enddate}))

;; ---------------------------------------------------------------------------
;; Convenience
;; ---------------------------------------------------------------------------

(defn routes
  "List all available route names for the client."
  [client]
  (->> client :handlers (mapv :route-name)))
