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

(defn system []
  (deref (deref (var config/-system))))

(defn polling-wait
  ([success-condition-fn]
   (polling-wait success-condition-fn 10000))
  ([success-condition-fn timeout-ms]
   (polling-wait success-condition-fn timeout-ms 100))
  ([success-condition-fn timeout-ms polling-period-ms]
   (loop [cnt 0]
     (if (success-condition-fn)
       :success
       (if (< cnt (/ timeout-ms polling-period-ms))
         (do (Thread/sleep polling-period-ms) (recur (inc cnt)))
         :failure)))))

(defn shutdown-threads []
  (reset! (:imesc.core/exit-flag (system)) true)
  (polling-wait #(and (realized? (:imesc/activator (system)))
                      (realized? (:imesc/initiator (system))))))

(defn- fast-activator-polling-configuration [config]
  (update-in config [:imesc/activator :poll-millis] (fn [_] 200)))

(defn with-initialized-system [f]
  (config/initialize! (fast-activator-polling-configuration config/config))
  (try (f) (finally (shutdown-threads)))
  (config/halt!))

(use-fixtures :each with-instrumentation with-initialized-system)

(defn dummy-start-request [process-id]
  {:action :start
   :process-id process-id
   :notifications [{:delay-in-seconds 10
                    :channel :console
                    :params {:message "First dummy notification to console."}}
                   {:delay-in-seconds 300
                    :channel :email
                    :params {:to ["orders@example.com"]
                             :subject "You have unconfirmed new orders in RoomOrders."
                             :body "Visit https://roomorders.com."}}
                   {:delay-in-seconds 600
                    :channel :phone
                    :params {:phone-number "38599000001"
                             :message "new-order-unconfirmed"}}]})

(defn create-producer []
  (client/producer {:bootstrap.servers "localhost:9092"
                    :batch.size 0
                    :acks "all"
                    :max.block.ms 9000
                    :request.timeout.ms 9000}
                   (client/string-serializer)
                   (client/edn-serializer)))

(defn- suppose-there-is-an-input-request [ctx]
  (let [process-id (str (java.util.UUID/randomUUID))
        producer (:kafka/producer (system))]
    (logger/info "initiating process id" process-id)
    (client/send! producer config/request-topic process-id (dummy-start-request process-id))
    (client/flush! producer)
    (merge ctx {:process-id process-id})))

(defn- verify-that-the-request-starts-a-process [ctx]
  (logger/info "waiting for process id to appear in db" (:process-id ctx))
  (is (= :success (polling-wait
                   #(alarm/exists? (:repo ctx) (:process-id ctx))))))

(deftest ^:integration basic-system-test
  (testing "successfully creating new escalation process"
    (let [ctx {:repo (:imesc.alarm.mongodb/repository (system))}]
      (-> ctx
          suppose-there-is-an-input-request
          verify-that-the-request-starts-a-process))))

(defn- suppose-there-is-an-active-process [ctx]
  (let [now (ZonedDateTime/now)
        process-id (str (java.util.UUID/randomUUID))
        notifications [{:id "0"
                        :channel :console
                        :params {:message "0"}
                        :delay-in-seconds 1}
                       {:id "1"
                        :channel :phone
                        :params {:phone-number "099111111"
                                 :message "m"}
                        :delay-in-seconds 2}]]
    (is (nil? (alarm/exists? (:repo ctx) process-id)))
    (logger/info "creating process in db" process-id)
    (alarm/set-alarm (:repo ctx) process-id notifications now)
    (merge ctx {:process-id process-id})))

(defn- verify-that-the-process-eventually-finishes [ctx]
  (logger/info "waiting for process to complete, i.e. disappear from db" (:process-id ctx))
  (polling-wait
   #(nil? (alarm/exists? (:repo ctx) (:process-id ctx))))
  ctx)

(deftest ^:integration activator-processing
  (testing "successful handling of active processes"
    (let [ctx {:repo (:imesc.alarm.mongodb/repository (system))}]
      (-> ctx
          suppose-there-is-an-active-process
          verify-that-the-process-eventually-finishes))))
