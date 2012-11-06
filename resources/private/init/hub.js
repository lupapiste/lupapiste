/*
 * hub.js:
 */

var hub = function() {

  var nextId = 0;
  var subscriptions = { };

  function Subscription(listener, filter, oneshot) {
    this.listener = listener;
    this.filter = filter;
    this.oneshot = oneshot;
    this.deliver = function(e) {
      for (var k in this.filter) {
        if (this.filter[k] != e[k]) return false;
      }
      this.listener(e);
      return true;
    };
  }

  function subscribe(filter, listener, oneshot) {
    var id = nextId;
    nextId += 1;
    if (typeof filter === "string") filter = { type: filter };
    subscriptions[id] = new Subscription(listener, filter, oneshot);
    return id;
  }

  function unsubscribe(id) {
    delete subscriptions[id];
  }

  function makeEvent(type, data) {
    var e = {type: type};
    for (var k in data) e[k] = data[k];
    return e;
  }

  function send(type, data) {
    var count = 0;
    var event = makeEvent(type, data || {});
    var remove = [];

    for (var id in subscriptions) {
      var s = subscriptions[id];
      if (s.deliver(event)) {
        if (s.oneshot) remove.push(id);
        count++;
      }
    }

    for (var i in remove) {
      unsubscribe(i);
    }

    if (count == 0) warn("No subscribers for message with type:", type);
    
    return count;
  }

  // Helper for common case:
  function onPageChange(pageId, listener, oneshot) {
    hub.subscribe({type: "page-change", pageId: pageId}, listener, oneshot);
  }

  return {
    subscribe:        subscribe,
    unsubscribe:      unsubscribe,
    send:             send,
    onPageChange:     onPageChange
  };

}();
