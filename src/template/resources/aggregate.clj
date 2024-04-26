(ns template.resources.aggregate
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [reitit.core :as reitit]
   [template.projection :as projection]
   [template.resources.urls :as urls]))

(defn get-handler [database request]
  (let [{::reitit/keys [router]} request
        {{{:keys [id]} :path} :parameters} request
        aggregate (projection/projection database id)
        self-url (urls/url-for router request :aggregate {:id id})]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-properties (:projections/data aggregate))
               (haljson/resource->json))}))
