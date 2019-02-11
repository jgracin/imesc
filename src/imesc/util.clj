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
