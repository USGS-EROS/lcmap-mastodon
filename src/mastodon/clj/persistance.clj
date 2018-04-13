(ns mastodon.clj.persistance
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.string :as string]
            [mastodon.cljc.data :as data]
            [mastodon.cljc.util :as util]
            [clojure.tools.logging :as log]))

(defn ingest 
  "Post ingest requests to IWDS resources"
  [data iwds_resource]
  (log/infof "persist/ingest: data: %s  iwds_resource: %s" data iwds_resource)
  (let [file_name (last (string/split data #"/"))]
    (try 
      (let [iwds_path (str iwds_resource "/inventory")
            post_opts {:body (json/encode {"url" data}) :timeout 120000
                       :headers {"Content-Type" "application/json" "Accept" "application/json"}}
            post_resp (http/post iwds_path post_opts)
            response {file_name (:status @post_resp)}]
        (log/infof "ingest attempt: %s" response)
        response)
      (catch Exception ex 
        (log/errorf "caught exception during ingest. ard: %s  iwds: %s  exception: %" 
                    data iwds_resource (.getMessage ex))
        {file_name 500 :error (.getMessage ex)}))))

(defn ard-resource-path
  [name ing_resource]
  (let [tar     (data/ard-tar-name name)
        tarpath (data/tar-path tar)]
    (format "%s/%s/%s/%s" ing_resource tarpath tar name)))

(defn aux-resource-path
  [name ing_resource]
  (let [tar (data/aux-tar-name name)]
    (format "%s/%s/%s" ing_resource tar name)))

(defn status-check
  "Return hash-map of ingest resource and IWDS ingest query response"
  [data iwds_host data_resource]
  (let [iwdsresp  (http/get (str iwds_host "/inventory?only=source&source=" data))
        body      (:body @iwdsresp)]

    (if (re-find #"AUX_" data) 
      (do
        (hash-map (aux-resource-path data data_resource) body))
      (do
        (hash-map (ard-resource-path data data_resource) body)))))

