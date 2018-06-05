(ns mastodon.clj.server
   (:require [clojure.string           :as string]
             [clojure.tools.logging    :as log]
             [clojure.set              :as set]
             [compojure.core           :as compojure]
             [compojure.route          :as route]
             [mastodon.cljc.data       :as data]
             [mastodon.cljc.util       :as util]
             [mastodon.clj.config      :refer [config]]
             [mastodon.clj.file        :as file]
             [mastodon.clj.persistance :as persist]
             [mastodon.clj.validation  :as validation]
             [mastodon.clj.warehouse   :as warehouse]
             [ring.middleware.json     :as ring-json]
             [ring.middleware.keyword-params :as ring-keyword-params]
             [ring.middleware.defaults :as ring-defaults]
             [org.httpkit.client       :as http]
             [org.httpkit.server       :as http-server]))


(defn bulk-ingest
  "Generate ingest requests for list of posted ARD."
  [{:keys [:body] :as req}]
  (try
    (let [tifs    (string/split (:urls body) #",")
          results (doall (pmap #(persist/ingest % (:chipmunk_host config)) tifs))]
      {:status 200 :body results})
    (catch Exception ex
      (log/errorf "exception in server/bulk-ingest. request: %s message: %s" req (util/exception-cause-trace ex "mastodon"))
      {:status 200 :body {:error (format "exception with bulk-ingest %s" (util/exception-cause-trace ex "mastodon"))}})))

(defn http-deps-check
  "Return error response if external http dependencies are not reachable"
  []
  (let [ard-accessible  (validation/http-accessible? (:ard_host config) "ARD_HOST")
        iwds-accessible (validation/http-accessible? (:chipmunk_host config) "CHIPMUNK_HOST")
        nemo-accessible (validation/http-accessible? (:nemo_host config) "NEMO_HOST")
        ard-message  (str "ARD Host: " (:ard_host config) " is not reachable. ") 
        iwds-message (str "CHIPMUNK Host: " (:chipmunk_host config) " is not reachable")
        nemo-message (str "NEMO Host: " (:nemo_host config) " is not reachable")]
    (if (= #{true} (set [ard-accessible iwds-accessible]))
      (do {:error nil})
      (do (cond
           (= true ard-accessible)  {:error iwds-message}
           (= true iwds-accessible) {:error ard-message}
           :else {:error (str ard-message iwds-message)})))))

(defn available-ard
  "Return a vector of available ARD for the given tile id"
  [tileid]
  (let [hvmap    (util/hv-map tileid)
        filepath (str (:ard_path config) (:h hvmap) "/" (:v hvmap) "/*")]
    (-> filepath 
        (file/get-filenames "tar")
        (#(map data/ard-manifest %))
        (flatten))))

(defn filtered-ard
  "Return vector of available ARD for a given tile id, between the provided years"
  [tileid from to]
  (let [tifs  (available-ard tileid)
        froms (filter (fn [i] (>= (-> i (util/tif-only) (data/year-acquired) (read-string)) from)) tifs)
        tos   (filter (fn [i] (<= (-> i (util/tif-only) (data/year-acquired) (read-string)) to)) tifs)]
    (vec (set/intersection (set froms) (set tos)))))

(defn data-report
  "Return hash-map of missing ARD and an ingested count"
  [available-tifs ingested-tifs]
  (try
    (let [available-only (set/difference (set available-tifs) (set ingested-tifs))
          ingested-only  (set/difference (set ingested-tifs) (set available-tifs))]
      {:missing (vec available-only) :ingested (count ingested-tifs)})))

(defn ard-tifs
  "Return vector of ARD tif names for a given tileid"
  [tileid {:keys [params] :as req}]
  (let [from (or (util/try-string (:from params)) 0)
        to   (or (util/try-string (:to params)) 3000)]
    (filtered-ard tileid from to)))

(defn aux-tifs
  "Return vector of Auxiliary tif names for a given tileid"
  [tileid]
  (let [aux_resp (http/get (:aux_host config))
        aux_file (util/get-aux-name (:body @aux_resp) tileid)]
    (doall (data/aux-manifest aux_file))))

(defn get-status
  "Return ingest status for a given tile id"
  [tileid request]
  (try
    (let [available-tifs (if (= (:server_type config) "ard") (ard-tifs tileid request) (aux-tifs tileid))
          ingested-tifs (warehouse/ingested-tifs tileid)
          deps (http-deps-check)]
      (if (nil? (:error deps))
        {:status 200 :body (data-report available-tifs ingested-tifs)}
        {:status 200 :body {:error (:error deps)}}))
    (catch Exception ex
      (log/errorf "Error determining tile: %s tile data status. exception: %s" tileid (util/exception-cause-trace ex "mastodon"))
      {:status 200 :body {:error (format "Error determining tile: %s tile data status. exception: %s" tileid (util/exception-cause-trace ex "mastodon"))}})))

(defn get-config
  "Return config/config"
  [request]
  {:status 200 :body config})

(defn get-base 
  "Hello Mastodon"
  [request]
  {:status 200 :body ["Would you like some data with that?"]})

(compojure/defroutes routes
  (compojure/context "/" request
    (route/resources "/")
    (compojure/GET   "/" [] (get-base request))
    (compojure/GET   "/inventory/:tileid{[0-9]{6}}" [tileid] (get-status tileid request))
    (compojure/GET   "/config" [] (get-config request))
    (compojure/POST  "/bulk-ingest" [] (bulk-ingest request))))

(defn response-handler
  [routes]
  (-> routes
      (ring-json/wrap-json-body {:keywords? true})
      (ring-json/wrap-json-response)
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (ring-keyword-params/wrap-keyword-params)))

(def app (response-handler routes))

(defn run-server
  [config]
  (log/infof "chipmunk-host: %s" (:chipmunk_host config))
  (log/infof "aux-host: %s" (:aux_host config))
  (log/infof "ard-host: %s" (:ard_host config))
  (log/infof "ard-path: %s" (:ard_path config))
  (log/infof "nemo-host: %s" (:nemo_host config))
  (log/infof "server-type: %s" (:server_type config))
  (http-server/run-server app {:port 9876}))

