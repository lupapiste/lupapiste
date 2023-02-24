var hub = (function() {
  "use strict";

  var nextId = 0;
  var subscriptions = { };
  var debugEvents = false;

  function setDebug(b) {
    debug("Hub debug setting is " + b);
    debugEvents = b;
  }

  function Subscription(listener, filter, oneshot) {
    this.listener = listener;
    this.filter = filter;
    this.oneshot = oneshot;
    this.deliver = function(e) {
      for (var k in this.filter) {
        if (this.filter[k] !== e[k]) {
          return false;
        }
      }
      this.listener(e);
      return true;
    };
  }

  function subscribe(filter, listener, oneshot) {
    if (!(listener && listener.call)) { throw "Parameter 'listener' must be a function"; }
    var id = nextId;
    nextId += 1;
    if (_.isString(filter)) { filter = { eventType: filter }; }
    subscriptions[id] = new Subscription(listener, filter, oneshot);
    return id;
  }

  function unsubscribe(id) {
    delete subscriptions[id];
  }

  function makeEvent(eventType, data) {
    var e = {eventType: eventType};
    for (var key in data) {
      if (data.hasOwnProperty(key)) {
        e[key] = data[key];
      }
    }
    return e;
  }

  function send(eventType, data) {
    var count = 0;
    var event = makeEvent(eventType, data || {});

    if (debugEvents) {
      debug(event);
    }

    _.each(subscriptions, function(s, id) {
      if (s !== undefined && s.deliver(event)) {
        if (s.oneshot) {
          unsubscribe(id);
        }
        count++;
      }
    });
    return count;
  }

  // Message params are in the hash.
  // ...#param1=value1&param2=value2...
  function sendHash( eventType, hash ) {
    var params = _.reduce( _.split( _.get( _.split( hash , "#" ), 1, ""), "&"),
                           function( acc, p ) {
                             var xs = _.map( _.split( p, "="), decodeURIComponent );
                             return _.set( acc, _.first( xs ), _.last( xs ));
                           }, {});
    hub.send( eventType, params );

  }

  // Helpers for page change events:
  function onPageLoad(pageId, listener, oneshot) {
    return hub.subscribe({eventType: "page-load", pageId: pageId}, listener, oneshot);
  }
  function onPageUnload(pageId, listener, oneshot) {
    return hub.subscribe({eventType: "page-unload", pageId: pageId}, listener, oneshot);
  }

  return {
    subscribe:      subscribe,
    unsubscribe:    unsubscribe,
    send:           send,
    sendHash:        sendHash,
    onPageLoad:     onPageLoad,
    onPageUnload:   onPageUnload,
    setDebug:       setDebug
  };

})();
