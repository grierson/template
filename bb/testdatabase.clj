#!/usr/bin/env bb

(ns testdatabase
  (:require [contajners.core :as c]))

(def image-tag "postgres:latest")

(def image-conn
  (c/client {:engine   :docker
             :category :images
             :version  "v1.41"
             :conn     {:uri "unix:///var/run/docker.sock"}}))

(def container-conn
  (c/client {:engine :docker
             :category :containers
             :version  "v1.41"
             :conn     {:uri "unix:///var/run/docker.sock"}}))

(defn pull-image []
  (c/invoke
   image-conn
   {:op :ImageCreate
    :params {:fromImage image-tag}}))

(defn start-container []
  (let [{:keys [Id]} (c/invoke container-conn
                               {:op     :ContainerCreate
                                :data   {:Image image-tag
                                         :ExposedPorts {"5432" {}
                                                        "5432/0" {}
                                                        "5432/tcp" {}}
                                         :Env ["POSTGRES_PASSWORD=postgres"]}})]
    (c/invoke container-conn {:op :ContainerStart
                              :params {:id Id}})))

(comment
  (start-container))

