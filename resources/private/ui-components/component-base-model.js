LUPAPISTE.ComponentBaseModel = function() {
  "use strict";
  var self = this;

  var hubSubscriptions = [];
  var koSubscriptions = [];
  // Subscriptions that can be turned on by applySubscriptions and turned off by disposeAppliedSubscriptions
  var applyableSubscriptions = [];

  self.sendEvent = function(service, event, data) {
    hub.send(service + "::" + event, data);
  };

  self.addHubListener = function( event, fn ) {
    hubSubscriptions.push(hub.subscribe( event, fn ));
  };

  self.addEventListener = function(service, event, fn) {
    if (_.isString(event)) {
      self.addHubListener(service + "::" + event, fn);
    } else {
      self.addHubListener(_.assign(event, {eventType: service + "::" + event.eventType}), fn);
    }
  };

  self.disposedSubscribe = function(observable, fn) {
    var subscription = observable.subscribe(fn);
    koSubscriptions.push(subscription);
    return subscription;
  };

  self.unsubscribe = function(subscription) {
    subscription.dispose();
    koSubscriptions = _.pull(koSubscriptions, subscription);
  };

  self.disposedComputed = function(fn) {
    var computed = ko.computed(fn);
    koSubscriptions.push(computed);
    return computed;
  };

  self.disposedPureComputed = function(fn) {
    var computed = ko.pureComputed(fn);
    koSubscriptions.push(computed);
    return computed;
  };

  // Register updating subscription so that they can be turned off when needed.
  self.registerApplyableSubscription = function(obs, f) {
    applyableSubscriptions.push({observable: obs, callback: f});
  };

  // Turns on registered applyable subscriptions.
  self.applySubscriptions = function() {
    _.forEach(applyableSubscriptions, function(subscription) {
      _.set(subscription, "id", self.disposedSubscribe(subscription.observable, subscription.callback));
    });
  };

  // Turns off registered applyable subscriptions.
  self.disposeAppliedSubscriptions = function() {
    _.forEach(applyableSubscriptions, function(subscription) {
      self.unsubscribe(subscription.id);
    });
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
