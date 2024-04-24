(ns template.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [donut.system :as ds]
   [donut.system.repl :as dsr]
   [ring.adapter.jetty :as rj]
   [template.events :as events]
   [template.resource :as resource]
   [freeport.core :as freeport]
   [clj-test-containers.core :as tc]))

(defn env-config
  [profile]
  (aero/read-config (io/resource "config.edn") {:profile profile}))

(def base-system
  {::ds/defs
   {:env {}

    :components
    {:event-store
     #::ds{:start (fn [{:keys [::ds/config]}]
                    (events/make-store config))
           :stop (fn [{:keys [::ds/instance]}]
                   (events/kill-store instance))
           :config {:dbtype "postgresql"
                    :dbname "postgres"
                    :user "postgres"
                    :password "postgres"}}}

    :http
    {:handler
     #::ds{:start  (fn [{{:keys [event-store]} ::ds/config}]
                     (resource/make-handler {:event-store event-store}))
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

(defmethod ds/named-system ::production
  [_]
  (ds/system ::base {[:env] (env-config :production)}))

(defmethod ds/named-system ::development
  [_]
  (ds/system ::base {[:env] (env-config :development)}))

(defn make-testcontainer-postgres []
  (let [container (-> (tc/create {:image-name "postgres:latest"
                                  :exposed-ports [5432]
                                  :env-vars {"POSTGRES_PASSWORD" "postgres"}}))]
    container))

(defmethod ds/named-system ::test
  [_]
  (ds/system ::base {[:env] {:webserver
                             {:host "0.0.0.0"
                              :port (freeport/get-free-port!)}

                             :database
                             (make-testcontainer-postgres)}}))

(defmethod ds/named-system :donut.system/repl
  [_]
  (ds/system ::development))

(comment
  (dsr/start)
  (dsr/stop)
  ;; REPL
  (dsr/restart))

(comment
  (def container (make-testcontainer-postgres))
  #p (tc/start! container)
  (tc/stop! container))

