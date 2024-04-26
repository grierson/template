(ns template.events
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.date-time]
            [template.database.postgres]))

(defn make-table [database]
  (jdbc/execute!
   database
   ["create table if not exists events (
      id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      position SERIAL,
      type varchar NOT NULL,
      stream_id UUID NOT NULL,
      data jsonb NOT NULL,
      timestamp TIMESTAMP default now() NOT NULL)"])
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

(defn raise [database {:keys [id] :as event}]
  (sql/insert! database :events event jdbc/snake-kebab-opts)
  (sql/get-by-id database :events id))
