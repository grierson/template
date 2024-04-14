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
   [reitit.core :as r]
   [reitit.impl :as impl]
   [halboy.resource :as resource]))

(require 'hashp.core)

(defn url-for
  ([router name] (url-for router name {}))
  ([router name path-params]
   (let [match (r/match-by-name router name path-params)
         template (:template match)
         route (impl/parse template (r/options router))]
     (impl/path-for route path-params))))

(defn make-routes [{:keys [event-store]}]
  [["/" {:name ::discovery
         :get {:handler (fn [{::r/keys [router]}]
                          {:status 200
                           :body (-> (resource/new-resource (url-for router ::discovery))
                                     (resource/add-links
                                      {:events (str (url-for router ::events) "{?start,end}")
                                       :aggregate (url-for router ::aggregate {:id "{id}"})
                                       :aggregates (url-for router ::aggregates)}))})}}]
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
      :handler (fn [request]
                 (let [{::r/keys [router]} request
                       {{{:keys [name]} :body} :parameters} request
                       aggregate (aggregate/create-aggregate event-store {:name name})]
                   {:status 201
                    :body (merge
                           aggregate
                           {:links
                            {:self (url-for router ::aggregate {:id (:id aggregate)})}})}))}}]])

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
