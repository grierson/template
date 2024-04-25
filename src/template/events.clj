(ns template.events
  (:require
   [jsonista.core :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as rs]
   [next.jdbc.date-time])
  (:import [java.sql PreparedStatement]
           [org.postgresql.util PGobject]))

(def mapper (json/object-mapper {:decode-key-fn keyword}))
(def ->json json/write-value-as-string)
(def <-json #(json/read-value % mapper))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (<-json value) {:pgtype type}))
      value)))

(set! *warn-on-reflection* true)

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))

(defn make-store [db-spec]
  (let [datasource #p (jdbc/get-datasource #p db-spec)]
    (println "I run")
    (jdbc/execute!
     datasource
     ["create table if not exists events (
      id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      position SERIAL,
      type varchar NOT NULL,
      stream_id UUID NOT NULL,
      data jsonb NOT NULL,
      timestamp TIMESTAMP default now() NOT NULL)"])
    (println "I dont run")
    (jdbc/execute!
     datasource
     ["create table if not exists projections (
      id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      type varchar NOT NULL,
      data jsonb NOT NULL)"])
    (println "do I run fater")
    datasource))

(comment
  (make-store {}))

(defn kill-store [store]
  (jdbc/execute! store ["DROP TABLE events"])
  (jdbc/execute! store ["DROP TABLE projections"]))

(defn get-events
  ([store]
   (get-events store 1 10))
  ([store start end]
   (println "I dont")
   (sql/query
    store
    ["SELECT * FROM events WHERE position BETWEEN ? AND ?" start end]
    jdbc/snake-kebab-opts)))

(defn get-aggregate-events [store id]
  (sql/query store ["SELECT * FROM events WHERE stream_id = ?" id] jdbc/snake-kebab-opts))

(defn raise [store {:keys [id] :as event}]
  (sql/insert! store :events event jdbc/snake-kebab-opts)
  (sql/get-by-id store :events id))
