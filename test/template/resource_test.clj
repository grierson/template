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

(defn request->map [request]
  (-> request
      :body
      slurp
      (json/read-value json/keyword-keys-object-mapper)))

(deftest health-test
  (let [{:keys [handler]} (extract ds/*system*)
        request (handler {:request-method :get
                          :uri "/health"})]
    (testing "Calling health returns 200"
      (is (= {:status 200
              :body  "healthy"}
             request)))))

(deftest events-test
  (let [{:keys [handler]} (extract ds/*system*)
        request (handler {:request-method :get
                          :uri "/events"})]
    (testing "/events"
      (testing "Calling events returns 200"
        (is (= 200 (:status request))))
      (testing "Contains events"
        (is (= 0 (count (request->map request))))))))

(deftest events-found-test
  (let [{:keys [handler]} (extract ds/*system*)
        name "alice"
        _ (handler {:request-method :post
                    :uri "/aggregates"
                    :body-params {:name name}})
        request (handler {:request-method :get
                          :uri "/events"})
        events (request->map request)
        event (first events)]
    (testing "/events"
      (testing "Calling events returns 200"
        (is (= 200 (:status request))))
      (testing "Contains events"
        (is (= 1 (count events)))
        (is (= {:name name} (:events/data event)))
        (is (= "aggregate-created" (:events/type event)))))))

(deftest post-aggregates-test
  (let [{:keys [handler]} (extract ds/*system*)
        name "alice"
        request (handler {:request-method :post
                          :uri "/aggregates"
                          :body-params {:name name}})
        response (request->map request)]
    (testing "/aggregates"
      (testing "Calling POST returns 201"
        (is (= 201 (:status request))))
      (testing "Contains properties"
        (is (= name (:name response)))))))

(deftest get-aggregate-test
  (let [{:keys [handler]} (extract ds/*system*)
        name "alice"
        created-aggregate
        (handler {:request-method :post
                  :uri "/aggregates"
                  :body-params {:name name}})
        data #p (request->map created-aggregate)
        request (handler {:request-method :get
                          :uri (get-in data [:links :self])})
        response #p (request->map request)]
    (testing "/aggregate/:id"
      (testing "Calling GET returns 200"
        (is (= 200 (:status request))))
      (testing "Contains properties"
        (is (= name (:name response)))))))

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
        response (request->map request)]
    (testing "GET /aggregates"
      (testing "returns 200"
        (is (= 200 (:status request)))))))
