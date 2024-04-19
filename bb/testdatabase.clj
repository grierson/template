#!/usr/bin/env bb

(ns testdatabase
  (:require [contajners.core :as c]))

(def images-docker
  (c/client {:engine   :docker
             :category :images
             :version  "v1.41"
             :conn     {:uri "unix:///var/run/docker.sock"}}))

(c/invoke images-docker {:op :ImageList})
