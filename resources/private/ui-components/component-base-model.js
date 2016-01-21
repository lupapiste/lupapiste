LUPAPISTE.ComponentBaseModel = function() {
  "use strict";
  var self = this;

  var hubSubscriptions = [];
  var koSubscriptions = [];

  self.sendEvent = function(service, event, data) {
    hub.send(service + "::" + event, data);
  };

  self.addEventListener = function(service, event, fn) {
    hubSubscriptions.push(hub.subscribe(service + "::" + event, fn));
  };

  self.disposedSubscribe = function(observable, fn) {
    koSubscriptions.push(observable.subscribe(fn));
  };

  self.disposedComputed = function(fn) {
    var computed = ko.computed(fn);
    koSubscriptions.push(computed);
    return computed;
  };

  self.dispose = function() {
    while(hubSubscriptions.length !== 0) {
      hub.unsubscribe(hubSubscriptions.pop());
    }
    while(koSubscriptions.length !== 0) {
      (koSubscriptions.pop()).dispose();
    }
  };
};
