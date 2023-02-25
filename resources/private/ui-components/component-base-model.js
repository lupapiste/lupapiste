LUPAPISTE.ComponentBaseModel = function() {
  "use strict";
  var self = this;

  var hubSubscriptions = [];
  var koSubscriptions = [];
  // Subscriptions that can be turned on by applySubscriptions and turned off by disposeAppliedSubscriptions
  var applyableSubscriptions = [];
  // Dispose methods of the queue items are called on base dispose.
  var disposeQueue = [];

  self.sendEvent = function(service, event, data) {
    hub.send(service + "::" + event, data);
  };

  self.addHubListener = function( filter, fn ) {
    hubSubscriptions.push(hub.subscribe( filter, fn ));
  };

  self.addEventListener = function(service, event, fn) {
    if (_.isString(event)) {
      self.addHubListener(service + "::" + event, fn);
    } else {
      self.addHubListener(_.assign(event, {eventType: service + "::" + event.eventType}), fn);
    }
  };

  // As of KO 3.0 listening to changes in observableArray lengths requires two additional parameters fn2 and obs.
  // They are not usually required for other uses.
  self.disposedSubscribe = function(observable, fn, fn2, obs) {
    var subscription = observable.subscribe(fn, fn2, obs);
    koSubscriptions.push(subscription);
    return subscription;
  };

  self.unsubscribe = function(subscription) {
    subscription.dispose();
    koSubscriptions = _.pull(koSubscriptions, subscription);
  };

  self.subscribeChanged = function(observable, fn) {
    var prevValue;
    var multiSubscription = { // Wraps both (before and after) subscriptions in one disposable object
      subscriptions: [ observable.subscribe(function(value) { prevValue = _.clone(value); }, null, "beforeChange"),
                       observable.subscribe(function(value) { fn(value, prevValue); }) ],
      dispose: function() { _.invokeMap(multiSubscription.subscriptions, "dispose"); }
    };
    koSubscriptions.push(multiSubscription);
    return multiSubscription;
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

  self.addToDisposeQueue = function( obj ) {
    disposeQueue.push( obj );
  };

  self.dispose = function() {
    while(hubSubscriptions.length !== 0) {
      hub.unsubscribe(hubSubscriptions.pop());
    }
    while(koSubscriptions.length !== 0) {
      (koSubscriptions.pop()).dispose();
    }
    while(_.size(disposeQueue) !== 0 ) {
      (disposeQueue.pop()).dispose();
    }
  };
};
