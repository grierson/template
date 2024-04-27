(ns template.core
  (:require [template.helper :as helper]
            [donut.system :as ds])
  (:gen-class))

(defn -main
  []
  (println "running...")
  (ds/start ::helper/test))
