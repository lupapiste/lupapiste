LUPAPISTE.ComponentBaseModel = function() {
  "use strict";
  var self = this;

  var subscriptions = [];

  self.sendEvent = function(service, event, data) {
    hub.send(service + "::" + event, data);
  };

  self.addEventListener = function(service, event, fn) {
    subscriptions.push(hub.subscribe(service + "::" + event, fn));
  };

  self.dispose = function() {
    while(subscriptions.length !== 0) {
      hub.unsubscribe(subscriptions.pop());
    }
  };
};
