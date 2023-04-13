(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {org.clojars.askonomm/ruuter {:mvn/version "1.3.2"}
                        ring/ring-codec {:mvn/version "1.2.0"}}})

(require
 '[org.httpkit.server :as srv]
 '[hiccup.core :as hp]
 '[cheshire.core :as json] 
 '[ruuter.core :as ruuter]
 '[ring.util.codec :as codec])  

(def state (atom {:count 0}))

(defn counter [value]
  [:label
   {:id "counter"}
   "count: " value])

(defn label
  [id]
  [:label {:id id} [:b id ": "] (get @state id)])

(defn input [id]
  [:form {:hx-post (str "/value/" id)
          :hx-target (str "#" id)}
   [:input.edit {:type "text" 
                 :name "player"
                 :value (get @state id)}]])

(defn home-page [_request]
  (hp/html
   [:html
    [:head
     [:meta {:charset "UTF-8"}]
     [:title "Htmx + Kit"]
     [:script {:src   "https://unpkg.com/htmx.org@1.9.0/dist/htmx.min.js"
               :defer true}]
     [:script {:src   "https://unpkg.com/hyperscript.org@0.9.5"
               :defer true}]]
    [:body
     [:h1 "Welcome to Htmx"]
     (counter (:count @state))
     [:br]
     (label "foo")
     [:br]
     (input "foo")
     [:p#foo]
     [:button {:hx-post "/increment" :hx-target "#counter"} "Increment"]]]))

(def routes
  [{:path "/"
    :method :get
    :response (fn [request]
                {:body (home-page request)
                 :status 200})}
   {:path "/favicon.ico"
    :method :get
    :response {:status 404}}
   {:path "/increment"
    :method :post
    :response (fn [_request]
                {:body (hp/html (counter (:count (swap! state update :count inc))))
                 :status 200})}
   {:path "/reset"
    :method :post
    :response (fn [_request]
                {:body (hp/html (counter (:count (swap! state assoc :count 0))))
                 :status 200})}
   {:path "/value/:id"
    :method :post
    :response (fn [{{:keys [id]} :params
                    {:strs [player]} :body}]
                (swap! state assoc id player)
                {:body   (hp/html (label id))
                 :status 200})}])

(defonce server (atom nil))

(defn run []
  (let [port 3000
        url  (str "http://localhost:" port "/")
        router #(ruuter/route routes %)]
    (when-let [server @server]
      (server))
    (reset! server 
            (srv/run-server
             (fn [request]
               (router (update request :body #(some-> % slurp codec/form-decode))))
             {:port port}))
    (println "serving" url)))


@server
(comment
  (run)
  )
