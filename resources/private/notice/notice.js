LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;

  self.authorization = lupapisteApp.models.applicationAuthModel;

  self.authorityNotice = ko.observable();
  self.urgency = ko.observable("normal");
  self.availableUrgencyStates = ko.observableArray(["normal", "urgent", "pending"]);
  self.selectedTags = ko.observableArray([]);

  self.noticeLabel = loc("notice.prompt") + " (" + loc("notice.prompt.info") + ")";
  var subscriptions = [];

  var subscribe = function() {
    subscriptions.push(self.urgency.subscribe(_.debounce(function(value) {
      ajax
        .command("change-urgency", {
          id: self.applicationId,
          urgency: value})
        .success(function() {
          hub.send("indicator-icon", {style: "positive"});
        })
        .error(function() {
          hub.send("indicator-icon", {style: "negative"});
        })
        .call();
    }, 500)));

    subscriptions.push(self.authorityNotice.subscribe(_.debounce(function(value) {
      ajax
        .command("add-authority-notice", {
          id: self.applicationId,
          authorityNotice: value})
        .success(function() {
          hub.send("indicator-icon", {style: "positive"});
        })
        .error(function() {
          hub.send("indicator-icon", {style: "negative"});
        })
        .call();
    }, 500)));

    subscriptions.push(self.selectedTags.subscribe(_.debounce(function(tags) {
      ajax
        .command("add-application-tags", {
          id: self.applicationId,
          tags: _.pluck(tags, "id")})
        .success(function() {
          hub.send("indicator-icon", {style: "positive"});
        })
        .error(function() {
          hub.send("indicator-icon", {style: "negative"});
        })
        .call();
    }, 500)));
  };

  var unsubscribe = function() {
    while(subscriptions.length !== 0) {
      subscriptions.pop().dispose();
    }
  };

  subscribe();

  self.refresh = function(application) {
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = ko.unwrap(application.id);
    self.urgency(application.urgency());
    self.authorityNotice(ko.unwrap(application.authorityNotice));
    self.selectedTags(ko.toJS(application.tags));
    subscribe();
  };
};
