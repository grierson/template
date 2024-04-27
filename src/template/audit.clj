(ns template.audit
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.date-time]
            [template.database.postgres]))

(defn make-tables [database]
  (jdbc/execute!
   database
   ["create table if not exists events (
      id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      position SERIAL,
      type varchar NOT NULL,
      stream_id UUID NOT NULL,
      stream_type varchar NOT NULL,
      data jsonb NOT NULL,
      timestamp TIMESTAMP default now() NOT NULL)"])
  (jdbc/execute!
   database
   ["create table if not exists projections (
      id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      type varchar NOT NULL,
      data jsonb NOT NULL)"])
  database)

(defn get-events
  ([database]
   (get-events database 1 10))
  ([database start end]
   (sql/query
    database
    ["SELECT * FROM events WHERE position BETWEEN ? AND ?" start end]
    jdbc/snake-kebab-opts)))

(defn get-aggregate-events [database id]
  (sql/query database ["SELECT * FROM events WHERE stream_id = ?" id] jdbc/snake-kebab-opts))

(defn raise-event [database {:keys [id] :as event}]
  (sql/insert! database :events event jdbc/snake-kebab-opts)
  (sql/get-by-id database :events id))

(defn upsert-projection [database projection]
  (sql/insert! database :projections projection jdbc/snake-kebab-opts))

(defn get-projections
  ([database] (get-projections database 10))
  ([database limit]
   (sql/query database ["SELECT * FROM projections LIMIT ?" limit] jdbc/snake-kebab-opts)))

(defn get-projection [database id]
  (sql/get-by-id database :projections id jdbc/snake-kebab-opts))

(defn create-projection! [database project-fn {:keys [stream-id stream-type] :as event}]
  (let [_ (raise-event database event)
        projection (project-fn database stream-id)
        projection-record {:id stream-id
                           :type stream-type
                           :data projection}]
    (upsert-projection database projection-record)
    projection-record))
