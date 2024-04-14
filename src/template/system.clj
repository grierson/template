(ns template.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [donut.system :as ds]
   [donut.system.repl :as dsr]
   [ring.adapter.jetty :as rj]
   [template.events :as events]
   [template.resource :as resource]))

(defn env-config [profile]
  (aero/read-config
   (io/resource "config.edn")
   (when profile {:profile profile})))

(def base-system
  {::ds/defs
   {:env {}

    :components
    {:event-store
     #::ds{:start (fn [_] (events/make-store))
           :stop (fn [{:keys [::ds/instance]}]
                   (events/kill-store instance))}}

    :http
    {:handler
     #::ds{:start  (fn [{{:keys [event-store]} ::ds/config}]
                     (resource/app {:event-store event-store}))
           :config {:event-store (ds/ref [:components :event-store])}}

     :server
     #::ds{:start (fn [{{:keys [handler options]} ::ds/config}]
                    (rj/run-jetty handler options))
           :stop  (fn [{:keys [::ds/instance]}]
                    (.stop instance))
           :config  {:handler (ds/local-ref [:handler])
                     :options {:port (ds/ref [:env :webserver :port])
                               :join? false}}}}}})

(defmethod ds/named-system ::base
  [_]
  base-system)

(defmethod ds/named-system :production
  [_]
  (ds/system ::base {[:env] (env-config :production)}))

(defmethod ds/named-system :development
  [_]
  (ds/system ::base {[:env] (env-config :development)}))

(defmethod ds/named-system :test
  [_]
  (ds/system ::base {[:env] (env-config :test)
                     [:http :server] ::disabled}))

(defmethod ds/named-system :donut.system/repl
  [_]
  (ds/system :development))
(comment
  (dsr/start)
  (dsr/stop)
  ;; REPL
  (dsr/restart))
