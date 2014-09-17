LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.urgent = ko.observable();
  self.authorityNotice = ko.observable();

  var subscibtions = [];
  var subscribe = function() {
    subscibtions.push(self.urgent.subscribe(_.debounce(function(value) {
      ajax
        .command("toggle-urgent", {
          id: self.applicationId,
          urgent: value})
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
    self.urgent(application.urgent);
    self.authorityNotice(application.authorityNotice);
    subscribe();
  };
};
