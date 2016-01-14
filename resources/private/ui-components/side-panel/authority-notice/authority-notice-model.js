LUPAPISTE.AuthorityNoticeModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.application = params.application;

  self.authorization = params.authorization;

  self.urgency = ko.observable();
  self.authorityNotice = ko.observable();
  self.tags = ko.observableArray([]);

  self.noticeLabel = loc("notice.prompt") + " (" + loc("notice.prompt.info") + ")";

  var updatingObservables = true;

  ko.computed(function() {
    updatingObservables = true;
    self.urgency(params.urgency());
    self.authorityNotice(params.authorityNotice());
    self.tags(params.tags() || []);
    updatingObservables = false;
  });

  self.urgency.subscribe(function(val) {
    if (updatingObservables) return;
    self.sendEvent("SidePanelService", "UrgencyChanged", {urgency: val});
  });

  self.authorityNotice.subscribe(function(val) {
    if (updatingObservables) return;
    self.sendEvent("SidePanelService", "AuthorityNoticeChanged", {authorityNotice: val});
  });

  self.tags.subscribe(function(val) {
    if (updatingObservables) return;
    self.sendEvent("SidePanelService", "TagsChanged", {tags: _.pluck(val, "id")});
  });

  self.addEventListener("SidePanelService", "NoticeChangeProcessed", function(event) {
    if (event.status === "success") {
      hub.send("indicator-icon", {style: "positive"});
    } else {
      hub.send("indicator-icon", {style: "negative"});
    }
  });
};
