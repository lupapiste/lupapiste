LUPAPISTE.TriggerTargetService = function() {
  "use strict";
  var self = this;

  var _data = ko.observable();

  self.selected = ko.observableArray([]);

  self.data = ko.pureComputed(function() {
    return _data();
  });

  function load() {
    if (lupapisteApp.models.globalAuthModel.ok("organization-attachment-types")) {
      ajax.query("organization-attachment-types")
        .success(function(res) {
          _data(res.attachmentTypes);
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }
};
