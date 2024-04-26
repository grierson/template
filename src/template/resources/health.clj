(ns template.resources.health
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [reitit.core :as reitit]
   [template.resources.urls :as urls]))

(defn get-handler [request]
  (let [{::reitit/keys [router]} request
        self-url (urls/url-for router request :health)]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-property :status "healthy")
               (haljson/resource->json))}))

(def route ["/health"
            {:name :health
             :get {:handler get-handler}}])
