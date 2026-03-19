(ns midas.client-test
  (:require [clojure.test :refer [deftest is testing]]
            [midas.client :as client])
  (:import [java.time Instant]))

(deftest create-client-test
  (testing "client creation with raw token string"
    (let [c (client/create-client "fake-token")]
      (is (some? c))
      (is (= "https://midasapi.energy.ca.gov/api" (:api-root c)))
      (is (seq (client/routes c)))))
  (testing "client creation with token-info map"
    (let [token-info {:token "fake-token"
                      :acquired-at (Instant/now)
                      :expires-at (.plusSeconds (Instant/now) 600)}
          c (client/create-client token-info)]
      (is (some? c))
      (is (= token-info (-> c meta :midas/token-info))))))

(deftest routes-test
  (testing "all expected routes are present"
    (let [c (client/create-client "fake-token")
          r (set (client/routes c))]
      (is (contains? r :get-value-data))
      (is (contains? r :get-holidays))
      (is (contains? r :get-historical-list))
      (is (contains? r :get-historical-data)))))

(deftest token-expired-test
  (testing "fresh token is not expired"
    (let [token-info {:token "t"
                      :acquired-at (Instant/now)
                      :expires-at (.plusSeconds (Instant/now) 600)}]
      (is (not (client/token-expired? token-info)))))
  (testing "old token is expired"
    (let [token-info {:token "t"
                      :acquired-at (.minusSeconds (Instant/now) 700)
                      :expires-at (.minusSeconds (Instant/now) 100)}]
      (is (client/token-expired? token-info))))
  (testing "token expiring within buffer is expired"
    (let [token-info {:token "t"
                      :acquired-at (.minusSeconds (Instant/now) 580)
                      :expires-at (.plusSeconds (Instant/now) 20)}]
      (is (client/token-expired? token-info))
      (is (not (client/token-expired? token-info 10))))))

(deftest success-test
  (testing "2xx responses are successful"
    (is (client/success? {:status 200}))
    (is (client/success? {:status 201}))
    (is (client/success? {:status 299})))
  (testing "non-2xx responses are not successful"
    (is (not (client/success? {:status 401})))
    (is (not (client/success? {:status 500})))
    (is (not (client/success? {:status 100})))))
