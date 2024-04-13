(ns template.resource-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [template.system :as system]
   [donut.system :as ds]
   [jsonista.core :as json]))

(require 'hashp.core)

(use-fixtures :each (ds/system-fixture ::system/test))

(defn extract [system]
  {:handler (get-in system [::ds/instances :http :handler])
   :event-store (get-in system [::ds/instances :components :event-store])})

(defn request->resource [request]
  (-> request
      :body
      slurp
      (json/read-value json/keyword-keys-object-mapper)))

(deftest discovery-test
  (let [{:keys [handler]} (extract ds/*system*)
        request (handler {:request-method :get
                          :uri "/"})
        resource (request->resource request)]
    (testing "returns 200"
      (is (= 200 (:status request))))
    (testing "contains discovery link"
      (is (= {:links {:self "/"
                      :events "/events"}} resource)))))

(deftest health-test
  (let [{:keys [handler]} (extract ds/*system*)
        request (handler {:request-method :get
                          :uri "/health"})]
    (testing "Calling health returns 200"
      (is (= {:status 200 :body "healthy"}
             request)))))

(deftest events-test
  (let [{:keys [handler]} (extract ds/*system*)
        request (handler {:request-method :get
                          :uri "/events"})]
    (testing "/events"
      (testing "Calling events returns 200"
        (is (= 200 (:status request))))
      (testing "Contains events"
        (is (= 0 (count (request->resource request))))))))

(deftest events-found-test
  (let [{:keys [handler]} (extract ds/*system*)
        name "alice"

        post-aggregregates-request
        (handler {:request-method :post
                  :uri "/aggregates"
                  :body-params {:name name}})
        post-aggregates-resource
        (request->resource post-aggregregates-request)

        events-request (handler {:request-method :get
                                 :uri "/events"})
        events-resource (request->resource events-request)
        event (first events-resource)]
    (testing "/events"
      (testing "Calling events returns 200"
        (is (= 200 (:status events-request))))
      (testing "Contains events"
        (is (= 1 (count events-resource)))
        (is (= {:name name :id (:id post-aggregates-resource)}
               (:events/data event)))
        (is (= "aggregate-created" (:events/type event)))))))

(deftest post-aggregates-test
  (let [{:keys [handler]} (extract ds/*system*)
        name "alice"
        request (handler {:request-method :post
                          :uri "/aggregates"
                          :body-params {:name name}})
        resource (request->resource request)]
    (testing "/aggregates"
      (testing "Calling POST returns 201"
        (is (= 201 (:status request))))
      (testing "Contains properties"
        (is (= name (:name resource)))))))

(deftest get-aggregate-test
  (let [{:keys [handler]} (extract ds/*system*)
        name "alice"

        post-aggregates-request
        (handler {:request-method :post
                  :uri "/aggregates"
                  :body-params {:name name}})
        post-aggregates-resource
        (request->resource post-aggregates-request)

        request
        (handler {:request-method :get
                  :uri (get-in post-aggregates-resource [:links :self])})

        resource (request->resource request)]
    (testing "/aggregate/:id"
      (testing "Calling GET returns 200"
        (is (= 200 (:status request))))
      (testing "Contains properties"
        (is (= name (get-in resource [:projections/data :name])))))))

(deftest get-aggregates-test
  (let [{:keys [handler]} (extract ds/*system*)
        [name1 name2] ["alice" "bob"]
        _ (handler {:request-method :post
                    :uri "/aggregates"
                    :body-params {:name name1}})
        _ (handler {:request-method :post
                    :uri "/aggregates"
                    :body-params {:name name2}})
        request (handler {:request-method :get
                          :uri "/aggregates"})
        resource (request->resource request)]
    (testing "GET /aggregates"
      (testing "returns 200"
        (is (= 200 (:status request))))
      (testing "properties"
        (is (= 2 (count resource)))))))
