LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.authorityNotice = ko.observable();
  self.urgency = ko.observable('normal');

  self.availableUrgencyStates = ko.observableArray(['normal', 'urgent', 'pending']);

  var subscibtions = [];
  var subscribe = function() {
    subscibtions.push(self.urgency.subscribe(_.debounce(function(value) {
      ajax
        .command("change-urgency", {
          id: self.applicationId,
          urgency: value})
        .call();
    }, 500)));

    subscibtions.push(self.authorityNotice.subscribe(_.debounce(function(value) {
      ajax
        .command("add-authority-notice", {
          id: self.applicationId,
          authorityNotice: value})
        .call();
    }, 500)));
  };

  var unsubscribe = function() {
    while(subscibtions.length != 0) {
      subscibtions.pop().dispose();
    }
  }

  subscribe();

  self.refresh = function(application) {
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = application.id;
    self.urgency(application.urgency);
    self.authorityNotice(application.authorityNotice);
    subscribe();
  };
};
