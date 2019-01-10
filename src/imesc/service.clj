(ns imesc.service
  (:require [imesc.config :as config]
            [imesc.case-repository]))

(defn ->error-response [s]
  {:status 500
   :description (str s)})

(defn duplicate-case-error-report [data])

(defn- do-open-case [case-repository data]
  (try
    (imesc.case-repository/save-case case-repository data)
    (catch Exception e
      (case (:type (ex-data e))
        :duplicate-case (->error-response (duplicate-case-error-report data))
        :illegal-escalation-specification (->error-response (ex-data e))
        (throw e)))))

(defn- collect [repository data]
  {:id 12340})

(defn open-case [data]
  (collect (:imesc.case-repository/case-repository @config/system) data))

(defn- do-close-case [case-repository data])

(defn close-case [data]
  (do-close-case (:imesc.case-repository/case-repository @config/system) data))
