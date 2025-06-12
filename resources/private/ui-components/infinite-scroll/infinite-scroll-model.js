LUPAPISTE.InfiniteScrollModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.id = params.id || util.randomElementId("infinite-scroll");
  self.waiting = ko.observable(false);
  self.load = params.load || ko.observable(false);

  self.load.extend({notify: "always"});

  var loadFn = params.loadFn || _.noop;
  var waypoint;

  // always refresh waypoint when load parameter triggers event i.e. something was loaded
  // therefore load parameter must trigger always even if the value stays the same
  self.disposedComputed(function() {
    var load = self.load();
    if (waypoint) {
      waypoint.context.refresh();
      self.waiting(false);
      // Check if waypoint is still visible and we have more to load
      if (util.elementInViewport(document.getElementById(self.id)) && load) {
        self.waiting(true);
        loadFn();
      }
    }
  });

  // add waypoint to element after it is added to dom
  _.defer(function() {
    waypoint = new Waypoint({
      element: document.getElementById(self.id),
      handler: function(direction) {
        if (self.load() && direction === "down") {
          self.waiting(true);
          loadFn();
        }
      },
      offset: "bottom-in-view"
    });
  });
};
