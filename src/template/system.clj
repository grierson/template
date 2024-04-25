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
           :config  (ds/ref [:env :database])}}

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
  (tc/start! (tc/create {:image-name "postgres:latest"
                         :env-vars {"POSTGRES_PASSWORD" "postgres"}
                         :exposed-ports [5432]
                         :wait-for      {:wait-strategy   :log
                                         :message         "accept connections"
                                         :startup-timeout 100}})))

(defmethod ds/named-system ::test
  [_]
  (let [container (make-testcontainer-postgres)
        webserver {:webserver {:port (freeport/get-free-port!)}}
        database {:database {:port (get (:mapped-ports container) 5432)}}
        env-config (merge-with into
                               (env-config :development)
                               webserver
                               database)
        config {[:env] env-config}]
    (ds/system ::base config)))

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
  (def store (events/make-store {:dbtype "postgresql"
                                 :dbname "postgres"
                                 :user "postgres"
                                 :password "postgres"
                                 :port (get (:mapped-ports container) 5432)}))
  (events/get-events store)
  (tc/stop! container))

