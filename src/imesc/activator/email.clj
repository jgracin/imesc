(ns imesc.activator.email
  (:require [imesc.config :as config]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as kafka]
            [integrant.core :as integrant]
            [clojure.tools.logging :as logger]))

(def partitioning-key :id)

(defmethod integrant/init-key :imesc.activator/email-notifier-adapter [_ {:keys [topic producer]}]
  (fn [request]
    (logger/info "email-notifier-adapter handling email request to topic" topic request)))
