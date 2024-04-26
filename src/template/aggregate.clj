(ns template.aggregate
  (:require [template.events :as events]
            [template.projection :as projection]
            [tick.core :as tick]))

(defn aggregate-created-event
  [{:keys [id stream-id timestamp data]
    :or {id (random-uuid)
         timestamp (tick/now)
         stream-id (random-uuid)}}]
  {:id          id
   :type        "aggregate-created"
   :stream-id   stream-id
   :data        data
   :timestamp  timestamp})

(defmulti apply-event (fn [_ event] (:events/type event)))

(defmethod apply-event :default [state _] state)

(defmethod apply-event "aggregate-created"
  [state event]
  (merge state (:events/data event)))

(defn project [events] (reduce apply-event {} events))

(defn project-aggregate [database id]
  (project (events/get-aggregate-events database id)))

(defn create-aggregate [database data]
  (let [aggregate-id (random-uuid)
        event (aggregate-created-event {:stream-id aggregate-id
                                        :data (merge {:id aggregate-id} data)})
        _ (events/raise database event)
        aggregate (project-aggregate database aggregate-id)]
    (projection/upsert database {:id aggregate-id
                                 :type "aggregate"
                                 :data aggregate})
    aggregate))
