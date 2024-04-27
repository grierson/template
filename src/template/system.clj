(ns template.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [donut.system :as ds]
   [donut.system.repl :as dsr]
   [next.jdbc :as jdbc]
   [ring.adapter.jetty :as rj]
   [template.handler :as handler]))

(defn env-config
  [profile]
  (aero/read-config
   (io/resource "config.edn")
   {:profile profile}))

(def base-system
  {::ds/defs
   {:env {}

    :components
    {:database
     #::ds{:start (fn [{:keys [::ds/config]}]
                    (jdbc/get-datasource config))
           :config  (ds/ref [:env :database])}}

    :http
    {:handler
     #::ds{:start  (fn [{{:keys [database]} ::ds/config}]
                     (handler/make-handler {:database database}))
           :config {:database (ds/ref [:components :database])}}

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

(defmethod ds/named-system ::production
  [_]
  (ds/system ::base {[:env] (env-config :production)}))

(defmethod ds/named-system ::development
  [_]
  (ds/system ::base {[:env] (env-config :development)}))

(defmethod ds/named-system :donut.system/repl
  [_]
  (ds/system ::development))

(comment
  (dsr/start)
  (dsr/stop)
  ;; REPL
  (dsr/restart))
