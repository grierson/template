(ns template.resource
  (:require
   [reitit.ring :as ring]
   [malli.experimental.lite :as l]
   [reitit.coercion.malli :as mcoercion]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [muuntaja.core :as m]
   [template.aggregate :as aggregate]
   [template.event-store :as events]))

(require 'hashp.core)

(defn app
  [{:keys [event-store]}]
  (ring/ring-handler
   (ring/router
    [["/health"
      {:get
       {:handler (fn [_]
                   {:status 200
                    :body "healthy"})}}]
     ["/events"
      {:get
       {:parameters {:query {:start (l/optional int?)
                             :end  (l/optional int?)}}
        :handler (fn [{{{:keys [start end]
                         :or {start 0
                              end (+ start 10)}} :query} :parameters}]
                   {:status 200
                    :body (events/get-events event-store start end)})}}]
     ["/aggregate/:id"
      {:get
       {:parameters {:path {:id int?}}
        :handler (fn [{{{:keys [id]} :path} :parameters}]
                   (let [events (events/get-aggregate-events event-store id)
                         aggregate (aggregate/project events)]
                     {:status 200
                      :body aggregate}))}}]
     ["/aggregates"
      {:post
       {:parameters {:body [:map [:name string?]]}
        :handler (fn [{{{:keys [name]} :body} :parameters}]
                   (let [aggregate (aggregate/create-aggregate event-store {:name name})]
                     {:status 201
                      :body aggregate}))}}]]
    {:data       {:coercion mcoercion/coercion
                  :muuntaja   m/instance
                  :middleware [parameters/parameters-middleware
                               muuntaja/format-negotiate-middleware
                               muuntaja/format-request-middleware
                               muuntaja/format-response-middleware
                               rrc/coerce-request-middleware
                               rrc/coerce-response-middleware]}})))
