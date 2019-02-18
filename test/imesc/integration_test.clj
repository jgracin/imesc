(ns imesc.integration-test
  (:require [clojure.test :refer :all]
            [imesc.core :as core]
            [imesc.initiator :as initiator]
            [imesc.activator :as activator]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [environ.core :refer [env]]
            [kinsky.client :as client]
            [integrant.core :as integrant]
            [clojure.tools.logging :as logger]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [orchestra.spec.test])
  (:import [java.time Duration ZonedDateTime]))

(defn with-instrumentation [f]
  (orchestra.spec.test/instrument)
  (f))

(defn with-initialized-system [f]
  (reset! config/system (core/initialize!))
  (f)
  (integrant/halt! @config/system))

(use-fixtures :once with-instrumentation with-initialized-system)

(defn dummy-start-request [process-id]
  {:action :start
   :process-id process-id
   :notifications [{:delay-in-seconds 10
                    :channel :console
                    :params {:message "First dummy notification to console."}}
                   {:delay-in-seconds 15
                    :channel :console
                    :params {:message "Second dummy notification to console."}}
                   {:delay-in-seconds 300
                    :channel :email
                    :params {:to ["orders@example.com"]
                             :subject "You have unconfirmed new orders in RoomOrders."
                             :body "Visit https://roomorders.com."}}
                   {:delay-in-seconds 600
                    :channel :phone
                    :params {:phone-number "38599000001"
                             :message "new-order-unconfirmed"}}]})

(def input-topic "imesc.requests")

(defn polling-wait
  ([success-condition-fn]
   (polling-wait success-condition-fn 60000))
  ([success-condition-fn timeout-ms]
   (polling-wait success-condition-fn timeout-ms 100))
  ([success-condition-fn timeout-ms polling-period-ms]
   (loop [cnt 0]
     (if (success-condition-fn)
       :success
       (if (< cnt (/ timeout-ms polling-period-ms))
         (do (Thread/sleep polling-period-ms) (recur (inc cnt)))
         :failure)))))

(defn create-producer []
  (client/producer {:bootstrap.servers "localhost:9092"
                    :batch.size 0
                    :acks "all"
                    :max.block.ms 9000
                    :request.timeout.ms 9000}
                   (client/string-serializer)
                   (client/edn-serializer)))

(deftest ^:integration basic-system-test
  (testing "successfully creating a new escalation process"
    (let [should-exit? (atom false)
          main-loop (core/make-kafka-based-main-input-loop (fn [] @should-exit?))
          process-id (str (java.util.UUID/randomUUID))
          producer (create-producer)]
      (client/send! producer input-topic process-id (dummy-start-request process-id))
      (client/flush! producer)
      (let [t (future (main-loop))]
        (try
          (logger/info "waiting for process id to appear in db" process-id)
          (is (= :success (polling-wait
                           #(alarm/exists? (:alarm/repository @config/system) process-id))))
          (finally (reset! should-exit? true)))
        (polling-wait #(future-done? t))))))

(deftest ^:integration activator
  (testing "succesfully processing activations"
    (let [now (ZonedDateTime/now)
          alarm {:id "0000" :at now
                 :notifications [{:id "0" :at (.plusMinutes now 0)
                                  :channel :console
                                  :params {:message "0"}
                                  :delay-in-seconds 1}
                                 {:id "1" :at (.plusMinutes now 1)
                                  :channel :console
                                  :params {:message "1"}
                                  :delay-in-seconds 1}
                                 {:id "2" :at (.plusMinutes now 2)
                                  :channel :console
                                  :params {:message "2"}
                                  :delay-in-seconds 1}
                                 {:id "3" :at (.plusMinutes now 3)
                                  :channel :console
                                  :params {:message "3"}
                                  :delay-in-seconds 1}]}]
      (is nil? (activator/process alarm (:alarm/repository @config/system) (.plusMinutes now 2))))))

(comment
  (def p (create-producer))
  (let [process-id (str (java.util.UUID/randomUUID))]
    (client/send! p input-topic process-id (dummy-start-request process-id)))
  

  )
