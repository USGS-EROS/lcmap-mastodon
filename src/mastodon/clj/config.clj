(ns mastodon.clj.config
  [:require [environ.core :as environ]])

(defn nemo-host
  []
  (:nemo-host environ/env))

(defn nemo-inventory
  []
  (or (:nemo-inventory environ/env) "/inventory_by_tile?tile="))

(defn try-read
  [val]
  (try (read-string val)
       (catch Exception ex
         nil)))

(def config
  {:nemo_host      (nemo-host)
   :nemo_inventory (str (nemo-host) (nemo-inventory))
   :chipmunk_host  (:chipmunk-host environ/env)
   :chipmunk_timeout (or (try-read (:chipmunk-timeout environ/env)) 120000)
   :aux_host       (:aux-host      environ/env)
   :ard_host       (:ard-host      environ/env)
   :ard_path       (:ard-path      environ/env)
   :from_date      (:from-date     environ/env)
   :to_date        (:to-date       environ/env)
   :server_type    (:server-type   environ/env)
   :inventory-timeout (or (try-read (:inventory-timeout environ/env)) 120000)
   :ingest-timeout    (or (try-read (:ingest-timeout    environ/env)) 120000)
   :partition_level (or (try-read (:partition-level environ/env)) 10)})

