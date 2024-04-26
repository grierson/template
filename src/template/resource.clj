(ns template.resource
  (:require
   [template.resources.aggregate :as aggregate-resource]
   [malli.experimental.lite :as l]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as mcoercion]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [template.resources.discovery :as discovery]
   [template.resources.health :as health]
   [template.resources.events :as events-resource]
   [template.resources.aggregates :as aggregates-resource]))

(require 'hashp.core)

(defn make-routes [{:keys [database]}]
  [["/" {:name :discovery
         :get {:handler discovery/handler}}]
   ["/health"
    {:name :health
     :get {:handler health/handler}}]

   ["/events"
    {:name :events
     :get
     {:parameters {:query {:start (l/optional int?)
                           :end  (l/optional int?)}}
      :handler (partial events-resource/get-handler database)}}]

   ["/aggregate/:id"
    {:name :aggregate
     :get
     {:parameters {:path {:id uuid?}}
      :handler (partial aggregate-resource/get-handler database)}}]

   ["/aggregates"
    {:name :aggregates
     :get
     {:handler (partial aggregates-resource/get-handler database)}
     :post
     {:parameters {:body [:map [:name string?]]}
      :handler (partial aggregates-resource/post-handler database)}}]])

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

(defn make-handler [dependencies] (ring/ring-handler (make-router dependencies)))
