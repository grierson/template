(ns template.event-store
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]))

(defn make-store []
  (let [db {:dbtype "h2" :dbname "template"}
        ds (jdbc/get-datasource db)]
    (jdbc/execute!
     ds
     ["create table if not exists events (
      id UUID NOT NULL DEFAULT random_uuid() PRIMARY KEY,
      position int auto_increment,
      type varchar(255),
      streamId UUID,
      streamType varchar(255),
      data varchar (255),
      createdAt Datetime,
      timestamp datetime default CURRENT_TIMESTAMP)"])
    ds))

(defn kill-store [store]
  (jdbc/execute! store ["drop table events"]))

(defn get-events
  ([store]
   (get-events store 1 10))
  ([store start end]
   (sql/query store ["SELECT * FROM events WHERE position BETWEEN ? AND ?" start end])))

(defn get-aggregate-events [store id]
  (sql/query store ["SELECT * FROM events WHERE streamId = ?" id]))

(defn raise
  [store event]
  (sql/insert!
   store
   :events
   (cske/transform-keys csk/->camelCaseString event)))
