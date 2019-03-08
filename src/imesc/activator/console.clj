(ns imesc.activator.console
  (:require [imesc.activator :as activator]
            [integrant.core :as integrant]
            [clojure.tools.logging :as logger]))

(defmethod integrant/init-key :imesc.activator/console-notifier-adapter [_ config]
  (fn [request]
    (logger/info "console notifier adapter handling request" request)))
