(ns imesc.alarm.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(s/def :alarm/id                        :common/non-empty-string)
(s/def :alarm/at                        :common/zoned-date-time)
(s/def :alarm/notification              (s/keys :req-un [:notification/id
                                                         :alarm/at
                                                         :notification/delay-in-seconds
                                                         :notification/channel]
                                                :opt-un [:notifier/params]))
(s/def :alarm/notifications             (s/coll-of :alarm/notification :min-count 1))
(s/def :alarm/alarm                     (s/keys :req-un [:alarm/id
                                                         :alarm/at
                                                         :alarm/notifications]))
