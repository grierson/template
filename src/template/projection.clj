(ns template.projection
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [template.database.postgres]))

(defn make-table [database]
  (jdbc/execute!
   database
   ["create table if not exists projections (
      id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      type varchar NOT NULL,
      data jsonb NOT NULL)"])
  database)

(defn upsert [database projection]
  (sql/insert! database :projections projection jdbc/snake-kebab-opts))

(defn get-projections
  ([database] (get-projections database 10))
  ([database limit]
   (sql/query database ["SELECT * FROM projections LIMIT ?" limit] jdbc/snake-kebab-opts)))

(defn projection [database id]
  (sql/get-by-id database :projections id jdbc/snake-kebab-opts))
