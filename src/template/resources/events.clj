(ns template.resources.events
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [malli.experimental.lite :as l]
   [reitit.core :as reitit]
   [template.events :as events]
   [template.resources.urls :as urls]))

(defn get-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        self-url (urls/url-for router request :events)
        {{{:keys [start end]
           :or {start 0
                end (+ start 10)}} :query} :parameters} request
        events (events/get-events database start end)]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-property :events events)
               (haljson/resource->json))}))

(defn route [dependencies]
  ["/events"
   {:name :events
    :get
    {:parameters {:query {:start (l/optional int?)
                          :end  (l/optional int?)}}
     :handler (partial get-handler dependencies)}}])
