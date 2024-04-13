(ns template.resource
  (:require
   [malli.experimental.lite :as l]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as mcoercion]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [template.aggregate :as aggregate]
   [template.events :as events]
   [template.projection :as projection]
   [reitit.core :as r]))

(require 'hashp.core)

(defn make-routes [{:keys [event-store]}]
  [["" {:name ::discovery
        :get {:handler (fn [{::r/keys [router]}]
                         {:status 200
                          :body {:links {:self
                                         (-> router
                                             (r/match-by-name ::discovery)
                                             (r/match->path))}}})}}]
   ["/health"
    {:name ::health
     :get
     {:handler (fn [_] {:status 200 :body "healthy"})}}]

   ["/events"
    {:name ::events
     :get
     {:parameters {:query {:start (l/optional int?)
                           :end  (l/optional int?)}}
      :handler (fn [{{{:keys [start end]
                       :or {start 0
                            end (+ start 10)}} :query} :parameters}]
                 {:status 200
                  :body (events/get-events event-store start end)})}}]
   ["/aggregate/:id"
    {:name ::aggregate
     :get
     {:parameters {:path {:id uuid?}}
      :handler (fn [{{{:keys [id]} :path} :parameters}]
                 (let [aggregate (projection/get-by-id event-store id)]
                   {:status 200
                    :body aggregate}))}}]
   ["/aggregates"
    {:name ::aggregates
     :get
     {:handler (fn [_]
                 {:status 200
                  :body (projection/get-projections event-store)})}
     :post
     {:parameters {:body [:map [:name string?]]}
      :handler (fn [{{{:keys [name]} :body} :parameters}]
                 (let [aggregate (aggregate/create-aggregate event-store {:name name})]
                   {:status 201
                    :body (merge
                           aggregate
                           {:links {:self (str "/aggregate/" (:id aggregate))}})}))}}]])

(defn make-router [dependencies]
  (ring/router
   (make-routes dependencies)
   {:data       {:coercion mcoercion/coercion
                 :muuntaja   m/instance
                 :middleware [parameters/parameters-middleware
                              muuntaja/format-negotiate-middleware
                              muuntaja/format-request-middleware
                              muuntaja/format-response-middleware
                              rrc/coerce-request-middleware
                              rrc/coerce-response-middleware]}}))

(defn app [dependencies] (ring/ring-handler (make-router dependencies)))
