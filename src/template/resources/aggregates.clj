(ns template.resources.aggregates
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [reitit.core :as reitit]
   [template.aggregate :as aggregate]
   [template.projection :as projection]
   [template.resources.urls :as urls]))

(defn get-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        self-url (urls/url-for router request :aggregates)
        projections (projection/get-projections database)]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-property :aggregates (map :projections/data projections))
               (haljson/resource->json))}))

(defn post-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        {{{:keys [name]} :body} :parameters} request
        aggregate (aggregate/create-aggregate database {:name name})
        self-url (urls/url-for router request :aggregate {:id (:id aggregate)})]
    {:status 201
     :headers {"Location" self-url}
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-properties aggregate)
               (haljson/resource->json))}))

(defn route [dependencies]
  ["/aggregates"
   {:name :aggregates
    :get
    {:handler (partial get-handler dependencies)}
    :post
    {:parameters {:body [:map [:name string?]]}
     :handler (partial post-handler dependencies)}}])
