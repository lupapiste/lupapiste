LUPAPISTE.CompanyNotesPanelModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.application = params.application;
  self.authorization = params.authorization;

  self.note = ko.observable();
  self.tags = ko.observableArray([]);

  self.noteLabel = loc("company-note.prompt") + " (" + loc("company-note.prompt.info") + ")";

  // Update inner observables when values change and suppress change events going outside
  self.disposedComputed(function() {
    self.disposeAppliedSubscriptions();
    self.note(params.note());
    self.tags(params.tags() || []);
    self.applySubscriptions();
  });

  self.registerApplyableSubscription(self.note, function(val) {
    self.sendEvent("SidePanelService", "CompanyNoteChanged", {note: val});
  });

  self.registerApplyableSubscription(self.tags, function(val) {
    self.sendEvent("SidePanelService", "CompanyTagsChanged", {tags: val});
  });

  // Show when any of the values have been processed
  self.addEventListener("SidePanelService", "NoticeChangeProcessed", function(event) {
    if (event.status === "success") {
      hub.send("indicator-icon", {style: "positive"});
    } else {
      hub.send("indicator-icon", {style: "negative"});
    }
  });

  self.applySubscriptions();
};
