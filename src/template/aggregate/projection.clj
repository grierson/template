(ns template.aggregate.projection
  (:require
   [template.audit :as audit]
   [template.database.postgres]
   [tick.core :as tick]
   [template.aggregate.projection :as projection]))

(defn aggregate-created-event
  [{:keys [id stream-id timestamp data]
    :or {id (random-uuid)
         timestamp (tick/now)
         stream-id (random-uuid)}}]
  {:id          id
   :type        "aggregate-created"
   :stream-type "aggregate"
   :stream-id   stream-id
   :data        data
   :timestamp   timestamp})

(defmulti apply-event (fn [_ event] (:events/type event)))

(defmethod apply-event :default [state _] state)

(defmethod apply-event "aggregate-created"
  [state event]
  (merge state (:events/data event)))

(defn make-projection [events] (reduce apply-event {} events))

(defn project [database id]
  (make-projection (audit/get-aggregate-events database id)))

(defn create-projection! [database data]
  (let [event (aggregate-created-event {:data data})]
    (audit/create-projection! database project event)))
