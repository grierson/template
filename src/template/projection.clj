(ns template.projection
  (:require
   [jsonista.core :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(defn upsert [store projection]
  (sql/insert! store :projections projection jdbc/snake-kebab-opts))

(defn get-projections
  ([store]
   (get-projections store 10))
  ([store limit]
   (map
    (fn [e] (update e :projections/data (fn [x] (json/read-value x json/keyword-keys-object-mapper))))
    (sql/query
     store
     ["SELECT * FROM projections LIMIT ?" limit]
     jdbc/snake-kebab-opts))))

(defn get-by-id [store id]
  (let [aggregate (sql/get-by-id store :projections id jdbc/snake-kebab-opts)]
    (update aggregate :projections/data (fn [x] (json/read-value x json/keyword-keys-object-mapper)))))

(defn projection [store id]
  (let [aggregate (sql/get-by-id store :projections id jdbc/snake-kebab-opts)]
    (json/read-value (:projections/data aggregate) json/keyword-keys-object-mapper)))
