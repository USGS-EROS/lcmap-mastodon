(ns lcmap.mastodon.core
  (:require [clojure.data :as data]
            [clojure.string :as string]
            [clojure.set :as set]
            [lcmap.mastodon.http :as http]))

(enable-console-print!)
(println "Hello from LCMAP Mastodon!")

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(defn hv-map
  "Helper function.
   Return map for :h and :v given
   a tileid of hhhvvv e.g 052013

   ^String :id: 6 character string representing tile id
   ^Expression :regx: Optional expression to parse tile id

   Returns map with keys :h & :v"
  [id & [regx]]
  (hash-map :h (first (re-seq (or regx #"[0-9]{3}") id)) 
            :v (last  (re-seq (or regx #"[0-9]{3}") id)))
)

(defn tile-id-rest
  "Helper function.
   Takes a 6 digit tileid, and returns a
   string splitting the H and V values so
   they may be used in generating a URL
   for an inventory request.
   ^String :tile-id: 6 character string representing a tileid

   Returns a string with a forward slash separating H and V values
   e.g. /hhh/vvv/
  "
  [tile-id]
  (string/join "/" [(:h (hv-map tile-id)) (:v (hv-map tile-id)) ""])
)

(defn get-map-val
  "Helper function.
   Return particular value for a map,
   for the conditional key and value

   ^Keyword :desired-key:     Desired key
   ^Keyword :conditional-key: Conditional key
   ^String  :conditional-val: Conditional key val
   ^Map     :map-obj: Map object
  "
  [map-obj desired-key & [conditional-key conditional-val]]
  (let [ck (or conditional-key :nil)]
    (if (= conditional-val (ck map-obj))
      (desired-key map-obj)
    )
  )
)

(defn collect-map-values
  "Data organization function.
   Takes a list of maps and returns
   a collection of values for specified
   key, if another specified key/value
   pair exists in the map
   
   ^List :inmaps: List of maps representing dir contents as json
   ^Keyword :dk: Desired key to collect from ard-response
   ^Keyword :ck: Conditional key to check value of
   ^String  :cv: Conditional value to check value of

   Returns list of dk values from ard-response"
  [map-list desired-key & [conditional-key conditional-value]]
  (let [rcount (count map-list)]
    (set (map get-map-val
             map-list
             (repeat rcount desired-key) 
             (repeat rcount conditional-key) 
             (repeat rcount conditional-value)))
    )
)

(defn ard-url-format
  "URL generation function for requests to an ARD file access server

   ^String :host:    Host name
   ^String :tile-id: Tile ID
  "
  [host tile-id]
  (string/join "/" [host (tile-id-rest tile-id)])
)

(defn idw-url-format
  "URL generation function for requests to an LCMAP-Chipmunk instance

   ^String :host:    lcmap-chipmunk instance host
   ^String :tile-id: Tile ID
  "
  ;; $ curl -XGET http://localhost:5656/inventory?tile=027009
  [host tile-id]
  (str host "/inventory?tile=" tile-id)
)

(defn inventory-diff
  "Diff function, comparing what source files the ARD source has available, 
   and what sources have been ingested into the data warehouse for a specific tile

   ^String :ardh: ARD Host
   ^String :idwh: IDW Host
   ^String :hv:   Tile ID
   ^String :reg:  Region

   Returns vector (things only in ARD, things only in IDW)
  "
  [ard-host idw-host tile-id region & [req-fn]]
  (let [requestor (or req-fn http/get-request)
        ard-url   (ard-url-format ard-host tile-id)
        ard-list  (collect-map-values (requestor ard-url) :name :type "file")
        idw-url   (idw-url-format idw-host tile-id)        
        idw-list  (collect-map-values (:result (requestor idw-url)) :source)
]
        
        [(set/difference ard-list idw-list) (set/difference idw-list ard-list)]
  )
)
