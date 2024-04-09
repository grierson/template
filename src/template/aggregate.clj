(ns template.aggregate
  (:require [taoensso.carmine :as car :refer [wcar]]
            [tick.core :as tick]
            [jsonista.core :as json]
            [template.event-store :as events])
  (:import [com.github.fppt.jedismock RedisServer]))

(defn make-server []
  (let [server (RedisServer/newRedisServer)
        _ (.start server)]
    server))

(defn make-store [server]
  (let [redis-host (.getHost server)
        redis-port (.getBindPort server)
        redis-url (str "redis://" redis-host ":" redis-port)]
    {:pool (car/connection-pool {})
     :spec {:uri redis-url}}))

(defn get-projection [store id] (wcar store (car/get id)))

(defn created-event
  [{:keys [id stream-id created-at data]
    :or {id (random-uuid)
         created-at (tick/now)
         stream-id (random-uuid)}}]
  {:id          id
   :type        "aggregate-created"
   :stream-id   stream-id
   :stream-type "aggregate"
   :data        (json/write-value-as-string data)
   :created-at  created-at})

(defmulti apply-event (fn [_ event] (:events/type event)))

(defmethod apply-event :default [state _] state)

(defmethod apply-event "aggregate-created"
  [state event]
  (merge state (:events/data event)))

(defn project [events] (reduce apply-event {} events))

(defn project-aggregate [store id]
  (project (events/get-aggregate-events store id)))

(defn create-aggregate [store data]
  (let [id (random-uuid)
        event (created-event {:stream-id id :data data})]
    (events/raise store event)
    (merge {:id id} (project-aggregate store id))))
