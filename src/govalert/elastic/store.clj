(ns govalert.elastic.store
  ; Build elastic search index for agendas and associated support documents."
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [govalert.elastic.db :as db])
  (:use [clj-time.coerce :only (to-long)]))

(defn upsert [index mapping-type id doc]
  (if-not (esd/present? index mapping-type id)
     (esd/put index mapping-type id doc)))

(defn index-agenda [& {id :id title :title url :url date :date summary :summary content :content} ]
  (assert (string? id))
  (upsert db/current-index "agenda" id 
            {:title title :url url :date date :summary summary :content content}))

(defn index-attachment [& {id :id label :label title :title url :url content :content groupurl :groupurl groupname :groupname agenda :agenda timestamp :timestamp  :as args}]
  (assert (string? id))
  (upsert db/current-index "attachment" (str agenda "_" id)
    (merge {:label label :title title :url url :content content :groupurl groupurl :groupname groupname :agenda agenda} 
           (if timestamp {:timestamp (to-long timestamp)} ))))

(defn add-subscription [& {email :email query :query updated :updated :as args}]
  (assert (every? string? [email query]))
  (esd/put db/current-index "subscription" (str email ":" query) {:email email :query query :updated (if updated (to-long updated))}))

(defn add-harvester [name & {title :title govbody :govbody agendas :agendas docs :docs :as spec}]
  (if name
    (esd/put db/current-index "harvester" name spec)
    (esd/create db/current-index "harvester" spec)))









