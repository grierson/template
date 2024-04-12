(ns template.events
  (:require
   [jsonista.core :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(defn make-store []
  (let [db {:dbtype "h2" :dbname "template"}
        store (jdbc/get-datasource db)]
    (jdbc/execute!
     store
     ["create table if not exists events (
      id UUID NOT NULL DEFAULT random_uuid() PRIMARY KEY,
      position int auto_increment,
      type varchar(255),
      stream_id UUID,
      stream_type varchar(255),
      data varchar(MAX),
      created_at datetime default CURRENT_TIMESTAMP)"])
    (jdbc/execute!
     store
     ["create table if not exists projections (
      id UUID NOT NULL DEFAULT random_uuid() PRIMARY KEY,
      type varchar(255),
      data varchar(MAX))"])
    store))

(defn kill-store [store]
  (jdbc/execute! store ["DROP TABLE events"])
  (jdbc/execute! store ["DROP TABLE projections"]))

(defn get-events
  ([store]
   (get-events store 1 10))
  ([store start end]
   (map
    (fn [e] (update e :events/data (fn [x] (json/read-value x json/keyword-keys-object-mapper))))
    (sql/query
     store
     ["SELECT * FROM events WHERE position BETWEEN ? AND ?" start end]
     jdbc/snake-kebab-opts))))

(defn get-aggregate-events [store id]
  (map
   (fn [e] (update e :events/data (fn [x] (json/read-value x json/keyword-keys-object-mapper))))
   (sql/query store ["SELECT * FROM events WHERE stream_id = ?" id] jdbc/snake-kebab-opts)))

(defn raise [store {:keys [id] :as event}]
  (sql/insert! store :events event jdbc/snake-kebab-opts)
  (sql/get-by-id store :events id))
