LUPAPISTE.SuomifiService = function() {
  "use strict";
  var self = this;

  var _data = ko.observable();

  self.selectedVerdict = ko.observableArray([]);
  self.selectedNeighbors = ko.observableArray([]);

  self.data = ko.pureComputed(function() {
    return _data();
  });

  var initialized = false;

  function load() {
    if (lupapisteApp.models.globalAuthModel.ok("organization-attachment-types")) {
      ajax.query("organization-attachment-types")
        .success(function(res) {
          _data(res.attachmentTypes);
        })
        .call();

      ajax.query("get-organization-suomifi-attachments")
        .success(function(res) {
          if (_.isArray(res.verdict)) {
            self.selectedVerdict(res.verdict);
          }
          if (_.isArray(res.neighbors)) {
            self.selectedNeighbors(res.neighbors);
          }
          initialized = true;
        })
        .call();

      return true;
    }
    return false;
  }

  ko.computed(function() {
    var verdictAttachments = self.selectedVerdict();
    if (initialized) {
      ajax.command("upsert-organization-suomifi-message-attachments", {section: "verdict", attachments: verdictAttachments})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  ko.computed(function() {
    var neighborsAttachments = self.selectedNeighbors();
    if (initialized) {
      ajax.command("upsert-organization-suomifi-message-attachments", {section: "neighbors", attachments: neighborsAttachments})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }});

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }
};
