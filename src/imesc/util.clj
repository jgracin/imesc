(ns imesc.util
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as string]
            [clojure.tools.logging :as logger])
  (:import (java.time ZonedDateTime ZoneId Instant)))

(defmacro ignoring-exceptions [& body]
  `(try ~@body (catch Exception e# (logger/error "skipping exception:" e#))))

(defmacro ignoring-exceptions-but-with-sleep [delay-millis & body]
  `(try ~@body (catch Exception e#
                 (logger/error "skipping exception:" e#)
                 (Thread/sleep ~delay-millis))))

(s/def :common/zoned-date-time
  (let [l #(.getEpochSecond (Instant/parse %))]
    (s/with-gen (partial instance? ZonedDateTime)
      (fn [] (gen/fmap #(.atZone (Instant/ofEpochSecond %)
                                (ZoneId/systemDefault))
                      (s/gen (s/int-in (- (l "1980-01-01T00:00:00.00Z"))
                                       (l "2070-01-01T00:00:00.00Z"))))))))

(s/def :common/non-empty-string
  (s/with-gen
    (s/and string? (complement string/blank?))
    #(gen/not-empty (gen/string-alphanumeric))))

(s/def :common/phone-number :common/non-empty-string)
