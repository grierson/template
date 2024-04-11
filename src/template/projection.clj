(ns template.projection
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(defn upsert [store projection]
  (sql/insert! store :projections projection jdbc/snake-kebab-opts))

(defn fetch [store id]
  #p (sql/query store ["SELECT * FROM projections"] jdbc/snake-kebab-opts))
