(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {org.clojars.askonomm/ruuter {:mvn/version "1.3.2"}
                        ring/ring-codec {:mvn/version "1.2.0"}}})

(require
 '[org.httpkit.server :as srv]
 '[hiccup.core :as hp]
 '[cheshire.core :as json]
 '[ruuter.core :as ruuter]
 '[ring.util.codec :as codec])

(defn home-page [_request]
  (hp/html
   [:html
    [:head
     [:meta {:charset "UTF-8"}]
     [:title "Htmx + Kit"]
     [:script {:src   "https://unpkg.com/htmx.org@1.9.0/dist/htmx.min.js"
               :defer true}]
     [:script {:src   "https://unpkg.com/hyperscript.org@0.9.5"
               :defer true}]
     [:script {:src "https://unpkg.com/htmx.org@1.9.0/dist/ext/ws.js"
               :defer true}]]
    [:body
     [:h1 "Welcome to Htmx"]

     [:div {:hx-ws "connect:/app/chat"}
      [:div#messages]
      [:form {:id    "form"
              :hx-ws "send:submit"}
       [:textarea {:name "text"}]
       [:br]
       [:button {:name "chat_message"
                 :type "submit"} "send message"]]
      [:div#content]]]]))

(def clients (atom #{}))

(def routes
  [{:path "/favicon.ico"
    :method :get
    :response {:status 404}}
   
   {:path "/"
    :method :get
    :response (fn [request]
                {:body (home-page request)
                 :status 200})}
   
   {:path "/app/chat"
    :method :get
    :response (fn ws [request]
                (if-not (:websocket? request)
                  {:status 200
                   :body   [:p "Welcome to the chatroom! JS client connecting... "]}
                  (srv/as-channel request
                                  {:on-open (fn [ch]
                                              (swap! clients conj ch)
                                              (println "channel opened:" ch))
                                   :on-receive (fn [_ch data]
                                                 (let [{:keys [text]} (json/decode data true)]
                                                   (doseq [ch @clients]
                                                     (srv/send! ch
                                                                (hp/html
                                                                 [:div#messages {:hx-swap-oob "beforeend"}
                                                                  [:p "new message: " text]])))))
                                   :on-close (fn [ch status]
                                               (println "channel:" ch "closed:" status))})))}])

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
  (run))
