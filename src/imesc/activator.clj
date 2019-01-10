(ns imesc.activator
  (:require [clojure.core.async :as async]))

(def response-channel (atom (async/chan)))

(defn schedule
  "Schedules an alarm at `time` and when the time comes, it will put `data` to
  the configured `response-channel`."
  [{::keys [case-repository time data]}])

