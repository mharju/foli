(ns foli.core
  (:require [reagent.core :as re]
            [re-frame.core :refer [debug subscribe register-sub register-handler dispatch]]
            [secretary.core :as secretary :refer-macros  [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [cljs-time.format :as f]
            [foli.handlers])
  (:require-macros [reagent.ratom :refer [reaction]])
  (:import goog.History))

(enable-console-print!)

(register-sub :stop-ids
  (fn [db _]
    (reaction (:stop-ids @db))))

(register-sub :stops
  (fn [db [_ stop-id]]
    (reaction (get-in @db [:stops stop-id]))))

(register-sub :selected-stop
  (fn [db _]
    (reaction (get-in @db [:selected-stop]))))

(register-sub :search-value
  (fn [db _]
    (reaction (get-in @db [:search-value]))))

(register-sub :name-search-results
  (fn [db _]
    (reaction (get-in @db [:name-search-results]))))

(register-sub :selected-stop-schedule
  (fn [db _]
    (let [stop-id (reaction (get @db :selected-stop))
          schedule (reaction (get-in @db [:stops @stop-id]))
          stop-ids (reaction (get @db :stop-ids))]
      (reaction {:schedule @schedule
         :stop-id @stop-id
         :stop-name (get @stop-ids @stop-id)}))))

(defn schedule [data]
  (let [fmt (f/formatter "HH:mm")]
      [:tr
        [:td [:p (:line data)]]
        [:td [:p (:display data)]]
        [:td [:p (f/unparse fmt (:estimated-time data))]]]))

(defn stop-schedule []
  (let [sched (subscribe [:selected-stop-schedule])]
    [:div {:className "stop"
          :style {:height (str (- (.-clientHeight  (.-documentElement js/document)) 150) "px")}}
      (when-not (nil? (get @sched :stop-id))
        [:div
          [:h4 {:style {:textAlign "center"}} (str (get @sched :stop-id) " - " (get @sched :stop-name))]
          [:table.table
            [:thead
              [:tr
                [:th "Linja"]
                [:th "Kohde"]
                [:th "Lähtö"]]]
            [:tbody
              (map-indexed (fn [index s] ^{:key index} [schedule s]) (get @sched :schedule))]]])]))

(declare stop-route)
(defn search-results []
  (let [search-results (subscribe [:name-search-results])]
      (fn []
        [:div#results.row
          [:div.column
            (map-indexed (fn [index {:keys [name id]}] ^{:key index} [:a {:href (stop-route {:stop-id id})} (str id " " name)]) @search-results)]])))

(defn intro []
  (let [message (first (shuffle ["Minknumeroisi bussei täst oikke kulke?" "Meneek toi kauppatoril?" "Täsä sul bussitiatoo" "Niimpal kauhiast aikataului" "No misä se ny oikke viippy?"]))]
  [:div#intro.row
    [:div.column
      [:span.fa.fa-bus]
      [:h1 message]
      [:p "Kirjottele toho vaa jottai järkevännäköst ni mää etti sul koska se oikke lähte."]]]))

(defn application []
  (let [search-value (subscribe [:search-value])
        selected-stop (subscribe [:selected-stop])
        name-search-results (subscribe [:name-search-results])]
    (fn []
      [:div.container
          [:div.row.input-container
            [:div.column
              [:span.fa.fa-search]
              [:input#search.form-control.input-lg {
                                    :type "text"
                                    :placeholder "Syötä pysäkin osoite tai numero"
                                    :value @search-value
                                    :onChange #(dispatch [:search-stop (.-value (.-target %))])}]]]
          (when (and (empty? @name-search-results) (empty? @selected-stop))
            [intro])
          (when-not (empty? @selected-stop) [stop-schedule])
          (when-not (nil? @name-search-results)
               [search-results])])))


(defroute stop-route "/stops/:stop-id" [stop-id]
    (dispatch [:set-selected-stop stop-id]))
(defroute default "*" []
    (dispatch [:main]))

(defonce init
  (do
    (secretary/set-config! :prefix  "#")
    (let [h  (History.)]
        (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch!  (.-token %)))
        (doto h (.setEnabled true)))
    (dispatch [:fetch-stops])
    (re/render-component
      [application]
      (.getElementById js/document "app"))
    (js/setTimeout (fn []
        (.focus (.getElementById js/document "search")))
      500)))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
