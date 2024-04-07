(ns template.core
  (:require [template.system :as system]
            [donut.system :as ds])
  (:gen-class))

(defn -main
  []
  (println "running...")
  (ds/start ::system/production))
