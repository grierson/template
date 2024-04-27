(ns template.helper
  (:require
   [clj-test-containers.core :as tc]
   [donut.system :as ds]
   [next.jdbc :as jdbc]
   [template.audit :as audit]
   [freeport.core :as freeport]
   [template.system :as system]))

(defn make-postgres-testcontainer [{:keys [image password port]}]
  (let [test-container
        (tc/create {:image-name image
                    :env-vars {"POSTGRES_PASSWORD" password}
                    :exposed-ports [port]
                    :wait-for      {:wait-strategy   :log
                                    :message         "accept connections"
                                    :times           2
                                    :startup-timeout 10}})]
    (tc/start! test-container)))

(defmethod ds/named-system ::test
  [_]
  (let [{:keys [database] :as env} (system/env-config :development)

        postgres-container
        (make-postgres-testcontainer
         (select-keys database [:image :password :port]))

        postgres-container-port
        (get (:mapped-ports postgres-container) (:port database))

        db-spec (merge database {:port postgres-container-port})
        datasource (jdbc/get-datasource db-spec)
        _ (audit/make-tables datasource)

        database {:database {:port postgres-container-port}}

        webserver-port (freeport/get-free-port!)
        _ (println "running on: " webserver-port)
        webserver {:webserver {:port webserver-port}}
        db-component
        #::ds{:start (fn [_] postgres-container)
              :stop (fn [{:keys [::ds/instance]}] (tc/stop! instance))}

        env-config (merge-with into
                               env
                               webserver
                               database)
        config {[:env] env-config
                [:test :database] db-component}]
    (ds/system ::system/base config)))
