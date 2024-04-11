(ns template.aggregate
  (:require
   [jsonista.core :as json]
   [template.events :as events]
   [template.projection :as projection]
   [tick.core :as tick]))

(defn aggregate-created-event
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
  (let [aggregate-id (random-uuid)
        event (aggregate-created-event {:stream-id aggregate-id
                                        :data (merge {:id aggregate-id} data)})
        _ (events/raise store event)
        projected-aggregate (project-aggregate store aggregate-id)]
    (projection/upsert store {:id aggregate-id
                              :type "aggregate"
                              :data (json/write-value-as-string projected-aggregate)})
    projected-aggregate))
