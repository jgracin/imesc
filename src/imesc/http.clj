(ns imesc.http
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.response :as resp]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [compojure.core :refer [GET POST defroutes routes]]
            [compojure.route :as route]
            [environ.core :refer [env]]))

(defroutes main-routes
  (POST "/status" {:status 200})
  (route/not-found "Not found."))

(defmethod integrant/init-key :adapter/jetty
  [_ {:keys [handler] :as opts}]
  (jetty/run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod integrant/halt-key! :adapter/jetty
  [_ server]
  (.stop server))

(defmethod integrant/init-key :handler/main-handler
  [_ {:keys [service]}]
  (-> main-routes
      wrap-session
      (wrap-json-body {:keywords? true :bigdecimals? true})
      wrap-json-response))
