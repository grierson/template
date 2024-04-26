(ns template.resource
  (:require
   [template.aggregate.route :as aggregate]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as mcoercion]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [template.resources.discovery :as discovery]
   [template.resources.health :as health]
   [template.resources.events :as events-resource]))

(defn make-routes [dependencies]
  [discovery/route
   health/route
   (events-resource/route dependencies)
   (aggregate/aggregate-route dependencies)
   (aggregate/aggregates-route dependencies)])

(defn make-router [routes]
  (ring/router
   routes
   {:data
    {:coercion mcoercion/coercion
     :muuntaja   m/instance
     :middleware [parameters/parameters-middleware
                  muuntaja/format-negotiate-middleware
                  muuntaja/format-request-middleware
                  muuntaja/format-response-middleware
                  rrc/coerce-request-middleware
                  rrc/coerce-response-middleware]}}))

(defn make-handler [dependencies]
  (-> dependencies
      make-routes
      make-router
      ring/ring-handler))
