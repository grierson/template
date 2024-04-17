(ns template.resource
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [malli.experimental.lite :as l]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as mcoercion]
   [reitit.core :as r]
   [reitit.impl :as impl]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [template.aggregate :as aggregate]
   [template.events :as events]
   [template.projection :as projection]))

(require 'hashp.core)

(defn base-url
  [{:keys [scheme headers]}]
  (let [host (get headers "host")]
    (str (name scheme) "://" host)))

(defn url-for
  ([router request name] (url-for router request name {}))
  ([router request name path-params]
   (let [match (r/match-by-name router name path-params)
         template (:template match)
         route (impl/parse template (r/options router))
         base (base-url request)
         path (impl/path-for route path-params)]
     (str base path))))

(defn make-routes [{:keys [event-store]}]
  [["/" {:name ::discovery
         :get {:handler (fn [request]
                          (let [{::r/keys [router]} request
                                self-url (url-for router request ::discovery)]
                            {:status 200
                             :body (-> (resource/new-resource self-url)
                                       (resource/add-links
                                        {:health (url-for router request ::health)
                                         :events (str (url-for router request ::events) "{?start,end}")
                                         :aggregate (url-for router request ::aggregate {:id "{id}"})
                                         :aggregates (url-for router request ::aggregates)})
                                       (haljson/resource->json))}))}}]
   ["/health"
    {:name ::health
     :get
     {:handler (fn [request]
                 (let [{::r/keys [router]} request
                       self-url (url-for router request ::health)]
                   {:status 200
                    :body (-> (resource/new-resource self-url)
                              (resource/add-links {:discovery (url-for router request ::discovery)})
                              (resource/add-property :status "healthy")
                              (haljson/resource->json))}))}}]

   ["/events"
    {:name ::events
     :get
     {:parameters {:query {:start (l/optional int?)
                           :end  (l/optional int?)}}
      :handler (fn [request]
                 (let [{::r/keys [router]} request
                       self-url (url-for router request ::events)
                       {{{:keys [start end]
                          :or {start 0
                               end (+ start 10)}} :query} :parameters} request
                       events (events/get-events event-store start end)]
                   {:status 200
                    :body (-> (resource/new-resource self-url)
                              (resource/add-links {:discovery (url-for router request ::discovery)})
                              (resource/add-property :events events)
                              (haljson/resource->json))}))}}]

   ["/aggregate/:id"
    {:name ::aggregate
     :get
     {:parameters {:path {:id uuid?}}
      :handler (fn [request]
                 (let [{::r/keys [router]} request
                       {{{:keys [id]} :path} :parameters} request
                       aggregate (projection/projection event-store id)
                       self-url (url-for router request ::aggregate {:id id})]
                   {:status 200
                    :body (-> (resource/new-resource self-url)
                              (resource/add-links {:discovery (url-for router request ::discovery)})
                              (resource/add-properties (:projections/data aggregate))
                              (haljson/resource->json))}))}}]

   ["/aggregates"
    {:name ::aggregates
     :get
     {:handler (fn [request]
                 (let [{::r/keys [router]} request
                       self-url (url-for router request ::aggregates)
                       projections (projection/get-projections event-store)]
                   {:status 200
                    :body (-> (resource/new-resource self-url)
                              (resource/add-links {:discovery (url-for router request ::discovery)})
                              (resource/add-property :aggregates (map :projections/data projections))
                              (haljson/resource->json))}))}
     :post
     {:parameters {:body [:map [:name string?]]}
      :handler (fn [request]
                 (let [{::r/keys [router]} request
                       {{{:keys [name]} :body} :parameters} request
                       aggregate (aggregate/create-aggregate event-store {:name name})
                       self-url (url-for router request ::aggregate {:id (:id aggregate)})]
                   {:status 201
                    :headers {"Location" self-url}
                    :body (-> (resource/new-resource self-url)
                              (resource/add-links {:discovery (url-for router request ::discovery)})
                              (resource/add-properties aggregate)
                              (haljson/resource->json))}))}}]])

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
