(ns template.resources.urls
  (:require
   [reitit.core :as reitit]
   [reitit.impl :as impl]))

(defn base-url
  [{:keys [scheme headers]}]
  (let [host (get headers "host")]
    (str (name scheme) "://" host)))

(defn url-for
  ([router request name] (url-for router request name {}))
  ([router request name path-params]
   (let [match (reitit/match-by-name router name path-params)
         template (:template match)
         route (impl/parse template (reitit/options router))
         base (base-url request)
         path (impl/path-for route path-params)]
     (str base path))))
