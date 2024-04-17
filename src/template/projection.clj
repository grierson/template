(ns template.projection
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(defn upsert [store projection]
  (sql/insert! store :projections projection jdbc/snake-kebab-opts))

(defn get-projections
  ([store] (get-projections store 10))
  ([store limit]
   (sql/query store ["SELECT * FROM projections LIMIT ?" limit] jdbc/snake-kebab-opts)))

(defn projection [store id]
  (sql/get-by-id store :projections id jdbc/snake-kebab-opts))
