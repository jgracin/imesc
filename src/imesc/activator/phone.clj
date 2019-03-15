(ns imesc.activator.phone
  (:require [imesc.config :as config]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as kafka]
            [integrant.core :as integrant]
            [kinsky.client :as client]
            [clojure.tools.logging :as logger]))

(def partitioning-key :id)

(defmethod integrant/init-key :imesc.activator/phone-notifier-adapter [_ {:keys [topic producer]}]
  (fn [request]
    (logger/info "phone notifier adapter sending request to topic" topic request)
    (client/send! producer topic (:id request) request)))
