(ns sci.impl.types)

(defprotocol IBox
  (setVal [_this _v])
  (getVal [_this]))
