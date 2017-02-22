LUPAPISTE.TriggerTargetService = function() {
  "use strict";
  var self = this;

  var _data = ko.observable();

  self.selected = ko.observableArray([]);

  ko.computed(function() {
  });

  self.data = ko.pureComputed(function() {
    return _data();
  });

  function load() {
    ajax.query("all-attachment-types-by-user")
      .success(function(res) {
        _data(res.attachmentTypes);
      })
      .call();
    return true;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }
};
