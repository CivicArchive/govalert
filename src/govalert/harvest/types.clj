(ns govalert.harvest.types)

(defn agendas-dispatcher [obj]
  (cond
   (string? obj) :url
   (map? obj) (:mode obj)))

(defmulti agendas agendas-dispatcher)

(defmulti attachments (comp :mode :docs))

;;

(defrecord Agenda [title type uri date summary links groupurl groupname])

(defn make-agenda [& {title :title type :type uri :uri date :date summary :summary links 
                      :links groupurl :groupurl groupname :groupname}]
  (Agenda. title type uri date summary links groupurl groupname))

;;

(defrecord Attachement [uri data label groupurl groupname])

(defn make-attachment [& {uri :uri data :data label :label groupurl :groupurl groupname :groupname}]
  (Attachement. uri data label groupurl groupname))

;;

