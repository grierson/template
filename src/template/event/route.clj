(ns template.event.route
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [malli.experimental.lite :as l]
   [reitit.core :as reitit]
   [template.audit :as audit]
   [template.resources.urls :as urls]))

(defn list-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        self-url (urls/url-for router request :events)
        {{{:keys [start end]
           :or {start 0
                end (+ start 10)}} :query} :parameters} request
        events (audit/get-events database start end)
        event-urls (map
                    (fn [{:keys [:events/id]}] (urls/url-for router request :event {:id id}))
                    events)
        event-resources (map resource/new-resource event-urls)]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-resources :events event-resources)
               (haljson/resource->json))}))

(defn get-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        {{{:keys [id]} :path} :parameters} request
        {:keys [:events/data :events/type]} (audit/get-event database id)
        properties (merge data {:type type})
        self-url (urls/url-for router request :event {:id id})]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-properties properties)
               (haljson/resource->json))}))

(defn route [dependencies]
  [["/event/:id"
    {:name :event
     :get {:parameters {:path {:id uuid?}}
           :handler (partial get-handler dependencies)}}]
   ["/events"
    {:name :events
     :get {:parameters {:query {:start (l/optional int?)
                                :end  (l/optional int?)}}
           :handler (partial list-handler dependencies)}}]])
