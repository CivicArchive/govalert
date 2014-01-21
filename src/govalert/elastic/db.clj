(ns govalert.elastic.db
  (:require 
     [clojurewerkz.elastisch.rest :as esr]
     [clojurewerkz.elastisch.rest.index :as esi]))

(def ^:dynamic current-index "gov")

(defmacro with-index [index & body]
  `(binding [current-index (or ~index current-index)]
     ~@body))

(defn taking-index 
  "Turns a function into one that takes an ElasticSearch index as first argument"
  [f]
  (fn [index & args]
    (with-index index
       (apply f args))))

(defn init 
  "Open a connection to ES as side effect"
  [endpoint]
  (esr/connect! endpoint))

(defn delete! 
  "Permanently delete the index from the database"
  [index] (esi/delete index))

(defn indices 
  "List the canonical name of each index in the database"
  [] (map name (keys (esi/get-aliases nil))))

(defn index-exists?
  "True of there is a matching index"
  [name] (esi/exists? name))

(defn refresh 
  ([index] (esi/refresh index))
  ([] (refresh current-index)))


  
