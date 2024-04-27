(ns template.aggregate.route
  (:require
   [halboy.json :as haljson]
   [halboy.resource :as resource]
   [reitit.core :as reitit]
   [template.aggregate.projection :as projection]
   [template.audit :as audit]
   [template.resources.urls :as urls]))

(defn get-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        {{{:keys [id]} :path} :parameters} request
        aggregate (projection/project database id)
        self-url (urls/url-for router request :aggregate {:id id})]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links
                {:discovery (urls/url-for router request :discovery)})
               (resource/add-properties aggregate)
               (haljson/resource->json))}))

(defn list-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        projections (audit/get-projections database)
        self-url (urls/url-for router request :aggregates)]
    {:status 200
     :body (-> (resource/new-resource self-url)
               (resource/add-links
                {:discovery (urls/url-for router request :discovery)})
               (resource/add-property :aggregates (map :projections/data projections))
               (haljson/resource->json))}))

(defn create-handler [{:keys [database]} request]
  (let [{::reitit/keys [router]} request
        {{{:keys [name]} :body} :parameters} request
        aggregate (projection/create-projection! database {:name name})
        self-url (urls/url-for router request :aggregate {:id (:id aggregate)})]
    {:status 201
     :headers {"Location" self-url}
     :body (-> (resource/new-resource self-url)
               (resource/add-links {:discovery (urls/url-for router request :discovery)})
               (resource/add-properties (:data aggregate))
               (haljson/resource->json))}))

(defn route [dependencies]
  [["/aggregate/:id"
    {:name :aggregate
     :get
     {:parameters {:path {:id uuid?}}
      :handler (partial get-handler dependencies)}}]
   ["/aggregates"
    {:name :aggregates
     :get {:handler (partial list-handler dependencies)}
     :post {:parameters {:body [:map [:name string?]]}
            :handler (partial create-handler dependencies)}}]])
