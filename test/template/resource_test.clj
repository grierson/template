(ns template.resource-test
  (:require
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [donut.system :as ds]
   [freeport.core :as freeport]
   [halboy.navigator :as navigator]
   [halboy.resource :as hal]
   [placid-fish.core :as uris]
   [template.events :as events]
   [template.system :as system :refer [env-config]]))

(require 'hashp.core)

(defn make-testcontainer-postgres []
  (let [test-container (tc/create {:image-name "postgres:latest"
                                   :env-vars {"POSTGRES_PASSWORD" "postgres"}
                                   :exposed-ports [5432]
                                   :wait-for      {:wait-strategy   :log
                                                   :message         "accept connections"
                                                   :startup-timeout 100}})]
    (tc/start! test-container)))

(comment
  (def container (make-testcontainer-postgres))
  (def store (events/make-store {:dbtype "postgresql"
                                 :dbname "postgres"
                                 :user "postgres"
                                 :password "postgres"
                                 :port (get (:mapped-ports container) 5432)}))
  (events/get-events store)
  (tc/stop! container))

(def test-system (atom nil))

(use-fixtures
  :once
  (fn [f]
    (let [postgres (make-testcontainer-postgres)
          _ (Thread/sleep 1000)
          db-port (get (:mapped-ports postgres) 5432)
          ds (events/get-datasource {:dbtype   "postgresql"
                                     :dbname   "postgres"
                                     :user     "postgres"
                                     :password "postgres"
                                     :port     db-port})
          _ (events/make-store ds)
          webserver {:webserver {:host "0.0.0.0"
                                 :port (freeport/get-free-port!)}}
          database {:database {:port db-port}}
          env-overrides (merge-with into
                                    (env-config :development)
                                    webserver
                                    database)
          config {[:env] env-overrides}
          system (ds/start ::system/test config)
          _ (reset! test-system system)]
      (f)
      (ds/stop system)
      (tc/stop! postgres))))

(use-fixtures :each
  (fn [f]
    (f)
    (let [event-store (get-in @test-system [::ds/instances :components :event-store])]
      (events/clear-store event-store))))

(defn extract [system]
  (let [host (get-in system [::ds/instances :env :webserver :host])
        port (get-in system [::ds/instances :env :webserver :port])]
    {:address (str "http://" host ":" port)
     :handler (get-in system [::ds/instances :http :handler])
     :event-store (get-in system [::ds/instances :components :event-store])}))

(deftest discovery-test
  (let [{:keys [address]} (extract @test-system)
        navigator (navigator/discover address)
        resource (navigator/resource navigator)]

    (testing "has self link"
      (let [self-href (hal/get-href resource :self)]
        (is (uris/absolute? self-href))
        (is (uris/ends-with? self-href "/"))))

    (testing "has health link"
      (let [health-href (hal/get-href resource :health)]
        (is (uris/absolute? health-href))
        (is (uris/ends-with? health-href "/health"))))

    (testing "has events link"
      (let [events-href (hal/get-href resource :events)]
        (is (uris/absolute? events-href))
        (is (uris/ends-with? events-href "/events{?start,end}"))))

    (testing "has aggregate link"
      (let [aggregate-href (hal/get-href resource :aggregate)]
        (is (uris/absolute? aggregate-href))
        (is (uris/ends-with? aggregate-href "/aggregate/{id}"))))

    (testing "has aggregates link"
      (let [aggregates-href (hal/get-href resource :aggregates)]
        (is (uris/absolute? aggregates-href))
        (is (uris/ends-with? aggregates-href "/aggregates"))))))

(deftest health-test
  (let [{:keys [address]} (extract @test-system)
        navigator (-> (navigator/discover address)
                      (navigator/get :health))
        resource (navigator/resource navigator)]

    (testing "Calling health returns 200"
      (is (= 200 (navigator/status navigator))))

    (testing "has self link"
      (let [self-href (hal/get-href resource :self)]
        (is (uris/absolute? self-href))
        (is (uris/ends-with? self-href "/health"))))

    (testing "has discovery link"
      (let [self-href (hal/get-href resource :discovery)]
        (is (uris/absolute? self-href))
        (is (uris/ends-with? self-href "/"))))

    (testing "has health property"
      (let [status (hal/get-property resource :status)]
        (is (= "healthy" status))))))

(deftest events-test
  (let [{:keys [address]} (extract @test-system)
        navigator (-> (navigator/discover address)
                      (navigator/get :events))
        resource (navigator/resource navigator)]

    (testing "Calling events returns 200"
      (is (= 200 (navigator/status navigator))))

    (testing "Contains events"
      (is (= 0 (count (hal/get-property resource :events)))))))

(deftest post-aggregates-test
  (let [{:keys [address]} (extract @test-system)
        name "alice"
        navigator (-> (navigator/discover address {:follow-redirects false})
                      (navigator/post :aggregates {:name name}))
        resource (navigator/resource navigator)]
    (testing "Calling POST returns 201"
      (is (= 201 (navigator/status navigator))))
    (testing "properties"
      (is (= name (hal/get-property resource :name))))))

(deftest get-aggregate-test
  (let [{:keys [address]} (extract @test-system)
        name "alice"
        navigator (-> (navigator/discover address)
                      (navigator/post :aggregates {:name name}))
        resource (navigator/resource navigator)]
    (testing "Calling GET returns 200"
      (is (= 200 (navigator/status navigator))))
    (testing "properties"
      (is (= name (hal/get-property resource :name))))))

(deftest get-aggregates-test
  (let [{:keys [address]} (extract @test-system)
        [name1 name2] ["alice" "bob"]
        _ (-> (navigator/discover address)
              (navigator/post :aggregates {:name name1}))
        _ (-> (navigator/discover address)
              (navigator/post :aggregates {:name name2}))
        navigator (-> (navigator/discover address)
                      (navigator/get :aggregates))
        resource (navigator/resource navigator)]
    (testing "returns 200"
      (is (= 200 (navigator/status navigator))))
    (testing "properties"
      (is (= 2 (count (hal/get-property resource :aggregates)))))))
