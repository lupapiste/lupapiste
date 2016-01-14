LUPAPISTE.SidePanelService = function() {
  "use strict";
  var self = this;

  var application = lupapisteApp.models.application;

  self.urgency = ko.computed(function() {
    return ko.unwrap(application.urgency);
  });

  self.authorityNotice = ko.computed(function() {
    return ko.unwrap(application.authorityNotice);
  });

  self.tags = ko.computed(function() {
    return ko.toJS(application.tags);
  });

  var changeNoticeInfo = _.debounce(function(command, data) {
    ajax
      .command(command, _.assign({id: application.id()}, data))
      .success(function() {
        hub.send("SidePanelService::NoticeChangeProcessed", {status: "success"});
      })
      .error(function() {
        hub.send("SidePanelService::NoticeChangeProcessed", {status: "failed"});
      })
      .call();
  }, 500);

  hub.subscribe("SidePanelService::UrgencyChanged", function(event) {
    changeNoticeInfo("change-urgency", _.pick(event, "urgency"));
  });

  hub.subscribe("SidePanelService::AuthorityNoticeChanged", function(event) {
    changeNoticeInfo("add-authority-notice", _.pick(event, "authorityNotice"));
  });

  hub.subscribe("SidePanelService::TagsChanged", function(event) {
    changeNoticeInfo("add-application-tags", _.pick(event, "tags"));
  });
};
