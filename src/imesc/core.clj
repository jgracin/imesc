(ns imesc.core
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.tools.logging :as logger]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.set :refer [subset?]]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [imesc.input.kafka :as kafka]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:import imesc.alarm.AlarmRepository
           (java.time ZonedDateTime ZoneId Instant))
  (:gen-class))

(def should-exit? (atom false))

(s/def ::zoned-date-time
  (let [l #(.getEpochSecond (Instant/parse %))]
    (s/with-gen (partial instance? ZonedDateTime)
      (fn [] (gen/fmap #(.atZone (Instant/ofEpochSecond %)
                                (ZoneId/systemDefault))
                      (s/gen (s/int-in (- (l "1980-01-01T00:00:00.00Z"))
                                       (l "2070-01-01T00:00:00.00Z"))))))))
(s/def ::non-empty-string
  (s/with-gen
    (s/and string? (complement string/blank?))
    #(gen/not-empty (gen/string-alphanumeric))))

(s/def :imesc/process-id                ::non-empty-string)
(s/def :imesc/action                    #{:start :stop})
(s/def :notification/channel            #{:console :email :phone})
(s/def :notification/delay-in-seconds   (s/int-in 1 86400))
(s/def :notification/id                 ::non-empty-string)
(s/def :notification/params             any?)
(s/def :notification/notification       (s/keys :req-un [:notification/delay-in-seconds
                                                         :notification/channel]
                                                :opt-un [:notification/params]))
(s/def :notification/notifications      (s/coll-of :notification/notification))
(s/def :imesc/request                   (s/keys :req-un [:imesc/process-id
                                                         :imesc/action]
                                                :opt-un [:notification/notifications]))
(s/def :alarm/id                        ::non-empty-string)
(s/def :alarm/at                        ::zoned-date-time)
(s/def :alarm/notification              (s/keys :req-un [:notification/id
                                                         :notification/delay-in-seconds
                                                         :notification/channel
                                                         :alarm/at]
                                               :opt-un [:notification/params]))
(s/def :alarm/notifications             (s/coll-of :alarm/notification))
(s/def :imesc/alarm-entry               (s/keys :req-un [:alarm/id
                                                         :alarm/at
                                                         :alarm/notifications]))

(defn- same-but-enriched? [coll1 coll2]
  (let [subset-keys? (fn [m1 m2]
                       (subset? (set (keys m1)) (set (keys m2))))]
    (and (= (count coll1) (count coll2))
         (every? true? (map subset-keys? coll1 coll2)))))

(defn alarm-entry [process-id notifications now]
  (let [notifs (map #(assoc %
                            :at (.plusSeconds now (:delay-in-seconds %))
                            :id (str (java.util.UUID/randomUUID)))
                    notifications)
        earliest-of (fn [notifications]
                      (reduce (fn [t1 t2]
                                (if (.isBefore (:at t1) (:at t2)) t1 t2))
                              notifications))]
    {:id process-id
     :at (:at (earliest-of notifs))
     :notifications notifs}))

(s/fdef alarm-entry
  :args (s/cat :process-id :imesc/process-id
               :notifications (s/coll-of :notification/notification :min-count 1)
               :now ::zoned-date-time)
  :ret :imesc/alarm-entry
  :fn (fn [m] (same-but-enriched? (-> m :args :notifications)
                                 (-> m :ret :notifications))))

(defmacro ignoring-exceptions [& body]
  `(try ~@body (catch Exception e# (logger/error "skipping exception:" e#))))

(defmacro ignoring-exceptions-but-with-sleep [delay-millis & body]
  `(try ~@body (catch Exception e#
                 (logger/error "skipping exception:" e#)
                 (Thread/sleep ~delay-millis))))

(defn valid? [request]
  (s/valid? :imesc/request request))

(defn decide-next-action [request process-already-exists?]
  (cond
    (and (= :start (:action request))
         (not process-already-exists?))
    :create-new-process

    (= :stop (:action request))
    :cancel-process

    :else
    :ignore-request))

(s/fdef decide-next-action
  :args (s/cat :request :imesc/request :process-exists? boolean?)
  :ret #{:create-new-process :cancel-process :ignore-request})

(defn process-request [^AlarmRepository r request]
  (let [pid (:process-id request)
        process-already-exists? (boolean (alarm/exists? r pid))
        now (java.time.ZonedDateTime/now)]
    (case (decide-next-action request process-already-exists?)
      :create-new-process
      (alarm/insert r (alarm-entry (:process-id request) (:notifications request) now))

      :cancel-process
      (alarm/delete r pid)

      :ignore-request
      (logger/debug "ignoring request for process" pid "because it does not change state")

      :else
      (logger/error "BUG: unknown scenario"))))

(defn make-main-input-loop
  "Construct the main input loop based on polling.

  The function request-supplying-fn is called without arguments and is expected
  to return a sequence of requests which need to be processed.

  The function request-processing-fn is called with single argument begin
  request and its return value is ignored.

  The function exit-condition-fn is called without arguments and is expected to
  return true or false. When true, the loop will finish."
  [exit-condition-fn request-supplying-fn request-processing-fn]
  (fn []
    (logger/info "Entering main input loop...")
    (loop []
      (ignoring-exceptions-but-with-sleep
       1000
       (doseq [request (request-supplying-fn)]
         (logger/debug "processing" (pr-str request))
         (ignoring-exceptions
          (if-not (valid? request)
            (logger/warn "ignoring invalid request with process-id" (:process-id request))
            (request-processing-fn request)))))
      (when-not (exit-condition-fn) (recur)))
    (logger/info "Main input loop finished.")))

#_(s/fdef make-main-input-loop
  :args (s/cat :exit-condition-fn (s/fspec :args (s/cat) :ret boolean?)
               :request-supplying-fn (s/fspec :args (s/cat) :ret (s/coll-of :imesc/request))
               :request-processing-fn (s/fspec :args (s/cat :request :imesc/request) :ret any?)))

(defn initialize!
  ([]
   (initialize! config/config))
  ([configuration]
   (-> configuration
       integrant/prep
       integrant/init)))

(def kafka-request-supplying-fn
  #(->> (kafka/poll-request (:kafka/request-consumer @config/system))
        (map (comp edn/read-string :value))))

(defn make-kafka-based-main-input-loop [exit-condition-fn]
  (make-main-input-loop exit-condition-fn
                        kafka-request-supplying-fn
                        (partial process-request (:alarm/repository @config/system))))

(defn -main
  "Starts the system."
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(reset! should-exit? false)))
  (reset! config/system (initialize!))
  (let [main-loop (make-kafka-based-main-input-loop (fn [] @should-exit?))]
    (future (main-loop))))

